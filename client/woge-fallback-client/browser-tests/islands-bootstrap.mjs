globalThis.islandErrors = [];
document.addEventListener("woge:island-error", (event) => islandErrors.push(event.detail.code));
globalThis.Woge = await import("/woge-fallback.js");
globalThis.runtime = Woge.createWogePatchRuntime(document);
const module = await import("/islands.js");
globalThis.islands = module.installWogeIslands();
