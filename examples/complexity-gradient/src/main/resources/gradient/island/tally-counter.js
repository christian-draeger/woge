// A local island: the count lives in the browser and survives region patches.
class TallyCounter extends HTMLElement {
  connectedCallback() {
    if (this.querySelector("button")) return;
    let count = 0;
    const button = document.createElement("button");
    button.type = "button";
    const show = () => {
      button.textContent = `Tally: ${count}`;
    };
    button.addEventListener("click", () => {
      count += 1;
      show();
    });
    show();
    this.replaceChildren(button);
  }
}

customElements.define("tally-counter", TallyCounter);
