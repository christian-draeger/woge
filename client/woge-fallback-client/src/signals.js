let observer;
let depth = 0;
let flushing = false;
let nextEffect = 0;
const queued = new Set();

function track(source) {
  if (!observer || observer.disposed) return;
  source.subscribers.add(observer);
  observer.dependencies.add(source);
}

function detach(node) {
  for (const source of node.dependencies) source.subscribers.delete(node);
  node.dependencies.clear();
}

function evaluate(node, fn) {
  detach(node);
  const previous = observer;
  observer = node;
  try { return fn(); } finally { observer = previous; }
}

function invalidate(source) {
  for (const node of source.subscribers) {
    if (node.disposed) continue;
    if (node.effect) queued.add(node);
    else if (!node.dirty) {
      node.dirty = true;
      invalidate(node);
    }
  }
}

function flush() {
  if (depth || flushing) return;
  flushing = true;
  let problem;
  let failed = false;
  let turns = 0;
  try {
    while (queued.size) {
      if (++turns > 100) {
        queued.clear();
        throw new Error("WOGE_SIGNAL_EFFECT_CYCLE");
      }
      const pending = [...queued].sort((a, b) => a.order - b.order);
      for (const node of pending) {
        if (!queued.delete(node) || node.disposed) continue;
        try { node.run(); } catch (error) { if (!failed) problem = error; failed = true; }
      }
    }
    if (failed) throw problem;
  } finally { flushing = false; }
}

export function batch(fn) {
  depth++;
  try { return fn(); } finally { depth--; flush(); }
}

export function signal(initial) {
  let value = initial;
  const source = { subscribers: new Set() };
  return Object.freeze({
    get() { track(source); return value; },
    set(next) {
      if (Object.is(value, next)) return;
      value = next;
      invalidate(source);
      flush();
    },
  });
}

export function computed(fn) {
  let value;
  let running = false;
  const node = { subscribers: new Set(), dependencies: new Set(), dirty: true, disposed: false };
  return Object.freeze({
    get() {
      if (node.disposed) throw new Error("WOGE_SIGNAL_DISPOSED");
      if (running) throw new Error("WOGE_SIGNAL_COMPUTED_CYCLE");
      track(node);
      if (node.dirty) {
        running = true;
        try { value = evaluate(node, fn); node.dirty = false; } finally { running = false; }
      }
      return value;
    },
    dispose() { node.disposed = true; detach(node); node.subscribers.clear(); },
  });
}

export function effect(fn, { signal: lifetime } = {}) {
  let cleanup;
  const node = { dependencies: new Set(), effect: true, disposed: false, order: nextEffect++, run() {
    // Cleanup is deliberately untracked, even for an effect created inside another effect.
    const previous = observer;
    observer = undefined;
    try {
      const old = cleanup;
      cleanup = undefined;
      old?.();
      if (!node.disposed) {
        const result = evaluate(node, fn);
        if (node.disposed) result?.();
        else cleanup = result;
      }
    } finally { observer = previous; }
  } };
  const dispose = () => {
    if (node.disposed) return;
    node.disposed = true;
    queued.delete(node);
    detach(node);
    lifetime?.removeEventListener("abort", dispose);
    const old = cleanup;
    cleanup = undefined;
    const previous = observer;
    observer = undefined;
    try { old?.(); } finally { observer = previous; }
  };
  if (lifetime?.aborted) { node.disposed = true; return dispose; }
  lifetime?.addEventListener("abort", dispose, { once: true });
  try { batch(() => node.run()); } catch (error) { dispose(); throw error; }
  return dispose;
}

/** One authoritative latest-intent scope; transitions must be pure and values immutable. */
export function createOptimisticAction(state) {
  let base = state.get();
  let sequence = 0;
  let pending = [];
  let lastFailure = null;
  const failure = signal(null);
  const count = signal(0);
  const read = (source) => Object.freeze({ get: source.get });
  function publish(next, nextBase = base, error = lastFailure) {
    // Evaluate before committing so a bad transition cannot partially change the queue/base.
    let value = nextBase;
    for (const mutation of next) value = mutation.apply(value);
    batch(() => {
      pending = next;
      base = nextBase;
      count.set(next.length);
      lastFailure = error;
      failure.set(error);
      state.set(value);
    });
  }
  function settle(id, kind, value) {
    if (!pending.some((mutation) => mutation.id === id)) return false;
    if (kind === "success") {
      // A newer applied server snapshot supersedes older intent, including its pending handles.
      publish(pending.filter((mutation) => mutation.id > id), value);
    } else {
      publish(pending.filter((mutation) => mutation.id !== id), base,
        kind === "rejected" ? Object.freeze({ id, code: value }) : lastFailure);
    }
    return true;
  }
  return Object.freeze({
    pendingCount: read(count), failure: read(failure),
    submit(apply) {
      if (sequence === Number.MAX_SAFE_INTEGER) throw new Error("WOGE_OPTIMISTIC_SEQUENCE_EXHAUSTED");
      const id = ++sequence;
      const capturedBase = base;
      publish([...pending, { id, apply }], base, null);
      return Object.freeze({ id, base: capturedBase,
        resolve: (authoritative) => settle(id, "success", authoritative),
        reject: (code) => settle(id, "rejected", code),
        stale: () => settle(id, "stale"),
      });
    },
  });
}
