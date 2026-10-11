import { performance } from "node:perf_hooks";
import { signal, computed, effect, batch } from "../dist/signals.js";

function run(batched) {
  const values = Array.from({ length: 1000 }, () => signal(0));
  const sum = computed(() => values.reduce((total, value) => total + value.get(), 0));
  let effects = 0, result;
  const stop = effect(() => { effects++; result = sum.get(); });
  const update = () => { for (let i = 0; i < 10000; i++) values[i % 1000].set(i + 1); };
  const start = performance.now();
  if (batched) batch(update); else update();
  const milliseconds = performance.now() - start;
  stop(); sum.dispose();
  if (result !== 9500500 || effects !== (batched ? 2 : 10001)) throw new Error("Incorrect benchmark result");
  return { milliseconds, effects };
}
for (const batched of [false, true]) {
  run(batched);
  const samples = Array.from({ length: 5 }, () => run(batched)).sort((a, b) => a.milliseconds - b.milliseconds);
  console.log(JSON.stringify({ node: process.version, signals: 1000, updates: 10000,
    batched, medianMs: Number(samples[2].milliseconds.toFixed(3)), effects: samples[2].effects }));
}
