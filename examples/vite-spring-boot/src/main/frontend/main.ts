import "./main.css";

const button = document.querySelector<HTMLButtonElement>("#show-chart");
const status = document.querySelector<HTMLElement>("#status");

// The chart code is a separate chunk that the browser loads only when it is needed.
button?.addEventListener("click", async () => {
  const { drawChart } = await import("./chart.ts");
  const list = document.querySelector<HTMLUListElement>("#visits");
  if (list) drawChart(list);
  if (status) status.textContent = "Chart ready";
});
