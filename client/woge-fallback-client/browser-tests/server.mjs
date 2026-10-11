import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { STRICT_CSP } from "../test-support/strict-policy.mjs";
import { completeFrame, encodeStream, patchFrame } from "../test-support/protocol-fixture.mjs";

const routes = new Map([
  ["/strict", { path: new URL("./fixture.html", import.meta.url), type: "text/html; charset=utf-8" }],
  ["/fixture-bootstrap.js", {
    path: new URL("./fixture-bootstrap.mjs", import.meta.url), type: "text/javascript; charset=utf-8",
  }],
  ["/", { path: new URL("./fixture.html", import.meta.url), type: "text/html; charset=utf-8" }],
  ["/action-forms", { path: new URL("./actions-fixture.html", import.meta.url), type: "text/html; charset=utf-8" }],
  ["/deferred", { path: new URL("./deferred-fixture.html", import.meta.url), type: "text/html; charset=utf-8" }],
  [
    "/deferred-bootstrap.js",
    { path: new URL("./deferred-bootstrap.mjs", import.meta.url), type: "text/javascript; charset=utf-8" },
  ],
  ["/woge-fallback.js", { path: new URL("../dist/woge-fallback.js", import.meta.url), type: "text/javascript; charset=utf-8" }],
  [
    "/package-consumer",
    { path: new URL("./package-consumer-fixture.html", import.meta.url), type: "text/html; charset=utf-8" },
  ],
  [
    "/package-consumer.js",
    { path: new URL("../build/package-consumer/consumer-bundle.js", import.meta.url), type: "text/javascript; charset=utf-8" },
  ],
]);

const deferredStream = await readDeferredStream();
const firstPatchEnd = firstFrameEnd(deferredStream);
const pendingDeferredResponses = new Map();

const server = createServer(async (request, response) => {
  const url = new URL(request.url, "http://127.0.0.1");
  if (url.pathname === "/strict") response.setHeader("Content-Security-Policy", STRICT_CSP);
  if (["/action", "/preview"].includes(url.pathname) && request.method === "POST") {
    for await (const _ of request) {}
    if (url.searchParams.has("validation")) {
      response.writeHead(400, {
        "content-type": "application/vnd.woge.patch-stream; version=1",
        "woge-validation": url.searchParams.get("summary") ?? "errors",
      });
      const interactionSequence = url.searchParams.has("stale") ? 1 : 0;
      response.end(Buffer.from(encodeStream([
        patchFrame({
          html: '<section id="errors" tabindex="-1"><h2>Check the form</h2><a href="#title">Title is required</a></section>',
          interactionSequence,
        }),
        completeFrame(1),
      ])));
      return;
    }
    response.writeHead(200, { "content-type": "application/vnd.woge.patch-stream; version=1" });
    const interactionSequence = url.searchParams.has("stale") ? 1 : 0;
    response.end(Buffer.from(encodeStream([
      patchFrame({ html: "<p>Updated</p>", interactionSequence }),
      patchFrame({ patchId: "status-patch", target: "status-1", html: "Saved", interactionSequence }),
      completeFrame(2),
    ])));
    return;
  }
  if (url.pathname === "/deferred-patches") {
    startDeferredResponse(url.searchParams.get("run"), response);
    return;
  }
  if (url.pathname === "/release-deferred" && request.method === "POST") {
    releaseDeferredResponse(url.searchParams.get("run"), response);
    return;
  }

  const route = routes.get(url.pathname);
  if (!route) {
    response.writeHead(404).end();
    return;
  }
  try {
    response.writeHead(200, { "content-type": route.type, "cache-control": "no-store" });
    response.end(await readFile(route.path));
  } catch {
    response.writeHead(500).end();
  }
});

server.listen(4173, "127.0.0.1");
process.on("SIGTERM", () => server.close());

function startDeferredResponse(runId, response) {
  if (!runId || pendingDeferredResponses.has(runId)) {
    response.writeHead(400).end();
    return;
  }
  response.writeHead(200, {
    "content-type": "application/vnd.woge.patch-stream; version=1",
    "cache-control": "no-store",
  });
  response.write(deferredStream.subarray(0, firstPatchEnd));
  pendingDeferredResponses.set(runId, response);
  response.on("close", () => {
    if (pendingDeferredResponses.get(runId) === response) pendingDeferredResponses.delete(runId);
  });
}

function releaseDeferredResponse(runId, releaseResponse) {
  const deferredResponse = runId ? pendingDeferredResponses.get(runId) : undefined;
  if (!deferredResponse) {
    releaseResponse.writeHead(404).end();
    return;
  }
  pendingDeferredResponses.delete(runId);
  deferredResponse.end(deferredStream.subarray(firstPatchEnd));
  releaseResponse.writeHead(204).end();
}

async function readDeferredStream() {
  const fixture = new URL(
    "../../../modules/woge-server-runtime/src/test/resources/fixtures/deferred-patch-stream-v1.hex",
    import.meta.url,
  );
  return Buffer.from((await readFile(fixture, "utf8")).replaceAll(/\s/g, ""), "hex");
}

function firstFrameEnd(bytes) {
  const preambleBytes = 5;
  const headerBytes = 10;
  const header = preambleBytes;
  const contentTypeLength = bytes[header + 1];
  const metadataLength = bytes.readUInt32BE(header + 2);
  const payloadLength = bytes.readUInt32BE(header + 6);
  return preambleBytes + headerBytes + contentTypeLength + metadataLength + payloadLength;
}
