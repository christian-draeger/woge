import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { STRICT_CSP } from "../test-support/strict-policy.mjs";
import { completeFrame, encodeStream, patchFrame } from "../test-support/protocol-fixture.mjs";

const routes = new Map([
  ["/signals", { path: new URL("./signals-fixture.html", import.meta.url), type: "text/html; charset=utf-8" }],
  ...["signals-bootstrap", "signals-island"].map((name) =>
    [`/${name}.js`, { path: new URL(`./${name}.mjs`, import.meta.url), type: "text/javascript; charset=utf-8" }]),
  ["/signals.js", { path: new URL("../dist/signals.js", import.meta.url), type: "text/javascript; charset=utf-8" }],
  ["/islands", { path: new URL("./islands-fixture.html", import.meta.url), type: "text/html; charset=utf-8" }],
  ["/islands-bootstrap.js", { path: new URL("./islands-bootstrap.mjs", import.meta.url), type: "text/javascript; charset=utf-8" }],
  ["/island-counter.js", { path: new URL("./island-counter.mjs", import.meta.url), type: "text/javascript; charset=utf-8" }],
  ["/islands.js", { path: new URL("../dist/islands.js", import.meta.url), type: "text/javascript; charset=utf-8" }],
  ["/navigation-bootstrap.js", { path: new URL("./navigation-bootstrap.mjs", import.meta.url),
    type: "text/javascript; charset=utf-8" }],
  ...["woge-navigation.js", "state.js", "transactions.js"].map((name) =>
    [`/${name}`, { path: new URL(`../dist/${name}`, import.meta.url), type: "text/javascript; charset=utf-8" }]),
  ["/strict", { path: new URL("./fixture.html", import.meta.url), type: "text/html; charset=utf-8" }],
  ["/fixture-bootstrap.js", {
    path: new URL("./fixture-bootstrap.mjs", import.meta.url), type: "text/javascript; charset=utf-8",
  }],
  ["/", { path: new URL("./fixture.html", import.meta.url), type: "text/html; charset=utf-8" }],
  ["/action-forms", { path: new URL("./actions-fixture.html", import.meta.url), type: "text/html; charset=utf-8" }],
  ["/deferred-complete", { path: new URL("./deferred-complete-fixture.html", import.meta.url), type: "text/html; charset=utf-8" }],
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
  if (url.pathname === "/signals-action" && request.method === "POST") {
    let body = "";
    for await (const chunk of request) body += chunk;
    const note = new URLSearchParams(body).get("note");
    const validation = note === "reject";
    const count = note === "second" ? 2 : 1;
    const revision = Number(request.headers["woge-region-revisions"].split(":")[1]);
    response.writeHead(validation ? 400 : 200, {
      "content-type": "application/vnd.woge.patch-stream; version=1",
      ...(validation ? { "woge-validation": "errors" } : {}),
    });
    response.end(Buffer.from(encodeStream([patchFrame({
      target: "saved", epoch: request.headers["woge-page-epoch"],
      interactionSequence: Number(request.headers["woge-interaction"]),
      baseRevision: revision, nextRevision: revision + 1,
      html: `<output data-authoritative-count="${count}">${count} saved notes</output>` +
        (validation ? '<p id="errors" tabindex="-1">The server rejected this note</p>' : ""),
    }), completeFrame()])));
    return;
  }
  if (url.pathname.startsWith("/navigation/")) {
    response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'unsafe-inline'");
    if (url.pathname === "/navigation/cross-origin") {
      response.writeHead(302, { location: "http://localhost:4173/navigation/b" }).end();
      return;
    }
    if (url.pathname === "/navigation/redirect") {
      response.writeHead(302, { location: "/navigation/b" }).end();
      return;
    }
    let html = await readFile(new URL(url.pathname === "/navigation/a" ?
      "./navigation-a.html" : "./navigation-b.html", import.meta.url), "utf8");
    if (url.pathname === "/navigation/skew") html = html.replace('content="1"', 'content="2"');
    if (url.pathname === "/navigation/assets") html = html.replace("</head>", "<style>h1{color:red}</style></head>");
    if (url.pathname === "/navigation/managed") html = html.replace("</head>",
      '<meta name="woge-render-session" content="new-session"></head>');
    response.writeHead(url.pathname === "/navigation/error" ? 500 : 200, {
      "content-type": url.pathname === "/navigation/non-html" ? "text/plain" : "text/html; charset=utf-8",
      "cache-control": "no-store",
    });
    response.end(html);
    return;
  }
  if (url.pathname === "/managed-action" && request.method === "POST") {
    for await (const _ of request) {}
    const revisions = new Map(request.headers["woge-region-revisions"].split(",").map((entry) => entry.split(":")));
    const patches = ["summary-1", "status-1"].map((target) => patchFrame({
      patchId: target, target, epoch: request.headers["woge-page-epoch"],
      interactionSequence: Number(request.headers["woge-interaction"]),
      baseRevision: Number(revisions.get(target)), nextRevision: Number(revisions.get(target)) + 1,
      html: target === "status-1" ? "Saved" : `Notes: ${Number(revisions.get(target)) + 1}`,
    }));
    response.writeHead(200, { "content-type": "application/vnd.woge.patch-stream; version=1" });
    response.end(Buffer.from(encodeStream([...patches, completeFrame(2)])));
    return;
  }
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
  if (url.pathname === "/native-dpu") {
    const mode = url.searchParams.get("mode");
    if (!["native", "fallback"].includes(mode)) {
      response.writeHead(400).end();
      return;
    }
    response.writeHead(200, {
      "content-type": "text/html; charset=utf-8",
      "cache-control": "no-store",
      "vary": "Cookie",
    });
    if (mode === "native") {
      response.end(`<!doctype html><html lang="en"><head><title>Native DPU fixture</title></head><body><main><h1>Projects</h1><?start name="woge-fixture-range"><section aria-label="Project results"><p>Loading projects</p></section><?end><template for="woge-fixture-range"><section aria-label="Project results"><ul><li>Alpha</li><li>Beta</li><li>Gamma</li></ul></section></template></main></body></html>`);
    } else {
      response.end(`<!doctype html><html lang="en"><head><title>Fallback fixture</title></head><body><main><h1>Projects</h1><section aria-label="Project results" id="project-results"><p>Loading projects</p></section><script>document.getElementById("project-results").innerHTML = "<ul><li>Alpha</li><li>Beta</li><li>Gamma</li></ul>";</script></main></body></html>`);
    }
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
