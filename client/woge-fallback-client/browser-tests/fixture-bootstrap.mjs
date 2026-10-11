const started = performance.now();
globalThis.Woge = await import("/woge-fallback.js");
globalThis.wogeModuleLoadParseEvalMs = performance.now() - started;
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
