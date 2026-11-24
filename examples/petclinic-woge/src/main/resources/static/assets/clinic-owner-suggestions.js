export function mount(root, props, { signal, propsVersion }) {
  const document = root.ownerDocument;
  const form = root.querySelector("form");
  const input = form?.elements.namedItem("lastName");
  const results = root.querySelector("ul");
  const status = root.querySelector('[role="status"]');
  const endpoint = new URL(props.endpoint, document.URL);
  const owners = new URL(form?.action ?? "", document.URL);
  const min = Number(props.minLength);
  const max = Number(props.maxLength);
  const limit = Number(props.limit);
  if (propsVersion !== 1 || !input || !results || !status ||
      endpoint.origin !== owners.origin || endpoint.origin !== document.location.origin ||
      ![min, max, limit].every(value => Number.isSafeInteger(value) && value > 0) ||
      min > max) {
    throw new Error("Invalid owner suggestions configuration");
  }

  let pending;
  let query;
  function clear() {
    pending?.abort();
    pending = undefined;
    results.replaceChildren();
    status.textContent = "";
  }

  async function lookup() {
    const next = input.value.trim();
    if (next === query) return;
    query = next;
    clear();
    if (next.length < min || next.length > max) return;

    const request = new AbortController();
    pending = request;
    const url = new URL(endpoint);
    url.searchParams.set("lastName", next);
    try {
      const response = await fetch(url, {
        headers: { Accept: "application/json" },
        redirect: "error",
        signal: request.signal,
      });
      if (!response.ok) throw new Error("Lookup failed");
      const matches = await response.json();
      if (signal.aborted || pending !== request) return;
      if (!Array.isArray(matches) || matches.length > limit) throw new Error("Invalid owner suggestions");
      for (const match of matches) {
        if (!Number.isSafeInteger(match.id) || match.id < 1 || typeof match.label !== "string") {
          throw new Error("Invalid owner suggestion");
        }
      }

      const links = matches.map(({ id, label }) => {
        const item = document.createElement("li");
        const link = document.createElement("a");
        const target = new URL(owners);
        target.pathname = `${target.pathname}/${id}`;
        link.href = target.href;
        link.textContent = label;
        item.append(link);
        return item;
      });
      results.replaceChildren(...links);
      status.textContent = matches.length
        ? `${matches.length} owner suggestions. Tab to the links.`
        : "No owner suggestions.";
    } catch {
      if (!signal.aborted && pending === request) {
        status.textContent = "Suggestions are unavailable. Use Find owners to search.";
      }
    } finally {
      if (pending === request) pending = undefined;
    }
  }

  input.addEventListener("input", event => {
    if (!event.isComposing) void lookup();
  }, { signal });
  input.addEventListener("compositionend", () => void lookup(), { signal });
  root.addEventListener("keydown", event => {
    if (event.key !== "Escape" || (!pending && results.childElementCount === 0)) return;
    event.preventDefault();
    clear();
    query = undefined;
    input.focus();
  }, { signal });

  return { dispose: clear };
}
