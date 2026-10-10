/**
 * Opens `a[data-woge-dialog]` links as modal `dialog` elements. Without this module the links
 * open their normal fallback page. One delegated listener covers links added later by patches.
 *
 * @param {Document | Element} [root]
 * @returns {() => void} removes the listener again
 */
export function installWogeDialogs(root = document) {
  const doc = root.ownerDocument ?? root;
  const onClick = (event) => {
    if (event.defaultPrevented || event.button !== 0) return;
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    const link = event.target instanceof Element ? event.target.closest("a[data-woge-dialog]") : null;
    if (!link || !root.contains(link)) return;
    const dialog = doc.getElementById(link.dataset.wogeDialog);
    if (!(dialog instanceof HTMLDialogElement) || dialog.open) return;
    event.preventDefault();
    dialog.showModal();
    dialog.addEventListener("close", () => returnFocus(doc, link), { once: true });
  };
  root.addEventListener("click", onClick);
  return () => root.removeEventListener("click", onClick);
}

// A patch may replace the link while the dialog is open; then its replacement gets focus.
function returnFocus(doc, link) {
  const target = link.isConnected
    ? link
    : doc.querySelector(`a[data-woge-dialog="${CSS.escape(link.dataset.wogeDialog)}"]`);
  target?.focus();
}
