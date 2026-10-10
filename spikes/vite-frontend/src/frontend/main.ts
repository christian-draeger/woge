import "./application.css";
import "./tailwind.css";

export const marker: string = "main-v1";

document.querySelector<HTMLButtonElement>("[data-load-chart]")?.addEventListener("click", async () => {
  const { renderChart } = await import("./chart");
  renderChart(document.querySelector<HTMLElement>("#chart")!);
});

if (import.meta.hot) {
  import.meta.hot.accept();
}
