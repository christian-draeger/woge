import { createWogePatchRuntime } from "/woge-fallback.js";
import { installWogeIslands } from "/islands.js";
globalThis.runtime = createWogePatchRuntime(document);
globalThis.islands = installWogeIslands();
