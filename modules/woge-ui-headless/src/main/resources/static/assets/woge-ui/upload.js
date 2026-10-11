// Optional native multipart enhancement. No imports, patch frames or automatic mutation retries.
const active = new Map();
document.addEventListener("submit", (event) => {
  const form = event.target;
  if (active.has(form)) { event.preventDefault(); return; }
  if (event.defaultPrevented || !(form instanceof HTMLFormElement) || !globalThis.XMLHttpRequest ||
      !globalThis.FormData || !form.hasAttribute("data-woge-upload")) return;
  const submitter = event.submitter;
  const setting = (name) => submitter?.getAttribute(`form${name}`) ?? form.getAttribute(name);
  if (setting("method")?.toLowerCase() !== "post" || setting("enctype")?.toLowerCase() !== "multipart/form-data" ||
      ![null, "", "_self"].includes(setting("target") ?? document.querySelector("base[target]")?.getAttribute("target") ?? null) ||
      submitter?.type === "image" || form.closest("dialog")) return;
  let action;
  try { action = new URL(setting("action") || document.URL, document.baseURI); } catch { return; }
  if (action.origin !== location.origin || !/^https?:$/.test(action.protocol) || action.username || action.password) return;
  const panel = document.getElementById(form.getAttribute("data-woge-upload"));
  const progress = panel?.querySelector("progress");
  const status = panel?.querySelector('[role="status"]');
  const cancel = panel?.querySelector('button[type="button"]');
  if (!form.contains(panel) || !progress || !status || !cancel) return;
  let data;
  try { data = new FormData(form, submitter ?? undefined); } catch { return; }
  const xhr = new XMLHttpRequest();
  xhr.open("POST", action.href);
  xhr.setRequestHeader("Accept", "text/html");
  event.preventDefault();
  panel.hidden = false;
  progress.removeAttribute("value");
  form.setAttribute("aria-busy", "true");
  const previous = submitter?.getAttribute("aria-disabled");
  submitter?.setAttribute("aria-disabled", "true");
  cancel.hidden = false;
  status.textContent = panel.dataset.uploadStart;
  let announced = performance.now();
  let finished = false;
  const restore = (message, fallback = true) => {
    if (finished) return;
    finished = true;
    active.delete(form);
    form.removeAttribute("aria-busy");
    if (previous == null) submitter?.removeAttribute("aria-disabled");
    else submitter?.setAttribute("aria-disabled", previous);
    if (document.activeElement === cancel) submitter?.focus();
    cancel.hidden = true;
    cancel.removeEventListener("click", abort);
    status.textContent = message;
    if (fallback) form.removeAttribute("data-woge-upload");
  };
  const abort = () => xhr.abort();
  cancel.addEventListener("click", abort);
  xhr.upload.onprogress = (update) => {
    if (finished || !update.lengthComputable) return;
    const percent = Math.min(100, Math.floor(update.loaded / update.total * 100));
    progress.value = percent;
    if (performance.now() - announced >= 1000) {
      status.textContent = `${panel.dataset.uploadStart} ${percent}%`;
      announced = performance.now();
    }
  };
  xhr.onabort = () => restore(panel.dataset.uploadCancelled);
  xhr.onerror = xhr.ontimeout = () => restore(panel.dataset.uploadFailed);
  xhr.onload = () => {
    const destination = new URL(xhr.responseURL || action.href);
    // Follow the normal POST/redirect/GET workflow. Never replay an uncertain POST.
    if (xhr.status >= 200 && xhr.status < 300 && destination.origin === location.origin &&
        destination.href !== action.href) {
      progress.value = 100;
      restore(panel.dataset.uploadComplete, false);
      location.assign(destination.href);
    } else restore(panel.dataset.uploadFailed);
  };
  active.set(form, xhr);
  try { xhr.send(data); } catch { restore(panel.dataset.uploadFailed); }
});
addEventListener("pagehide", () => { for (const xhr of active.values()) xhr.abort(); });
