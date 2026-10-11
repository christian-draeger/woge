/** Application-owned ES module: no framework, hydration or Node build required. */
export function mount(root, props, { signal }) {
  const input = root.querySelector("input");
  const count = root.querySelector('[role="status"]');
  let maximum = limit(props);
  function render() { count.textContent = `${input.value.length} / ${maximum} UTF-16 code units`; }
  input.addEventListener("input", render, { signal });
  render();
  return {
    update(next) { maximum = limit(next); render(); },
    dispose() { input.removeEventListener("input", render); },
  };
}
function limit(props) {
  const value = Number(props.maxLength);
  if (!Number.isSafeInteger(value) || value < 1) throw new Error("Invalid note limit");
  return value;
}
