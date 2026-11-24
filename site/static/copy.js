if (navigator.clipboard?.writeText) {
  for (const code of document.querySelectorAll(".site-main pre > code")) {
    const toolbar = document.createElement("div");
    toolbar.className = "copy-toolbar";

    const status = document.createElement("span");
    status.className = "copy-status";
    status.setAttribute("role", "status");

    const button = document.createElement("button");
    button.type = "button";
    button.textContent = "Copy code";
    button.addEventListener("click", async () => {
      status.textContent = "";
      try {
        await navigator.clipboard.writeText(code.textContent);
        status.textContent = "Copied.";
      } catch {
        status.textContent = "Select the code to copy it.";
      }
    });

    toolbar.append(status, button);
    code.parentElement.before(toolbar);
  }
}
