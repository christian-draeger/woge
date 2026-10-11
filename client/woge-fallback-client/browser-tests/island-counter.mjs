export function mount(root, props, { signal }) {
  globalThis.mountCalls = (globalThis.mountCalls ?? 0) + 1;
  const input = root.querySelector("input");
  const status = root.querySelector('[role="status"]');
  function render() { status.textContent = `${input.value.length} / ${props.maximum}`; }
  input.addEventListener("input", render, { signal });
  render();
  return {
    update(next) { globalThis.updateCalls = (globalThis.updateCalls ?? 0) + 1; props = next; render(); },
    dispose() {
      globalThis.disposals = [...(globalThis.disposals ?? []), root.isConnected];
      input.removeEventListener("input", render);
    },
  };
}
