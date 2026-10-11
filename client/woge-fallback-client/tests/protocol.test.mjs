import assert from "node:assert/strict";
import test from "node:test";
import { isPatchStreamMediaType, PatchStreamDecoder, WogePatchError } from "../src/protocol.js";
import {
  completeFrame,
  encodeStream,
  errorFrame,
  patchFrame,
  rawFrame,
  readFuzzRegression,
  readGoldenStream,
  readGoldenWireFixture,
} from "../test-support/protocol-fixture.mjs";

test("patch HTTP media types accept parameter whitespace and quoting but require version 1", () => {
  for (const value of [
    "application/vnd.woge.patch-stream; version=1",
    "application/vnd.woge.patch-stream;version=1",
    'APPLICATION/VND.WOGE.PATCH-STREAM ; version = "1" ',
  ]) {
    assert.equal(isPatchStreamMediaType(value), true, value);
  }
  for (const value of [
    null, "", "*/*", "text/html", "application/vnd.woge.patch-stream",
    "application/vnd.woge.patch-stream; version=2",
    "application/vnd.woge.patch-stream; version=01",
    "application/vnd.woge.patch-stream; version=1; charset=utf-8",
    "application/vnd.woge.patch-stream;\nversion=1",
  ]) {
    assert.equal(isPatchStreamMediaType(value), false, String(value));
  }
});

test("unknown server preamble and metadata versions fail as incompatible before application", () => {
  assert.throws(() => new PatchStreamDecoder().push(Uint8Array.of(0x57, 0x4f, 0x47, 0x45, 2)),
    (problem) => problem.code === "WOGE_UNSUPPORTED_VERSION");
  const metadata = '{"protocolVersion":2,"operation":"replace","patchId":"patch-1",' +
    '"epoch":"epoch-a","target":"summary-1","interactionSequence":0,"baseRevision":0,"nextRevision":1}';
  assert.throws(() => new PatchStreamDecoder().push(encodeStream([
    rawFrame(1, "text/html; charset=utf-8", metadata, "<p>new</p>"),
  ])), (problem) => problem.code === "WOGE_UNSUPPORTED_VERSION");
});

test("the JVM golden stream decodes at every two-chunk boundary", async () => {
  const bytes = await readGoldenStream();

  for (let split = 0; split <= bytes.byteLength; split += 1) {
    const decoder = new PatchStreamDecoder();
    const events = [...decoder.push(bytes.slice(0, split)), ...decoder.push(bytes.slice(split))];
    decoder.finish();
    assertGoldenEvents(events);
  }
});

test("seeded chunking, truncation and byte mutation stay bounded and typed", {
  timeout: fuzzIterations() > 128 ? 120_000 : 5_000,
}, () => {
  const random = seededRandom(0x574f4745);
  const bytes = encodeStream([patchFrame({ html: "<p>€ boundary</p>" }), completeFrame()]);
  const expected = decode(bytes);

  for (let iteration = 0; iteration < fuzzIterations(); iteration += 1) {
    const decoder = new PatchStreamDecoder();
    const events = [];
    let offset = 0;
    while (offset < bytes.byteLength) {
      const end = Math.min(bytes.byteLength, offset + 1 + Math.floor(random() * 32));
      events.push(...decoder.push(bytes.subarray(offset, end)));
      offset = end;
    }
    decoder.finish();
    assert.deepEqual(events, expected);

    const cut = Math.floor(random() * bytes.byteLength);
    const truncated = new PatchStreamDecoder();
    let partialEvents = [];
    try {
      partialEvents = truncated.push(bytes.subarray(0, cut));
    } catch (problem) {
      assert.ok(isTypedTruncation(problem), problem.code);
    }
    assert.ok(partialEvents.every((event) => event.type !== "complete"));
    assert.throws(() => truncated.finish(), isTypedTruncation);

    const mutated = bytes.slice();
    const index = Math.floor(random() * mutated.byteLength);
    mutated[index] = Math.floor(random() * 256);
    assertTypedOrValid(mutated);
  }
});

test("shared malformed-wire regressions retain their stable failure codes", { timeout: 5_000 }, async () => {
  const fixtures = new Map([
    ["unsupported-version", "WOGE_UNSUPPORTED_VERSION"],
    ["oversized-metadata", "WOGE_METADATA_TOO_LARGE"],
    ["truncated-header", "WOGE_TRUNCATED_STREAM"],
    ["unknown-frame-kind", "WOGE_UNKNOWN_FRAME_KIND"],
    ["oversized-payload", "WOGE_PAYLOAD_TOO_LARGE"],
  ]);
  for (const [name, code] of fixtures) {
    const decoder = new PatchStreamDecoder();
    const bytes = await readFuzzRegression(name);
    assert.throws(() => {
      decoder.push(bytes);
      decoder.finish();
    }, (problem) => problem instanceof WogePatchError && problem.code === code, name);
  }
});

test("invalid UTF-8 in an HTML payload fails with a typed error", { timeout: 5_000 }, () => {
  const frame = rawFrame(1, "text/html; charset=utf-8",
    '{"protocolVersion":1,"operation":"replace","patchId":"patch-1",' +
      '"epoch":"epoch-a","target":"summary-1","interactionSequence":0,"baseRevision":0,"nextRevision":1}',
    Uint8Array.of(0xc3, 0x28));
  assertFailure("WOGE_INVALID_UTF8", () => new PatchStreamDecoder().push(encodeStream([frame])));
});

test("oversized length declarations fail from the fixed header", { timeout: 5_000 }, () => {
  for (const [metadataLength, payloadLength, expected] of [
    [64 * 1024 + 1, 0, "WOGE_METADATA_TOO_LARGE"],
    [0, 8 * 1024 * 1024 + 1, "WOGE_PAYLOAD_TOO_LARGE"],
  ]) {
    const header = new Uint8Array(15);
    header.set([0x57, 0x4f, 0x47, 0x45, 1, 1, 24]);
    const view = new DataView(header.buffer);
    view.setUint32(7, metadataLength, false);
    view.setUint32(11, payloadLength, false);
    assert.throws(() => new PatchStreamDecoder().push(header),
      (problem) => problem.code === expected);
  }
});

test("the JVM golden stream decodes one byte at a time", async () => {
  const decoder = new PatchStreamDecoder();
  const events = [];
  for (const byte of await readGoldenStream()) events.push(...decoder.push(Uint8Array.of(byte)));

  decoder.finish();

  assertGoldenEvents(events);
});


test("version-one action form encoding and live SSE block match shared golden fixtures", async () => {
  const fields = new URLSearchParams();
  fields.append("value", "accepted");
  fields.append("note", "two words\r\nsecond line");
  assert.equal(`${fields}`, (await readGoldenWireFixture("action-form-v1.txt")).trim());

  const sse = (await readGoldenWireFixture("live-events-v1.txt")).replace(/\r?\n/g, "\n");
  assert.equal(sse, "id: 42\nevent: invalidate\ndata: tasks\ndata: summary\n\n");
  const event = sse.trimEnd().split("\n");
  assert.equal(event[1], "event: invalidate");
  assert.deepEqual(event.slice(2).map((line) => line.slice("data: ".length)), ["tasks", "summary"]);
});

test("stream byte budgets accept the exact threshold and reject incrementally without payload diagnostics", () => {
  const bytes = encodeStream([patchFrame(), completeFrame()]);
  const exact = new PatchStreamDecoder({ maxStreamBytes: bytes.byteLength });
  assert.equal(exact.push(bytes).length, 2);
  exact.finish();
  const decoder = new PatchStreamDecoder({ maxStreamBytes: bytes.byteLength - 1 });
  decoder.push(bytes.subarray(0, bytes.byteLength - 1));
  let rejection;
  assert.throws(() => decoder.push(bytes.subarray(bytes.byteLength - 1)), (problem) => {
    rejection = problem;
    return problem.code === "WOGE_RESOURCE_LIMIT_EXCEEDED" &&
      problem.limit === "PATCH_STREAM_BYTES" && problem.threshold === bytes.byteLength - 1;
  });
  assert.throws(() => decoder.finish(), (problem) => problem === rejection);
  assert.equal(rejection.message.includes("Updated"), false);
});

test("patch-count budgets reject the next header before its payload is buffered", () => {
  const decoder = new PatchStreamDecoder({ maxPatches: 1 });
  decoder.push(encodeStream([patchFrame()]));
  const second = patchFrame({ patchId: "patch-2", html: "not decoded" });
  assert.throws(() => decoder.push(second.subarray(0, 10)),
    (problem) => problem.code === "WOGE_RESOURCE_LIMIT_EXCEEDED" &&
      problem.limit === "PATCH_COUNT" && problem.threshold === 1);
  const exact = new PatchStreamDecoder({ maxPatches: 1 });
  assert.equal(exact.push(encodeStream([patchFrame(), completeFrame()])).length, 2);
  exact.finish();
});

test("resource limits reject unusable configuration", () => {
  for (const limits of [
    null, [], { maxPatches: 0 }, { maxPatches: 2_147_483_648 }, { maxStreamBytes: Infinity },
    { maxConcurrentStreams: -1 }, { maxStreamBytes: 1.5 }, { maxPatches: Number.MAX_SAFE_INTEGER + 1 },
  ]) {
    assert.throws(() => new PatchStreamDecoder(limits), (problem) => problem.code === "WOGE_INVALID_RESOURCE_LIMIT");
  }
});

test("the collection golden stream decodes every operation at every two-chunk boundary", async () => {
  const bytes = await readGoldenStream("collection-patch-stream-v1");
  for (let split = 0; split <= bytes.byteLength; split++) {
    const decoder = new PatchStreamDecoder();
    const events = [...decoder.push(bytes.slice(0, split)), ...decoder.push(bytes.slice(split))];
    decoder.finish();
    assert.deepEqual(events.slice(0, 3).map((event) => event.patch.operation), ["replace", "append", "remove"]);
    assert.equal(events[1].patch.itemId, "item-1");
    assert.equal(events[2].patch.focusTarget, "summary-1");
    assert.equal(events[3].patchCount, 3);
  }
});

test("collection metadata rejects missing identity, unknown operations and nonempty removal payloads", () => {
  for (const frame of [
    patchFrame({ operation: "append" }),
    patchFrame({ operation: "remove", itemId: "item-1", html: "" }),
    patchFrame({ operation: "unknown" }),
    patchFrame({ operation: "replace", itemId: "item-1" }),
  ]) {
    assert.throws(() => new PatchStreamDecoder().push(encodeStream([frame, completeFrame()])),
      (problem) => problem.code === "WOGE_INVALID_METADATA");
  }
  assert.throws(() => new PatchStreamDecoder().push(encodeStream([
    patchFrame({ operation: "remove", itemId: "item-1", focusTarget: "summary-1", html: "not empty" }),
    completeFrame(),
  ])), (problem) => problem.code === "WOGE_INVALID_LENGTH");
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

function fuzzIterations() {
  const value = Number(process.env.WOGE_FUZZ_ITERATIONS ?? 128);
  return Number.isSafeInteger(value) && value > 0 ? Math.min(value, 100_000) : 128;
}

function seededRandom(seed) {
  let state = seed >>> 0;
  return () => {
    state ^= state << 13;
    state ^= state >>> 17;
    state ^= state << 5;
    return (state >>> 0) / 0x1_0000_0000;
  };
}

function decode(bytes) {
  const decoder = new PatchStreamDecoder();
  const events = decoder.push(bytes);
  decoder.finish();
  return events;
}

function isTypedTruncation(problem) {
  return problem instanceof WogePatchError && [
    "WOGE_INVALID_PREAMBLE", "WOGE_TRUNCATED_STREAM", "WOGE_MISSING_TERMINAL",
  ].includes(problem.code);
}

function assertTypedOrValid(bytes) {
  try {
    decode(bytes);
  } catch (problem) {
    assert.ok(problem instanceof WogePatchError, problem?.constructor?.name);
  }
}
