/** DOM adapter only: all field rules and draft transitions execute in compiled common Kotlin. */
import { reviewNote } from "./note-rules/woge-woge-reference-shared-rules.mjs";

export function mount(root, props, { signal, propsVersion }) {
  const input = root.querySelector("input");
  const count = root.querySelector('[role="status"]');
  let maximum = limit(props, propsVersion);
  function render() {
    const [phase, code, message] = reviewNote(input.value, maximum, propsVersion);
    root.dataset.draftPhase = phase;
    root.dataset.errorCode = code;
    input.setAttribute("aria-invalid", code ? "true" : "false");
    const counter = `${input.value.length} / ${maximum} UTF-16 code units`;
    count.textContent = code ? `${counter}. text: ${code} — ${message}` : counter;
  }
  input.addEventListener("input", render, { signal });
  render();
  return {
    update(next) { maximum = limit(next, propsVersion); render(); },
    dispose() { input.removeEventListener("input", render); },
  };
}
function limit(props, version) {
  if (Object.keys(props).length !== 1 || typeof props.maxLength !== "string") {
    throw new Error("Invalid note props");
  }
  reviewNote("", props.maxLength, version);
  return props.maxLength;
}
