const encoder = new TextEncoder();

export function patchFrame({
  target = "summary-1",
  epoch = "epoch-a",
  baseRevision = 0,
  nextRevision = 1,
  html = "<p>Updated</p>"
} = {}) {
  return {
    kind: 1,
    contentType: "text/html; charset=utf-8",
    metadata: { protocolVersion: 1, operation: "replace", target, epoch, baseRevision, nextRevision },
    payload: encoder.encode(html)
  };
}

export function completeFrame() {
  return { kind: 2, contentType: "application/json; charset=utf-8", metadata: { patches: 1 }, payload: new Uint8Array() };
}

export function errorFrame() {
  return {
    kind: 3,
    contentType: "application/problem+json; charset=utf-8",
    metadata: { code: "WOGE_RENDER_FAILED", correlationId: "test-1" },
    payload: new Uint8Array()
  };
}

export function encodeFrames(frames) {
  const parts = [Uint8Array.of(0x57, 0x4f, 0x47, 0x45, 0x01)];
  for (const frame of frames) {
    const contentType = encoder.encode(frame.contentType);
    const metadata = encoder.encode(JSON.stringify(frame.metadata));
    const header = new Uint8Array(10);
    const view = new DataView(header.buffer);
    view.setUint8(0, frame.kind);
    view.setUint8(1, contentType.byteLength);
    view.setUint32(2, metadata.byteLength, false);
    view.setUint32(6, frame.payload.byteLength, false);
    parts.push(header, contentType, metadata, frame.payload);
  }
  return concat(parts);
}

function concat(parts) {
  const result = new Uint8Array(parts.reduce((size, part) => size + part.byteLength, 0));
  let offset = 0;
  for (const part of parts) {
    result.set(part, offset);
    offset += part.byteLength;
  }
  return result;
}
