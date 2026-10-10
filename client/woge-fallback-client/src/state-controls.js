const TEXT_TYPES = new Set([
  "text", "search", "url", "tel", "password", "email", "number", "date", "datetime-local",
  "month", "week", "time", "range", "color",
]);
const SELECTABLE_TYPES = new Set(["text", "search", "url", "tel", "password"]);

export function compatible(first, second) {
  return first.localName === second.localName && first.type === second.type &&
    first.multiple === second.multiple;
}

export function controlValue(element) {
  if (element.localName === "textarea" || element.localName === "input" && TEXT_TYPES.has(element.type)) {
    return { kind: "text", value: element.value, dirty: element.value !== element.defaultValue };
  }
  if (element.type === "checkbox" || element.type === "radio") {
    return { kind: "checked", value: element.checked, dirty: element.checked !== element.defaultChecked };
  }
  if (element.type === "file") return { kind: "file", dirty: element.files.length > 0 };
  if (element.localName === "select") {
    const selected = [...element.options].filter((option) => option.selected).map((option) => option.value);
    const defaults = [...element.options].filter((option) => option.defaultSelected).map((option) => option.value);
    if (!element.multiple && defaults.length === 0 && element.options.length) defaults.push(element.options[0].value);
    return { kind: "select", value: selected, dirty: JSON.stringify(selected) !== JSON.stringify(defaults) };
  }
  return null;
}

export function writeControlValue(element, control) {
  if (control.kind === "select" &&
      control.value.some((value) => ![...element.options].some((option) => option.value === value))) return false;
  if (control.kind === "text") element.value = control.value;
  if (control.kind === "checked") element.checked = control.value;
  if (control.kind === "select") {
    for (const option of element.options) option.selected = control.value.includes(option.value);
  }
  return true;
}

export function selection(element) {
  if (element.localName !== "textarea" && !SELECTABLE_TYPES.has(element.type)) return null;
  return { start: element.selectionStart, end: element.selectionEnd, direction: element.selectionDirection };
}
