import test from "node:test";
import assert from "node:assert/strict";
import { connectWogeWebSocket } from "../src/websocket.js";
import { readGoldenStream } from "../test-support/protocol-fixture.mjs";
import { PatchStreamDecoder } from "../src/protocol.js";

function environment(t) {
  t.mock.timers.enable({ apis: ["setTimeout", "Date"], now: 0 });
  const previous = { WebSocket: globalThis.WebSocket, location: globalThis.location };
  const sockets = [];
  class Socket {
    readyState = 0; bufferedAmount = 0; protocol = "woge.patch.v1"; sent = [];
    constructor(url, protocols) { this.url = url; this.protocols = protocols; sockets.push(this); }
    send(text) { this.sent.push(JSON.parse(text)); }
    open() { this.readyState = 1; this.onopen(); }
    close(code, reason) { this.readyState = 3; this.onclose?.({ code, reason }); }
    message(data) { this.onmessage({ data }); }
  }
  globalThis.WebSocket = Socket;
  globalThis.location = { href: "https://woge.test/page", origin: "https://woge.test" };
  t.after(() => Object.assign(globalThis, previous));
  return sockets;
}
const settle = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); };
const subscription = (id = "one", revisions = () => ({ cursor: "0" })) => ({ id, epoch: "socket-page", revisions });
function frame(bytes, id = "one") {
  const prefix = new TextEncoder().encode(id);
  return Uint8Array.from([prefix.length, ...prefix, ...bytes]).buffer;
}

test("optional transport applies the existing golden IR, multiplexes, heartbeats and resumes applied revisions", async (t) => {
  const sockets = environment(t), bytes = await readGoldenStream();
  let revision = "0", token = "first-proof";
  const received = [], errors = [];
  const runtime = { async applyPatchStream(stream) {
    const decoder = new PatchStreamDecoder(), reader = stream.getReader();
    for (;;) { const { done, value } = await reader.read(); if (done) break; received.push(...decoder.push(value)); }
    decoder.finish(); revision = "1";
  } };
  const transport = connectWogeWebSocket("wss://woge.test/socket", {
    runtime, proof: () => token, onError: (error) => errors.push(error),
    subscriptions: [subscription("one", () => ({ cursor: revision })), subscription("two")],
  });
  t.after(transport.close);
  await settle(); sockets[0].open();
  assert.equal(sockets[0].sent.filter((m) => m.op === "subscribe").length, 2);
  sockets[0].message(frame(bytes));
  await settle();
  assert.ok(received.some((event) => event.type === "patch"));
  sockets[0].message('{"op":"ping"}');
  assert.equal(sockets[0].sent.at(-1).op, "pong");
  assert.equal(transport.send("one", "12,34"), true);
  token = "fresh-proof";
  sockets[0].close(1006, "disconnected");
  t.mock.timers.tick(400); await settle();
  sockets[1].open();
  assert.equal(sockets[1].protocols[1], "woge.csrf.fresh-proof");
  assert.equal(sockets[1].sent[0].revisions.cursor, "1");
  assert.equal(sockets[1].sent.some((m) => m.op === "message"), false);
  assert.deepEqual(errors, []);
});

test("browser bounds inbound queue and outbound messages, and never reconnects policy failures", async (t) => {
  const sockets = environment(t), errors = [];
  const transport = connectWogeWebSocket("wss://woge.test/socket", {
    runtime: { applyPatchStream: async () => {} }, proof: () => "proof", subscriptions: [subscription()],
    maxQueuedFrames: 1, onError: (error) => errors.push(error.message),
  });
  await settle(); sockets[0].open();
  sockets[0].bufferedAmount = 70000;
  assert.equal(transport.send("one", "12,34"), false);
  sockets[0].message(frame([1])); sockets[0].message(frame([1]));
  await settle(); t.mock.timers.tick(20000); await settle();
  assert.equal(sockets.length, 1);
  assert.deepEqual(errors, ["WOGE_SOCKET_BACKPRESSURE"]);
});

test("idle reconnect is bounded and abort cancels reconnect", async (t) => {
  const sockets = environment(t), lifetime = new AbortController();
  const transport = connectWogeWebSocket("wss://woge.test/socket", {
    runtime: { applyPatchStream: async () => {} }, proof: () => "proof", signal: lifetime.signal,
    idleTimeout: 1000,
  });
  await settle(); sockets[0].open();
  t.mock.timers.tick(1001);
  assert.equal(sockets[0].readyState, 3);
  lifetime.abort(); t.mock.timers.tick(20000); await settle();
  assert.equal(sockets.length, 1);
  assert.equal(transport.send("one", "x"), false);
});

test("cross-origin endpoints and missing explicit proof integration are rejected", (t) => {
  environment(t);
  const options = { runtime: { applyPatchStream() {} }, proof: () => "proof" };
  assert.throws(() => connectWogeWebSocket("wss://evil.test/socket", options), /INVALID_WEBSOCKET_OPTIONS/);
  assert.throws(() => connectWogeWebSocket("wss://woge.test/socket", {}), /INVALID_WEBSOCKET_OPTIONS/);
});
