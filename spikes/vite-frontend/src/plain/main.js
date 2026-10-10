document.querySelector("[data-load-chart]")?.addEventListener("click", async () => {
  const { renderChart } = await import("./chart.js");
  renderChart(document.querySelector("#chart"));
});
