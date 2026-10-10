import { PATCH_STREAM_MEDIA_TYPE, WogePatchError } from "./protocol.js";
import { classifyWogeFailure } from "./recovery.js";

export const ACTION_ERROR_EVENT = "woge:action-error";

/** Opt-in POST enhancement. Native submission remains the baseline for unsupported forms. */
export function installWogeActionForms(root, runtime) {
  if (!root?.defaultView || typeof runtime?.applyPatchStream !== "function") {
    throw new WogePatchError("WOGE_INVALID_ACTION_CONFIGURATION", "Actions require a document and patch runtime");
  }
  const active = new Map();
  const listener = (event) => {
    const form = event.target;
    if (active.has(form)) {
      event.preventDefault();
      return;
    }
    if (event.defaultPrevented) return;
    const submission = eligibleSubmission(root, form, event.submitter);
    if (!submission) return;
    event.preventDefault();
    const controller = new AbortController();
    const restore = busy(form, event.submitter);
    active.set(form, { controller, restore });
    void submit(root, runtime, submission, controller.signal)
      .catch((problem) => {
        const failure = classifyWogeFailure(problem);
        if (!controller.signal.aborted && failure.outcome !== "ignore-stale") {
          submission.alert.textContent = form.getAttribute("data-woge-failure-message");
          form.dispatchEvent(new root.defaultView.CustomEvent(ACTION_ERROR_EVENT, {
            bubbles: true,
            detail: Object.freeze({ code: failure.code }),
          }));
        }
      })
      .finally(() => {
        restore();
        active.delete(form);
      });
  };
  root.addEventListener("submit", listener);
  return Object.freeze({
    dispose() {
      root.removeEventListener("submit", listener);
      for (const { controller, restore } of active.values()) {
        controller.abort();
        restore();
      }
      active.clear();
    },
  });
}

function eligibleSubmission(root, form, submitter) {
  const view = root.defaultView;
  if (!(form instanceof view.HTMLFormElement) || !form.hasAttribute("data-woge-action")) return;
  if (submitter?.type === "image" || form.closest("dialog")) return;
  const override = (name) => submitter?.getAttribute(`form${name}`) ?? form.getAttribute(name);
  if ((override("method") ?? "get").toLowerCase() !== "post") return;
  if ((override("enctype") ?? "application/x-www-form-urlencoded").toLowerCase() !==
      "application/x-www-form-urlencoded") return;
  if (!["", "_self"].includes(override("target") ?? root.querySelector("base[target]")?.getAttribute("target") ?? "")) return;
  const charset = form.getAttribute("accept-charset")?.trim().toLowerCase();
  if (charset && charset !== "utf-8") return;
  let action;
  try {
    action = new URL(override("action") || root.URL, root.baseURI);
  } catch (problem) {
    if (problem instanceof TypeError) return;
    throw problem;
  }
  if (!safeUrl(action, view.location.origin)) return;
  const status = root.getElementById(form.getAttribute("data-woge-status"));
  const alert = root.getElementById(form.getAttribute("data-woge-alert"));
  if (status?.getAttribute("role") !== "status" || alert?.getAttribute("role") !== "alert" ||
      !form.getAttribute("data-woge-failure-message")) return;
  const data = new view.FormData(form, submitter ?? undefined);
  const body = new URLSearchParams();
  for (const [name, value] of data) {
    if (typeof value !== "string") return;
    body.append(normalizeLines(name), normalizeLines(value));
  }
  return { form, action, body, alert };
}

async function submit(root, runtime, submission, signal) {
  const response = await root.defaultView.fetch(submission.action, {
    method: "POST",
    body: submission.body,
    credentials: "same-origin",
    redirect: "error",
    headers: { Accept: PATCH_STREAM_MEDIA_TYPE },
    signal,
  });
  const navigation = response.headers.get("Woge-Navigate");
  if (navigation && response.ok) {
    await response.body?.cancel();
    const url = new URL(navigation, submission.action);
    if (!safeUrl(url, root.defaultView.location.origin)) {
      throw new WogePatchError("WOGE_UNSAFE_ACTION_NAVIGATION", "Action navigation must remain same-origin");
    }
    if (!signal.aborted) root.defaultView.location.assign(url.href);
    return;
  }
  if (!response.ok || response.headers.get("Content-Type")?.trim().toLowerCase() !== PATCH_STREAM_MEDIA_TYPE) {
    await response.body?.cancel();
    throw new WogePatchError("WOGE_ACTION_RESPONSE_REJECTED", "Action did not return a compatible patch stream");
  }
  await runtime.applyPatchStream(response.body, { signal });
}

function safeUrl(url, origin) {
  return ["http:", "https:"].includes(url.protocol) && url.origin === origin && !url.username && !url.password;
}

function normalizeLines(value) {
  return value.replace(/\r\n|\r|\n/g, "\r\n");
}

function busy(form, submitter) {
  const originalBusy = form.getAttribute("aria-busy");
  const originalDisabled = submitter?.getAttribute("aria-disabled");
  form.setAttribute("aria-busy", "true");
  submitter?.setAttribute("aria-disabled", "true");
  return () => {
    restoreAttribute(form, "aria-busy", originalBusy);
    if (submitter) restoreAttribute(submitter, "aria-disabled", originalDisabled);
  };
}

function restoreAttribute(element, name, value) {
  if (value === null) element.removeAttribute(name);
  else element.setAttribute(name, value);
}
