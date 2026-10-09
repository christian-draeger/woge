const phases = new Set([
  "IDLE", "BUILDING", "BUILD_FAILED", "RELOAD_PENDING", "SERVER_RESTARTING",
  "SERVER_FAILED", "READY", "STOPPED",
]);
const labels = {
  IDLE: "Waiting for edits",
  BUILDING: "Rebuilding",
  BUILD_FAILED: "Build failed. Fix the source and save again.",
  RELOAD_PENDING: "Applying update",
  SERVER_RESTARTING: "Restarting",
  SERVER_FAILED: "Server failed. Fix the source and save again.",
  READY: "Ready",
  STOPPED: "Development session stopped",
};

function id(value) {
  if (typeof value !== "string" || !/^(0|[1-9][0-9]{0,18})$/.test(value)) {
    throw new TypeError("Invalid development identity");
  }
  return BigInt(value);
}

export function parseSnapshot(data) {
  const value = JSON.parse(data);
  if (value.version !== 1 || typeof value.session !== "string" ||
      !/^[a-f0-9-]{36}$/.test(value.session) || !phases.has(value.phase) ||
      !Array.isArray(value.diagnostics) || value.diagnostics.length > 20) {
    throw new TypeError("Invalid development snapshot");
  }
  for (const name of ["sequence", "build", "generation", "renderedBuild"]) id(value[name]);
  if (id(value.renderedBuild) > id(value.build)) throw new TypeError("Invalid ready build");
  for (const item of value.diagnostics) {
    if (typeof item.code !== "string" || typeof item.summary !== "string" ||
        item.summary.length > 1000 || (item.path !== undefined &&
          (typeof item.path !== "string" || !Number.isInteger(item.line) ||
            item.line < 1 || !Number.isInteger(item.column) || item.column < 1))) {
      throw new TypeError("Invalid development diagnostic");
    }
  }
  return value;
}

function createOverlay(details) {
  const panel = document.createElement("aside");
  panel.id = "woge-development-status";
  panel.setAttribute("aria-label", "Woge development status");
  const status = document.createElement("p");
  status.setAttribute("role", "status");
  status.setAttribute("aria-live", "polite");
  status.setAttribute("aria-atomic", "true");
  const errors = document.createElement("ul");
  const controls = document.createElement("div");
  const dismiss = document.createElement("button");
  dismiss.type = "button";
  dismiss.textContent = "Hide development status";
  dismiss.addEventListener("click", () => { panel.hidden = true; });
  controls.append(dismiss);
  if (details) {
    const link = document.createElement("a");
    link.href = details;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    link.textContent = "Raw build details";
    controls.append(link);
  }
  panel.append(status, errors, controls);
  document.body.append(panel);
  return (snapshot, connectionError = false) => {
    status.textContent = connectionError ? "Development channel disconnected. Reconnecting." : labels[snapshot.phase];
    panel.dataset.phase = connectionError ? "DISCONNECTED" : snapshot.phase;
    errors.replaceChildren();
    for (const diagnostic of snapshot.diagnostics) {
      const item = document.createElement("li");
      const location = diagnostic.path
        ? ` (${diagnostic.path}:${diagnostic.line}:${diagnostic.column})` : "";
      item.textContent = `${diagnostic.code}: ${diagnostic.summary}${location}`;
      errors.append(item);
    }
    if (snapshot.diagnosticsTruncated) {
      const item = document.createElement("li");
      item.textContent = "More diagnostics are available in the build details.";
      errors.append(item);
    }
  };
}

export function connectDevelopmentClient(config, { reload = () => location.reload() } = {}) {
  const endpoint = new URL(config.endpoint);
  if (endpoint.protocol !== "http:" ||
      !["127.0.0.1", "localhost", "[::1]"].includes(endpoint.hostname)) {
    throw new TypeError("A loopback development endpoint is required");
  }
  const source = new EventSource(endpoint);
  const render = config.overlay ? createOverlay(config.details) : () => {};
  let last = { phase: "IDLE", diagnostics: [] };
  let session = null;
  let sequence = -1n;
  let build = id(config.build);
  let generation = id(config.generation);
  let highestBuild = 0n;
  let highestGeneration = 0n;
  let reloading = false;
  const refreshIfReady = (next) => {
    const ready = next.renderedBuild !== undefined && id(next.renderedBuild) > 0n &&
      id(next.generation) > 0n &&
      !["SERVER_RESTARTING", "RELOAD_PENDING", "STOPPED", "SERVER_FAILED"].includes(next.phase);
    if (ready && navigator.onLine && !reloading &&
        (id(next.renderedBuild) > build || id(next.generation) > generation)) {
      reloading = true;
      build = id(next.renderedBuild);
      generation = id(next.generation);
      source.close();
      reload();
    }
  };
  source.addEventListener("snapshot", (event) => {
    try {
      const next = parseSnapshot(event.data);
      if (session !== null && next.session !== session) return;
      if (id(next.sequence) <= sequence || id(next.build) < highestBuild) return;
      // Zero means "no live server", not a generation going backwards.
      if (id(next.generation) !== 0n && id(next.generation) < highestGeneration) return;
      session = next.session;
      sequence = id(next.sequence);
      highestBuild = id(next.build);
      if (id(next.generation) !== 0n) highestGeneration = id(next.generation);
      last = next;
      render(next);
      refreshIfReady(next);
    } catch (error) {
      console.error("Woge development channel rejected an invalid snapshot", error);
    }
  });
  source.addEventListener("error", () => render(last, true));
  source.addEventListener("open", () => render(last));
  const online = () => refreshIfReady(last);
  window.addEventListener("online", online);
  const stop = () => {
    source.close();
    window.removeEventListener("online", online);
  };
  window.addEventListener("pagehide", stop, { once: true });
  return stop;
}

if (typeof document !== "undefined") {
  const meta = document.querySelector('meta[name="woge-development"]');
  if (meta) connectDevelopmentClient(JSON.parse(meta.content));
}
