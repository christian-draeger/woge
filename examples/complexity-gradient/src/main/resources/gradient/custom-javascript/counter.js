// Shows the remaining characters. Without this script the static hint still tells the limit.
for (const field of document.querySelectorAll("textarea[data-counter]")) {
  const hint = document.getElementById(field.dataset.counter);
  const update = () => {
    hint.textContent = `${field.maxLength - field.value.length} characters left`;
  };
  field.addEventListener("input", update);
  update();
}
