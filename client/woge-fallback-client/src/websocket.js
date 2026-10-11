/** Optional realtime transport. The caller supplies the existing document patch runtime, not another DOM engine. */
export function connectWogeWebSocket(url, {
  runtime, proof, subscriptions = [], onError = () => {}, onClose = () => {},
  maxQueuedFrames = 8, maxFrameBytes = 512 * 1024, maxReconnects = 6,
  idleTimeout = 45000, signal,
} = {}) {
  const endpoint = new URL(url, globalThis.location.href);
  const httpOrigin = endpoint.origin.replace(/^ws/, "http");
  if (!["ws:", "wss:"].includes(endpoint.protocol) || httpOrigin !== globalThis.location.origin ||
      endpoint.username || endpoint.password || endpoint.hash || typeof runtime?.applyPatchStream !== "function" ||
      typeof proof !== "function" || !Number.isInteger(maxQueuedFrames) || maxQueuedFrames < 1 ||
      maxQueuedFrames > 128 || !Number.isInteger(maxFrameBytes) || maxFrameBytes < 1024 ||
      maxFrameBytes > 8 * 1024 * 1024 || !Number.isInteger(maxReconnects) || maxReconnects < 0 ||
      maxReconnects > 16 || !Number.isFinite(idleTimeout) || idleTimeout < 1000) {
    throw new Error("WOGE_INVALID_WEBSOCKET_OPTIONS");
  }
  const active = new Map();
  let socket, reconnectTimer, idleTimer, stopped = false, attempts = 0, queued = 0;
  let work = Promise.resolve(), windowStart = 0, sent = 0;
  const lifetime = new AbortController();
  const fail = (error) => { stop(); onError(error); };
  function command(value) {
    if (stopped || socket?.readyState !== 1 || socket.bufferedAmount > 65536) return false;
    const now = Date.now();
    if (now - windowStart >= 1000) { windowStart = now; sent = 0; }
    const text = JSON.stringify(value);
    if (sent >= 32 || new TextEncoder().encode(text).length > 4096) return false;
    socket.send(text); sent++;
    return true;
  }
  function announce(subscription) {
    if (!command({ op: "subscribe", id: subscription.id, epoch: subscription.epoch,
      revisions: subscription.revisions() })) throw new Error("WOGE_SOCKET_SEND_LIMIT");
  }
  function subscribe(subscription) {
    if (stopped || !subscription || !/^[A-Za-z0-9_-]{1,64}$/.test(subscription.id) ||
        !/^[A-Za-z0-9_-]{1,256}$/.test(subscription.epoch) || typeof subscription.revisions !== "function" ||
        active.has(subscription.id) || active.size >= 16) throw new Error("WOGE_INVALID_SOCKET_SUBSCRIPTION");
    active.set(subscription.id, subscription);
    if (socket?.readyState === 1) announce(subscription);
  }
  function touch() {
    clearTimeout(idleTimer);
    idleTimer = setTimeout(() => socket?.close(4000, "idle_timeout"), idleTimeout);
  }
  async function open() {
    await work;
    if (stopped) return;
    try {
      const token = proof();
      if (!/^[A-Za-z0-9_-]{1,256}$/.test(token)) throw new Error("WOGE_INVALID_SOCKET_PROOF");
      const connection = new WebSocket(endpoint.href, ["woge.patch.v1", `woge.csrf.${token}`]);
      socket = connection;
      connection.binaryType = "arraybuffer";
      connection.onopen = () => {
        try {
          if (connection.protocol !== "woge.patch.v1") throw new Error("WOGE_SOCKET_PROTOCOL");
          windowStart = Date.now(); sent = 0;
          for (const subscription of active.values()) announce(subscription);
          touch();
        } catch (error) { fail(error); }
      };
      connection.onmessage = ({ data }) => {
        if (stopped || connection !== socket) return;
        touch();
        if (typeof data === "string") {
          if (data !== '{"op":"ping"}' || !command({ op: "pong" })) fail(new Error("WOGE_SOCKET_HEARTBEAT"));
          return;
        }
        if (!(data instanceof ArrayBuffer) || data.byteLength > maxFrameBytes || ++queued > maxQueuedFrames) {
          fail(new Error("WOGE_SOCKET_BACKPRESSURE")); return;
        }
        work = work.then(async () => {
          if (stopped) return;
          const bytes = new Uint8Array(data), length = bytes[0];
          const id = new TextDecoder("utf-8", { fatal: true }).decode(bytes.subarray(1, 1 + length));
          if (!length || length > 64 || bytes.length <= 1 + length) {
            throw new Error("WOGE_SOCKET_UNKNOWN_SUBSCRIPTION");
          }
          if (!active.has(id)) return;
          const stream = new ReadableStream({ start(controller) {
            controller.enqueue(bytes.subarray(1 + length)); controller.close();
          } });
          await runtime.applyPatchStream(stream, { signal: lifetime.signal });
        }).catch(fail).finally(() => { queued--; });
      };
      connection.onclose = ({ code, reason }) => {
        clearTimeout(idleTimer);
        onClose({ code, reason });
        if (stopped) return;
        if (code === 1008 || code === 1011 || ++attempts > maxReconnects) {
          fail(new Error("WOGE_SOCKET_RECONNECT_EXHAUSTED")); return;
        }
        const delay = Math.min(10000, 250 * 2 ** (attempts - 1)) * (0.75 + Math.random() * 0.5);
        reconnectTimer = setTimeout(() => { void open(); }, delay);
      };
    } catch (error) { fail(error); }
  }
  function stop() {
    if (stopped) return;
    stopped = true;
    clearTimeout(reconnectTimer); clearTimeout(idleTimer);
    lifetime.abort();
    signal?.removeEventListener("abort", stop);
    socket?.close(1000, "disposed");
  }
  for (const subscription of subscriptions) subscribe(subscription);
  signal?.addEventListener("abort", stop, { once: true });
  if (signal?.aborted) stop(); else void open();
  return Object.freeze({
    subscribe,
    unsubscribe(id) {
      if (socket?.readyState === 1 && !command({ op: "unsubscribe", id })) return false;
      return active.delete(id);
    },
    send(id, payload) {
      return active.has(id) && typeof payload === "string" && command({ op: "message", id, payload });
    },
    close: stop,
  });
}
