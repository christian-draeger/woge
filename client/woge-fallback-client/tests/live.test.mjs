import assert from "node:assert/strict";
import test from "node:test";
import { connectWogeLive } from "../src/live.js";

class FakeSource {
  static last;
  listeners = new Map();
  closed = false;
  constructor(url, options) {
    this.url = url;
    this.options = options;
    FakeSource.last = this;
  }
  addEventListener(name, listener) {
    this.listeners.set(name, listener);
  }
  emit(name, data) {
    this.listeners.get(name)({ data });
  }
  close() {
    this.closed = true;
  }
}

function fakeRuntime() {
  const calls = [];
  const pending = [];
  return {
    calls,
    pending,
    refreshRegion(target, load, { signal }) {
      calls.push(target);
      return new Promise((resolve, reject) => {
        pending.push({ target, resolve, reject, signal });
      });
    },
  };
}

const settle = () => new Promise((resolve) => setImmediate(resolve));

test("invalidations refresh each region once at a time with one follow-up", async () => {
  const runtime = fakeRuntime();
  const live = connectWogeLive(runtime, "/live", { load: async () => {}, EventSource: FakeSource });
  const source = FakeSource.last;
  assert.equal(source.url, "/live");
  source.emit("invalidate", "a\nb");
  source.emit("invalidate", "a");
  source.emit("invalidate", "a");
  assert.deepEqual(runtime.calls, ["a", "b"]);
  runtime.pending.shift().resolve();
  await settle();
  assert.deepEqual(runtime.calls, ["a", "b", "a"]);
  runtime.pending.shift().resolve();
  runtime.pending.shift().resolve();
  await settle();
  assert.deepEqual(runtime.calls, ["a", "b", "a"]);
  source.emit("resync", "a\nb");
  assert.deepEqual(runtime.calls, ["a", "b", "a", "a", "b"]);
  live.close();
  assert.equal(source.closed, true);
  assert.equal(runtime.pending[0].signal.aborted, true);
});

test("failed refreshes are reported and later events still refresh", async () => {
  const runtime = fakeRuntime();
  const problems = [];
  connectWogeLive(runtime, "/live", {
    load: async () => {},
    EventSource: FakeSource,
    onError: (problem) => problems.push(problem.message),
  });
  FakeSource.last.emit("invalidate", "a");
  runtime.pending.shift().reject(new Error("offline"));
  await settle();
  FakeSource.last.emit("invalidate", "a");
  assert.deepEqual(runtime.calls, ["a", "a"]);
  assert.deepEqual(problems, ["offline"]);
});

test("oversized events and missing configuration are rejected", () => {
  const runtime = fakeRuntime();
  const problems = [];
  connectWogeLive(runtime, "/live", {
    load: async () => {},
    EventSource: FakeSource,
    onError: (problem) => problems.push(problem.code),
  });
  FakeSource.last.emit("invalidate", Array.from({ length: 129 }, (_, index) => `r${index}`).join("\n"));
  assert.deepEqual(runtime.calls, []);
  assert.deepEqual(problems, ["WOGE_INVALID_LIVE_EVENT"]);
  assert.throws(() => connectWogeLive(runtime, "/live", { EventSource: FakeSource }), {
    code: "WOGE_INVALID_LIVE_CONFIGURATION",
  });
});
