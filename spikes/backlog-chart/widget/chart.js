import { CategoryScale, Chart, LineController, LineElement, LinearScale, PointElement } from "chart.js";
import { installWogeIslands } from "/assets/woge/islands.js";

Chart.register(CategoryScale, LineController, LineElement, LinearScale, PointElement);

function parse({ days, packing, dispatch }) {
  const labels = days.split(",");
  const counts = [packing, dispatch].map((column) => column.split(","));
  if (labels.length !== 14 || !labels.every((day) => /^\d{4}-\d{2}-\d{2}$/.test(day)) ||
      counts.some((column) => column.length !== labels.length || column.some((value) => !/^\d{1,4}$/.test(value)))) {
    throw new Error("Invalid backlog chart data");
  }
  return { labels, values: counts.map((column) => column.map(Number)) };
}

export function mount(root, props, { signal, propsVersion }) {
  if (propsVersion !== 1) throw new Error("Unsupported backlog chart data version");
  const initial = parse(props);
  const panel = root.parentElement;
  const controls = root.querySelector("[data-chart-controls]");
  const canvas = root.querySelector("canvas");
  const packing = controls?.querySelector("[data-packing]");
  const latest = controls?.querySelector("[data-latest]");
  if (!panel || !controls || !canvas || !packing || !latest) {
    throw new Error("Missing backlog chart markup");
  }
  const chart = new Chart(canvas, {
    type: "line",
    data: {
      labels: initial.labels,
      datasets: [
        { label: "Packing", data: initial.values[0], borderColor: "#12618a" },
        { label: "Dispatch", data: initial.values[1], borderColor: "#8a3c17", borderDash: [6, 4] },
      ],
    },
    options: {
      animation: false,
      responsive: false,
      events: [],
      scales: { x: { ticks: { maxTicksLimit: 5 } }, y: { beginAtZero: true } },
    },
  });

  packing.addEventListener("click", () => {
    const visible = !chart.isDatasetVisible(0);
    chart.setDatasetVisibility(0, visible);
    packing.setAttribute("aria-pressed", String(visible));
    chart.update("none");
  }, { signal });
  latest.addEventListener("click", () => {
    const narrow = latest.getAttribute("aria-pressed") !== "true";
    latest.setAttribute("aria-pressed", String(narrow));
    chart.options.scales.x.min = narrow ? chart.data.labels.at(-4) : undefined;
    chart.update("none");
  }, { signal });

  // A keyed root and its original props survive the parent patch. Read the new
  // public values from the server-owned sibling table and update explicitly.
  panel.addEventListener("woge:after-replace", ({ target }) => {
    if (target !== panel || !root.isConnected) return;
    const columns = Array.from(panel.querySelectorAll("tbody tr"), (row) =>
      Array.from(row.cells, (cell) => cell.textContent));
    const field = (index) => columns.map((row) => row[index]).join(",");
    installWogeIslands().update(root, { days: field(0), packing: field(1), dispatch: field(2) });
  }, { signal });

  controls.hidden = false;
  return {
    update(next) {
      const { labels, values } = parse(next);
      chart.data.labels = labels;
      chart.data.datasets.forEach((dataset, index) => { dataset.data = values[index]; });
      chart.options.scales.x.min = latest.getAttribute("aria-pressed") === "true" ? labels.at(-4) : undefined;
      chart.update("none");
    },
    dispose() { chart.destroy(); },
  };
}
