/** Replaces the server-rendered list with bars; the counts come from its `data-count` attributes. */
export function drawChart(list: HTMLUListElement): void {
  const items = [...list.querySelectorAll<HTMLLIElement>("li")];
  const max = Math.max(...items.map((item) => Number(item.dataset.count)));
  for (const item of items) {
    const bar = document.createElement("div");
    bar.className = "bar";
    bar.style.inlineSize = `${(Number(item.dataset.count) / max) * 100}%`;
    item.append(bar);
  }
}
