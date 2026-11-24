const endpoint = new URLSearchParams({ filter: new URL(location.href).searchParams.get("filter") ?? "all" });
const title = () => document.querySelector("#todo-title");
const report = message => { document.querySelector("#todo-alert").textContent = message; };
const isValidation = xhr => xhr.status === 400 && xhr.getResponseHeader("X-Todo-Validation") === "1";
let submitted;
let refreshRequest;
let refreshPending = false;

document.addEventListener("htmx:beforeRequest", ({ detail }) => {
    if (detail.elt?.id !== "add-todo") return;
    submitted = { xhr: detail.xhr, value: title().value };
    refreshRequest?.abort();
});
document.addEventListener("htmx:beforeSwap", ({ detail }) => {
    if (!submitted || detail.xhr !== submitted.xhr) return;
    if (isValidation(detail.xhr)) {
        detail.shouldSwap = true;
        detail.isError = false;
    }
    const input = title();
    if (input.value !== submitted.value) {
        submitted.draft = {
            value: input.value,
            start: input.selectionStart,
            end: input.selectionEnd,
            focused: input === document.activeElement,
        };
    }
});
document.addEventListener("htmx:afterSwap", ({ detail }) => {
    if (!submitted || detail.xhr !== submitted.xhr || detail.target?.id !== "todo-form") return;
    const draft = submitted.draft;
    if (draft) {
        const input = title();
        input.value = draft.value;
        input.setSelectionRange(draft.start, draft.end);
        if (draft.focused) input.focus();
    }
    if (isValidation(detail.xhr)) document.querySelector("#todo-errors").focus();
});
document.addEventListener("htmx:afterRequest", ({ detail }) => {
    if (!submitted || detail.xhr !== submitted.xhr) return;
    submitted = undefined;
    report(detail.failed ? "Could not confirm the result. Reload before submitting again." : "");
    if (refreshPending) void refreshBoard();
});

async function refreshBoard() {
    refreshPending = true;
    if (submitted || refreshRequest) return;
    const request = new AbortController();
    refreshRequest = request;
    refreshPending = false;
    try {
        const response = await fetch(`/board?${endpoint}`, { signal: request.signal });
        if (!response.ok || !response.headers.get("Content-Type")?.startsWith("text/html")) {
            throw new Error("Unexpected live response");
        }
        const html = await response.text();
        if (submitted || request.signal.aborted) refreshPending = true;
        else htmx.swap("#todo-board", html, { swapStyle: "outerHTML" });
    } catch (error) {
        if (error.name === "AbortError") refreshPending = true;
        else report("Live updates are unavailable. Reload to refresh.");
    } finally {
        refreshRequest = undefined;
        if (refreshPending && !submitted && !hidden) void refreshBoard();
    }
}

let hidden = false;
const events = new EventSource(`/events?${endpoint}`);
events.addEventListener("todo-change", () => { if (!hidden) void refreshBoard(); });
addEventListener("pagehide", () => {
    hidden = true;
    events.close();
    refreshRequest?.abort();
});
