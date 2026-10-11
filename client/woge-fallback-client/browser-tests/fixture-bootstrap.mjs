const started = performance.now();
globalThis.Woge = await import("/woge-fallback.js");
globalThis.wogeModuleLoadParseEvalMs = performance.now() - started;
// Served only by the test server: install fallback regardless of browser capabilities.
if (new URL(location.href).searchParams.get("runtime") === "fallback") {
  globalThis.forcedFallbackRuntime = Woge.createWogePatchRuntime(document);
}
globalThis.byteStream = (bytes, splitAt) => {
  const chunks =
    splitAt === "one-byte"
      ? Array.from(bytes, (byte) => Uint8Array.of(byte))
      : typeof splitAt === "number"
        ? [bytes.slice(0, splitAt), bytes.slice(splitAt)]
        : [bytes];
  return new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(chunk));
      controller.close();
    },
  });
};
