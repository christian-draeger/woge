// Keep the ordinary form through peer refreshes; allow its own reply to render validation or clear it.
document.addEventListener("turbo:submit-start", (event) => {
    const form = event.target;
    if (form.id !== "add-todo") return;
    form.removeAttribute("data-turbo-permanent");
    form.addEventListener("turbo:submit-end", (submission) => {
        if (!submission.detail.fetchResponse) form.setAttribute("data-turbo-permanent", "true");
    }, { once: true });
});
