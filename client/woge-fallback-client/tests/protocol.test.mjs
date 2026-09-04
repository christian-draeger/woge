import assert from "node:assert/strict";
import test from "node:test";
import { PatchStreamDecoder, WogePatchError } from "../src/protocol.js";
import {
  completeFrame,
  encodeStream,
  errorFrame,
  patchFrame,
  rawFrame,
  readGoldenStream,
} from "../test-support/protocol-fixture.mjs";

test("the JVM golden stream decodes at every two-chunk boundary", async () => {
  const bytes = await readGoldenStream();

  for (let split = 0; split <= bytes.byteLength; split += 1) {
    const decoder = new PatchStreamDecoder();
    const events = [...decoder.push(bytes.slice(0, split)), ...decoder.push(bytes.slice(split))];
    decoder.finish();
    assertGoldenEvents(events);
  }
});

test("the JVM golden stream decodes one byte at a time", async () => {
  const decoder = new PatchStreamDecoder();
  const events = [];
  for (const byte of await readGoldenStream()) events.push(...decoder.push(Uint8Array.of(byte)));

  decoder.finish();

  assertGoldenEvents(events);
});

test("signed 64-bit protocol counters do not lose JavaScript number precision", () => {
  const base = 9_223_372_036_854_775_806n;
  const bytes = encodeStream([
    patchFrame({ interactionSequence: base, baseRevision: base, nextRevision: base + 1n }),
    completeFrame(),
  ]);
  const decoder = new PatchStreamDecoder();

  const [event] = decoder.push(bytes);
  decoder.finish();

  assert.equal(event.patch.interactionSequence, base);
  assert.equal(event.patch.baseRevision, base);
  assert.equal(event.patch.nextRevision, base + 1n);
});

test("canonical metadata rejects whitespace, unknown fields and duplicate fields", () => {
  const canonical =
    '{"protocolVersion":1,"operation":"replace","patchId":"patch-1","epoch":"epoch-a",' +
    '"target":"summary-1","interactionSequence":0,"baseRevision":0,"nextRevision":1}';
  const malformed = [
    canonical.replace("{", "{ "),
    canonical.slice(0, -1) + ',"extra":true}',
    canonical.slice(0, -1) + ',"target":"other"}',
  ];

  for (const metadata of malformed) {
    assertFailure(
      "WOGE_INVALID_METADATA",
      () => new PatchStreamDecoder().push(encodeStream([rawFrame(1, "text/html; charset=utf-8", metadata, "<p>x</p>")])),
    );
  }
});

test("malformed, truncated and terminal-invalid streams fail with stable errors", () => {
  assertFailure("WOGE_INVALID_PREAMBLE", () => new PatchStreamDecoder().push(new TextEncoder().encode("NOPE!")));

  const unknown = encodeStream([patchFrame(), completeFrame()]);
  unknown[5] = 99;
  assertFailure("WOGE_UNKNOWN_FRAME_KIND", () => new PatchStreamDecoder().push(unknown));

  const truncated = new PatchStreamDecoder();
  const complete = encodeStream([patchFrame(), completeFrame()]);
  truncated.push(complete.slice(0, -1));
  assertFailure("WOGE_TRUNCATED_STREAM", () => truncated.finish());

  const missingTerminal = new PatchStreamDecoder();
  missingTerminal.push(encodeStream([patchFrame()]));
  assertFailure("WOGE_MISSING_TERMINAL", () => missingTerminal.finish());

  assertFailure("WOGE_BYTES_AFTER_TERMINAL", () => {
    const bytes = encodeStream([completeFrame(0)]);
    const withTrailing = new Uint8Array(bytes.byteLength + 1);
    withTrailing.set(bytes);
    new PatchStreamDecoder().push(withTrailing);
  });
});

test("safe remote failure metadata is decoded without server details", () => {
  const decoder = new PatchStreamDecoder();

  const events = decoder.push(encodeStream([errorFrame()]));
  decoder.finish();

  assert.deepEqual(events, [
    { type: "error", failure: { code: "WOGE_RENDER_FAILED", correlationId: "trace-1", recovery: "reload" } },
  ]);
});

test("a decoder remembers and rethrows its first protocol failure", () => {
  const decoder = new PatchStreamDecoder();
  let first;
  try {
    decoder.push(new TextEncoder().encode("NOPE!"));
  } catch (problem) {
    first = problem;
  }

  assert.ok(first instanceof WogePatchError);
  assert.throws(() => decoder.push(new Uint8Array()), (problem) => problem === first);
  assert.throws(() => decoder.finish(), (problem) => problem === first);
});

function assertGoldenEvents(events) {
  assert.equal(events.length, 2);
  assert.equal(events[0].type, "patch");
  assert.deepEqual(events[0].patch, {
    protocolVersion: 1,
    operation: "replace",
    patchId: "patch-1",
    epoch: "epoch-a",
    target: "summary-1",
    interactionSequence: 41n,
    baseRevision: 7n,
    nextRevision: 8n,
    html: "<p>Tasks &lt;today&gt;</p>",
  });
  assert.deepEqual(events[1], { type: "complete", patchCount: 1 });
}

function assertFailure(code, action) {
  assert.throws(action, (problem) => problem instanceof WogePatchError && problem.code === code);
}
