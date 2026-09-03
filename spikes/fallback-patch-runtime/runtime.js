(() => {
  "use strict";

  const MAGIC = Uint8Array.of(0x57, 0x4f, 0x47, 0x45, 0x01);
  const PATCH = 1;
  const COMPLETE = 2;
  const ERROR = 3;
  const MAX_METADATA_BYTES = 64 * 1024;
  const MAX_PAYLOAD_BYTES = 8 * 1024 * 1024;
  const textDecoder = new TextDecoder("utf-8", { fatal: true });
  const blockedElements = "script,iframe,object,embed,base,meta[http-equiv]";
  const urlAttributes = new Set(["href", "src", "action", "formaction", "xlink:href"]);

  class WogePatchError extends Error {
    constructor(code, message) {
      super(message);
      this.name = "WogePatchError";
      this.code = code;
    }
  }

  class FrameDecoder {
    #buffer = new Uint8Array();
    #magicRead = false;
    #terminalRead = false;

    push(chunk) {
      if (!(chunk instanceof Uint8Array)) fail("WOGE_FRAME_TYPE", "Frame chunk must be bytes");
      if (this.#terminalRead && chunk.byteLength > 0) fail("WOGE_BYTES_AFTER_TERMINAL", "Bytes follow terminal frame");
      this.#buffer = concat(this.#buffer, chunk);
      const frames = [];

      if (!this.#magicRead) {
        if (this.#buffer.byteLength < MAGIC.byteLength) return frames;
        for (let index = 0; index < MAGIC.byteLength; index += 1) {
          if (this.#buffer[index] !== MAGIC[index]) fail("WOGE_PROTOCOL_VERSION", "Unknown stream preamble or version");
        }
        this.#buffer = this.#buffer.slice(MAGIC.byteLength);
        this.#magicRead = true;
      }

      while (!this.#terminalRead && this.#buffer.byteLength >= 10) {
        const view = new DataView(this.#buffer.buffer, this.#buffer.byteOffset, 10);
        const kind = view.getUint8(0);
        const contentTypeLength = view.getUint8(1);
        const metadataLength = view.getUint32(2, false);
        const payloadLength = view.getUint32(6, false);
        if (![PATCH, COMPLETE, ERROR].includes(kind)) fail("WOGE_FRAME_KIND", `Unknown frame kind: ${kind}`);
        if (metadataLength > MAX_METADATA_BYTES) fail("WOGE_METADATA_LIMIT", "Frame metadata exceeds 64 KiB");
        if (payloadLength > MAX_PAYLOAD_BYTES) fail("WOGE_PAYLOAD_LIMIT", "Frame payload exceeds 8 MiB");
        const frameLength = 10 + contentTypeLength + metadataLength + payloadLength;
        if (this.#buffer.byteLength < frameLength) return frames;

        let offset = 10;
        const contentType = decodeAscii(this.#buffer.slice(offset, offset + contentTypeLength));
        offset += contentTypeLength;
        const metadataText = decode(textDecoder, this.#buffer.slice(offset, offset + metadataLength), "WOGE_METADATA_ENCODING");
        offset += metadataLength;
        const payload = this.#buffer.slice(offset, offset + payloadLength);
        if ((kind === COMPLETE || kind === ERROR) && payload.byteLength !== 0) {
          fail("WOGE_TERMINAL_PAYLOAD", "Terminal frame payload must be empty");
        }
        frames.push({ kind, contentType, metadataText, payload });
        this.#buffer = this.#buffer.slice(frameLength);
        this.#terminalRead = kind === COMPLETE || kind === ERROR;
      }

      if (this.#terminalRead && this.#buffer.byteLength > 0) fail("WOGE_BYTES_AFTER_TERMINAL", "Bytes follow terminal frame");
      return frames;
    }

    finish() {
      if (!this.#magicRead) fail("WOGE_MISSING_PREAMBLE", "Stream has no complete preamble");
      if (this.#buffer.byteLength > 0) fail("WOGE_TRUNCATED_FRAME", "Stream ended inside a frame");
      if (!this.#terminalRead) fail("WOGE_MISSING_TERMINAL", "Stream ended without completion or error");
    }
  }

  async function applyPatchStream(stream, root = document) {
    const epoch = root.querySelector('meta[name="woge-page-epoch"]')?.content;
    if (!epoch) fail("WOGE_PAGE_EPOCH_MISSING", "Document has no page epoch");
    const regions = collectRegions(root);
    const decoder = new FrameDecoder();
    const reader = stream.getReader();
    let completeMetadata;

    try {
      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        for (const frame of decoder.push(value)) {
          const metadata = parseMetadata(frame.metadataText);
          if (frame.kind === PATCH) applyReplace(frame, metadata, epoch, regions, root);
          if (frame.kind === COMPLETE) completeMetadata = metadata;
          if (frame.kind === ERROR) fail(metadata.code || "WOGE_REMOTE_ERROR", "Server ended patch stream with an error");
        }
      }
      decoder.finish();
      return completeMetadata;
    } finally {
      reader.releaseLock();
    }
  }

  function applyReplace(frame, metadata, epoch, regions, root) {
    if (frame.contentType.toLowerCase() !== "text/html; charset=utf-8") {
      fail("WOGE_PATCH_CONTENT_TYPE", "Replace patch is not UTF-8 HTML");
    }
    if (metadata.operation !== "replace" || metadata.protocolVersion !== 1) {
      fail("WOGE_PATCH_METADATA", "Unsupported patch operation or protocol");
    }
    if (metadata.epoch !== epoch) fail("WOGE_PAGE_EPOCH_STALE", "Patch belongs to another document");
    const target = regions.get(metadata.target);
    if (!target) fail("WOGE_TARGET_UNKNOWN", "Patch target is not in the active document");
    const currentRevision = Number.parseInt(target.dataset.wogeRevision || "", 10);
    if (!Number.isSafeInteger(currentRevision) || metadata.baseRevision !== currentRevision || metadata.nextRevision !== currentRevision + 1) {
      fail("WOGE_REVISION_MISMATCH", "Patch does not continue the target revision");
    }

    const html = decode(textDecoder, frame.payload, "WOGE_HTML_ENCODING");
    const template = root.createElement("template");
    template.innerHTML = html;
    validateInertFragment(template.content);
    target.replaceChildren(template.content.cloneNode(true));
    target.dataset.wogeRevision = String(metadata.nextRevision);
  }

  function collectRegions(root) {
    const regions = new Map();
    for (const element of root.querySelectorAll("[data-woge-region]")) {
      const id = element.getAttribute("data-woge-region");
      if (!id || regions.has(id)) fail("WOGE_TARGET_DUPLICATE", "Region IDs must be unique");
      regions.set(id, element);
    }
    return regions;
  }

  function validateInertFragment(fragment) {
    for (const element of fragment.querySelectorAll("*")) {
      if (element.matches(blockedElements)) fail("WOGE_PATCH_ACTIVE_ELEMENT", `Patch contains blocked <${element.localName}>`);
      for (const attribute of element.attributes) {
        const name = attribute.name.toLowerCase();
        if (name.startsWith("on") || name === "srcdoc") fail("WOGE_PATCH_ACTIVE_ATTRIBUTE", `Patch contains blocked ${name}`);
        if (urlAttributes.has(name) && /^(?:[\u0000-\u0020]*)(?:javascript|vbscript|data):/i.test(attribute.value)) {
          fail("WOGE_PATCH_ACTIVE_URL", `Patch contains blocked ${name} URL`);
        }
      }
      if (element instanceof HTMLTemplateElement) validateInertFragment(element.content);
    }
  }

  function parseMetadata(value) {
    try {
      const parsed = JSON.parse(value);
      if (!parsed || Array.isArray(parsed) || typeof parsed !== "object") throw new Error("metadata is not an object");
      return parsed;
    } catch {
      fail("WOGE_METADATA_JSON", "Frame metadata is not valid JSON");
    }
  }

  function concat(left, right) {
    const result = new Uint8Array(left.byteLength + right.byteLength);
    result.set(left, 0);
    result.set(right, left.byteLength);
    return result;
  }

  function decode(decoder, bytes, code) {
    try {
      return decoder.decode(bytes);
    } catch {
      fail(code, "Frame text has invalid encoding");
    }
  }

  function decodeAscii(bytes) {
    let value = "";
    for (const byte of bytes) {
      if (byte > 0x7f) fail("WOGE_CONTENT_TYPE_ENCODING", "Content type is not ASCII");
      value += String.fromCharCode(byte);
    }
    return value;
  }

  function fail(code, message) {
    throw new WogePatchError(code, message);
  }

  globalThis.WogeFallback = Object.freeze({ applyPatchStream, FrameDecoder, WogePatchError });
})();
