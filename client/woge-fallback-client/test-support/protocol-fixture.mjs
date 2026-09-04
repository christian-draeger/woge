import { readFile } from "node:fs/promises";

const encoder = new TextEncoder();

export function patchFrame({
  patchId = "patch-1",
  target = "summary-1",
  epoch = "epoch-a",
  interactionSequence = 0,
  baseRevision = 0,
  nextRevision = 1,
  html = "<p>Updated</p>",
} = {}) {
  const metadata =
    `{"protocolVersion":1,"operation":"replace","patchId":"${patchId}",` +
    `"epoch":"${epoch}","target":"${target}",` +
    `"interactionSequence":${interactionSequence},"baseRevision":${baseRevision},` +
    `"nextRevision":${nextRevision}}`;
  return rawFrame(1, "text/html; charset=utf-8", metadata, html);
}

export function completeFrame(patchCount = 1) {
  return rawFrame(2, "application/json; charset=utf-8", `{"patches":${patchCount}}`, "");
}

export function errorFrame({
  code = "WOGE_RENDER_FAILED",
  correlationId = "trace-1",
  recovery = "reload",
} = {}) {
  const metadata = `{"code":"${code}","correlationId":"${correlationId}","recovery":"${recovery}"}`;
  return rawFrame(3, "application/problem+json; charset=utf-8", metadata, "");
}

export function rawFrame(kind, contentType, metadata, payload) {
  const contentTypeBytes = asBytes(contentType);
  const metadataBytes = asBytes(metadata);
  const payloadBytes = asBytes(payload);
  const header = new Uint8Array(10);
  const view = new DataView(header.buffer);
  view.setUint8(0, kind);
  view.setUint8(1, contentTypeBytes.byteLength);
  view.setUint32(2, metadataBytes.byteLength, false);
  view.setUint32(6, payloadBytes.byteLength, false);
  return concatenate([header, contentTypeBytes, metadataBytes, payloadBytes]);
}

export function encodeStream(frames) {
  return concatenate([Uint8Array.of(0x57, 0x4f, 0x47, 0x45, 0x01), ...frames]);
}

export async function readGoldenStream() {
  const value = await readFile(
    new URL("../../../modules/woge-protocol/src/test/resources/fixtures/patch-stream-v1.hex", import.meta.url),
    "utf8",
  );
  return Uint8Array.from(value.trim().match(/.{2}/g), (pair) => Number.parseInt(pair, 16));
}

function asBytes(value) {
  return value instanceof Uint8Array ? value : encoder.encode(value);
}

function concatenate(parts) {
  const result = new Uint8Array(parts.reduce((size, part) => size + part.byteLength, 0));
  let offset = 0;
  for (const part of parts) {
    result.set(part, offset);
    offset += part.byteLength;
  }
  return result;
}
