import { WOGE_PATCH_PROTOCOL_VERSION } from "./version.js";

const PREAMBLE_MAGIC = Uint8Array.of(0x57, 0x4f, 0x47, 0x45);
const PREAMBLE_BYTES = 5;
const HEADER_BYTES = 10;
const PATCH = 1;
const COMPLETE = 2;
const ERROR = 3;
const TRANSACTION = 4;
const MAX_METADATA_BYTES = 64 * 1024;
const MAX_PAYLOAD_BYTES = 8 * 1024 * 1024;
const MAX_CONTENT_TYPE_BYTES = 255;
const MAX_BUFFER_BYTES = HEADER_BYTES + MAX_CONTENT_TYPE_BYTES + MAX_METADATA_BYTES + MAX_PAYLOAD_BYTES;
const MAX_SIGNED_LONG = 9_223_372_036_854_775_807n;
const MAX_PATCH_COUNT = 2_147_483_647;
const PATCH_CONTENT_TYPE = "text/html; charset=utf-8";
const COMPLETE_CONTENT_TYPE = "application/json; charset=utf-8";
const ERROR_CONTENT_TYPE = "application/problem+json; charset=utf-8";
const decoder = new TextDecoder("utf-8", { fatal: true });

const opaqueId = "([A-Za-z0-9_-]{1,256})";
const unsignedLong = "(0|[1-9][0-9]{0,18})";
const patchMetadataPattern = new RegExp(
  `^\\{"protocolVersion":(0|[1-9][0-9]{0,9}),"operation":"([a-z]+)",` +
    `"patchId":"${opaqueId}","epoch":"${opaqueId}","target":"${opaqueId}",` +
    `"interactionSequence":${unsignedLong},"baseRevision":${unsignedLong},` +
    `"nextRevision":${unsignedLong}(?:,"itemId":"${opaqueId}"(?:,"focusTarget":"${opaqueId}")?)?\\}$`,
);
const completionMetadataPattern = /^\{"patches":(0|[1-9][0-9]{0,9})\}$/;
const errorMetadataPattern =
  /^\{"code":"(WOGE_[A-Z0-9_]{0,123})","correlationId":"([A-Za-z0-9._:-]{1,128})","recovery":"(none|reload)"\}$/;

/** A stable local framing, validation or DOM-application failure. */
export class WogePatchError extends Error {
  constructor(code, message) {
    super(message);
    this.name = "WogePatchError";
    this.code = code;
  }
}

/** A safe application failure deliberately sent by the server as a terminal frame. */
export class WogeRemotePatchError extends WogePatchError {
  constructor(failure) {
    super(failure.code, "The server ended the patch stream with a safe application failure");
    this.name = "WogeRemotePatchError";
    this.correlationId = failure.correlationId;
    this.recovery = failure.recovery;
  }
}

/** Incrementally decodes complete events from arbitrary Uint8Array transport chunks. */
export class PatchStreamDecoder {
  #buffer = new BoundedByteBuffer(MAX_BUFFER_BYTES);
  #preambleRead = false;
  #terminalRead = false;
  #patchCount = 0;
  #failure;
  #limits;
  #receivedBytes = 0;

  constructor(limits = {}) {
    this.#limits = patchStreamLimits(limits);
  }

  push(chunk) {
    return this.#guard(() => {
      if (!(chunk instanceof Uint8Array)) {
        fail("WOGE_INVALID_CHUNK", "Expected Uint8Array chunks");
      }
      if (this.#terminalRead && chunk.byteLength > 0) {
        fail("WOGE_BYTES_AFTER_TERMINAL", "Bytes follow the terminal frame");
      }
      if (chunk.byteLength > this.#limits.maxStreamBytes - this.#receivedBytes) {
        exceedBudget("PATCH_STREAM_BYTES", this.#limits.maxStreamBytes);
      }
      this.#receivedBytes += chunk.byteLength;

      const events = [];
      let offset = 0;
      while (offset < chunk.byteLength) {
        this.#drain(events);
        if (this.#terminalRead) {
          fail("WOGE_BYTES_AFTER_TERMINAL", "Bytes follow the terminal frame");
        }

        const capacity = MAX_BUFFER_BYTES - this.#buffer.length;
        if (capacity <= 0) fail("WOGE_INVALID_LENGTH", "Frame exceeds buffer limit");
        const count = Math.min(capacity, chunk.byteLength - offset);
        this.#buffer.append(chunk.subarray(offset, offset + count));
        offset += count;
      }
      this.#drain(events);
      return events;
    });
  }

  finish() {
    this.#guard(() => {
      if (!this.#preambleRead && this.#buffer.length === 0) {
        fail("WOGE_INVALID_PREAMBLE", "Missing stream preamble");
      }
      if (!this.#preambleRead || this.#buffer.length > 0) {
        fail("WOGE_TRUNCATED_STREAM", "Truncated frame");
      }
      if (!this.#terminalRead) {
        fail("WOGE_MISSING_TERMINAL", "Missing terminal frame");
      }
    });
  }

  #drain(events) {
    this.#readPreamble();
    if (!this.#preambleRead) return;

    while (!this.#terminalRead && this.#buffer.length >= HEADER_BYTES) {
      const header = this.#buffer.view(0, HEADER_BYTES);
      const view = new DataView(header.buffer, header.byteOffset, header.byteLength);
      const kind = view.getUint8(0);
      const contentTypeLength = view.getUint8(1);
      const metadataLength = view.getUint32(2, false);
      const payloadLength = view.getUint32(6, false);
      validateKind(kind);
      validateLengths(contentTypeLength, metadataLength, payloadLength);
      if ((kind === PATCH || kind === TRANSACTION) && this.#patchCount === this.#limits.maxPatches) {
        exceedBudget("PATCH_COUNT", this.#limits.maxPatches);
      }
      if ((kind === COMPLETE || kind === ERROR) && payloadLength !== 0) {
        fail("WOGE_INVALID_LENGTH", "Nonempty terminal payload");
      }

      const frameLength = HEADER_BYTES + contentTypeLength + metadataLength + payloadLength;
      if (this.#buffer.length < frameLength) return;

      let offset = HEADER_BYTES;
      const contentType = decodeContentType(this.#buffer.view(offset, contentTypeLength));
      offset += contentTypeLength;
      validateContentType(kind, contentType);

      const metadataText = decodeUtf8(this.#buffer.view(offset, metadataLength));
      offset += metadataLength;
      const metadata = kind === TRANSACTION ? metadataText : decodeMetadata(kind, metadataText);
      const event = this.#decodeEvent(kind, metadata, offset, payloadLength);

      this.#buffer.discard(frameLength);
      events.push(event);
      if (kind === PATCH || kind === TRANSACTION) this.#patchCount += 1;
      if (kind === COMPLETE || kind === ERROR) this.#terminalRead = true;
    }

    if (this.#terminalRead && this.#buffer.length > 0) {
      fail("WOGE_BYTES_AFTER_TERMINAL", "Bytes follow the terminal frame");
    }
  }

  #readPreamble() {
    if (this.#preambleRead || this.#buffer.length < PREAMBLE_BYTES) return;
    const preamble = this.#buffer.view(0, PREAMBLE_BYTES);
    for (let index = 0; index < PREAMBLE_MAGIC.byteLength; index += 1) {
      if (preamble[index] !== PREAMBLE_MAGIC[index]) {
        fail("WOGE_INVALID_PREAMBLE", "Invalid preamble magic");
      }
    }
    if (preamble[PREAMBLE_MAGIC.byteLength] !== WOGE_PATCH_PROTOCOL_VERSION) {
      fail("WOGE_UNSUPPORTED_VERSION", "Unsupported stream version");
    }
    this.#buffer.discard(PREAMBLE_BYTES);
    this.#preambleRead = true;
  }

  #decodeEvent(kind, metadata, payloadOffset, payloadLength) {
    if (kind === TRANSACTION) {
      return { type: "patch", patch: { operation: "transaction", metadata,
        html: this.#buffer.view(payloadOffset, payloadLength).slice() } };
    }
    if (kind === PATCH) {
      if (metadata.operation === "remove" && payloadLength !== 0) {
        fail("WOGE_INVALID_LENGTH", "Remove patch payload must be empty");
      }
      const html = decodeUtf8(this.#buffer.view(payloadOffset, payloadLength));
      return Object.freeze({ type: "patch", patch: Object.freeze({ ...metadata, html }) });
    }
    if (kind === COMPLETE) {
      if (metadata.patchCount !== this.#patchCount) {
        fail("WOGE_INVALID_SEQUENCE", "Completion patch count mismatch");
      }
      return Object.freeze({ type: "complete", patchCount: metadata.patchCount });
    }
    return Object.freeze({ type: "error", failure: Object.freeze(metadata) });
  }

  #guard(action) {
    if (this.#failure) throw this.#failure;
    try {
      return action();
    } catch (problem) {
      if (problem instanceof WogePatchError) this.#failure = problem;
      throw problem;
    }
  }
}

class BoundedByteBuffer {
  #bytes = new Uint8Array(1024);
  #start = 0;
  #end = 0;

  constructor(limit) {
    this.limit = limit;
  }

  get length() {
    return this.#end - this.#start;
  }

  append(chunk) {
    if (this.length + chunk.byteLength > this.limit) {
      fail("WOGE_INVALID_LENGTH", "Frame exceeds buffer limit");
    }
    this.#ensureCapacity(chunk.byteLength);
    this.#bytes.set(chunk, this.#end);
    this.#end += chunk.byteLength;
  }

  view(offset, length) {
    if (offset < 0 || length < 0 || offset + length > this.length) {
      fail("WOGE_INVALID_LENGTH", "Invalid frame byte range");
    }
    return this.#bytes.subarray(this.#start + offset, this.#start + offset + length);
  }

  discard(length) {
    if (length < 0 || length > this.length) {
      fail("WOGE_INVALID_LENGTH", "Invalid frame discard range");
    }
    this.#start += length;
    if (this.#start === this.#end) {
      this.#start = 0;
      this.#end = 0;
    }
  }

  #ensureCapacity(additional) {
    if (this.#bytes.byteLength - this.#end >= additional) return;
    if (this.#start > 0 && this.length + additional <= this.#bytes.byteLength) {
      this.#bytes.copyWithin(0, this.#start, this.#end);
      this.#end = this.length;
      this.#start = 0;
      return;
    }

    const required = this.length + additional;
    let capacity = this.#bytes.byteLength;
    while (capacity < required) capacity = Math.min(this.limit, capacity * 2);
    const grown = new Uint8Array(capacity);
    grown.set(this.#bytes.subarray(this.#start, this.#end));
    this.#end = this.length;
    this.#start = 0;
    this.#bytes = grown;
  }
}

function decodeMetadata(kind, value) {
  if (kind === PATCH) return decodePatchMetadata(value);
  if (kind === COMPLETE) return decodeCompletionMetadata(value);
  return decodeErrorMetadata(value);
}

function decodePatchMetadata(value) {
  const match = patchMetadataPattern.exec(value);
  if (!match) fail("WOGE_INVALID_METADATA", "Invalid/non-canonical patch metadata");
  const version = Number(match[1]);
  if (version !== WOGE_PATCH_PROTOCOL_VERSION) {
    fail("WOGE_UNSUPPORTED_VERSION", "Unsupported patch version");
  }
  const operation = match[2];
  if ((operation === "replace" && match[9] !== undefined) ||
      (operation === "append" && (match[9] === undefined || match[10] !== undefined)) ||
      (operation === "remove" && (match[9] === undefined || match[10] === undefined)) ||
      !["replace", "append", "remove"].includes(operation)) {
    fail("WOGE_INVALID_METADATA", "Unsupported operation/item metadata");
  }

  const interactionSequence = parseLong(match[6]);
  const baseRevision = parseLong(match[7]);
  const nextRevision = parseLong(match[8]);
  if (baseRevision === MAX_SIGNED_LONG || nextRevision !== baseRevision + 1n) {
    fail("WOGE_INVALID_METADATA", "Non-contiguous revision step");
  }
  return {
    protocolVersion: version,
    operation,
    patchId: match[3],
    epoch: match[4],
    target: match[5],
    interactionSequence,
    baseRevision,
    nextRevision,
    ...(match[9] === undefined ? {} : { itemId: match[9] }),
    ...(match[10] === undefined ? {} : { focusTarget: match[10] }),
  };
}

function decodeCompletionMetadata(value) {
  const match = completionMetadataPattern.exec(value);
  if (!match) fail("WOGE_INVALID_METADATA", "Invalid/non-canonical completion metadata");
  const patchCount = Number(match[1]);
  if (!Number.isSafeInteger(patchCount) || patchCount > MAX_PATCH_COUNT) {
    fail("WOGE_INVALID_METADATA", "Completion count exceeds v1 limit");
  }
  return { patchCount };
}

function decodeErrorMetadata(value) {
  const match = errorMetadataPattern.exec(value);
  if (!match) fail("WOGE_INVALID_METADATA", "Invalid/non-canonical remote failure metadata");
  return { code: match[1], correlationId: match[2], recovery: match[3] };
}

function parseLong(value) {
  const parsed = BigInt(value);
  if (parsed > MAX_SIGNED_LONG) fail("WOGE_INVALID_METADATA", "Number exceeds v1 limit");
  return parsed;
}

function validateKind(kind) {
  if (kind !== PATCH && kind !== COMPLETE && kind !== ERROR && kind !== TRANSACTION) {
    fail("WOGE_UNKNOWN_FRAME_KIND", "Unknown frame kind");
  }
}

function validateLengths(contentTypeLength, metadataLength, payloadLength) {
  if (contentTypeLength < 1 || contentTypeLength > MAX_CONTENT_TYPE_BYTES) {
    fail("WOGE_INVALID_CONTENT_TYPE", "Invalid content type length");
  }
  if (metadataLength > MAX_METADATA_BYTES) {
    fail("WOGE_METADATA_TOO_LARGE", "Metadata exceeds v1 limit");
  }
  if (payloadLength > MAX_PAYLOAD_BYTES) {
    fail("WOGE_PAYLOAD_TOO_LARGE", "Payload exceeds v1 limit");
  }
}

function validateContentType(kind, value) {
  const expected = kind === PATCH ? PATCH_CONTENT_TYPE : (kind === COMPLETE || kind === TRANSACTION) ? COMPLETE_CONTENT_TYPE : ERROR_CONTENT_TYPE;
  if (value !== expected) fail("WOGE_INVALID_CONTENT_TYPE", "Invalid content type");
}

function decodeContentType(bytes) {
  let value = "";
  for (const byte of bytes) {
    if (byte < 0x20 || byte > 0x7e) {
      fail("WOGE_INVALID_CONTENT_TYPE", "Content type must be printable ASCII");
    }
    value += String.fromCharCode(byte);
  }
  return value;
}

function decodeUtf8(bytes) {
  try {
    return decoder.decode(bytes);
  } catch {
    fail("WOGE_INVALID_UTF8", "Invalid frame UTF-8");
  }
}

export function fail(code, message) {
  throw new WogePatchError(code, message);
}

export function patchStreamLimits(options = {}) {
  if (!options || typeof options !== "object" || Array.isArray(options)) {
    fail("WOGE_INVALID_RESOURCE_LIMIT", "Patch limits must be an object");
  }
  const limits = {
    maxStreamBytes: options.maxStreamBytes ?? 16 * 1024 * 1024,
    maxPatches: options.maxPatches ?? 128,
    maxConcurrentStreams: options.maxConcurrentStreams ?? 8,
  };
  for (const [name, value] of Object.entries(limits)) {
    if (!Number.isSafeInteger(value) || value <= 0) {
      fail("WOGE_INVALID_RESOURCE_LIMIT", `${name} must be a positive safe integer`);
    }
  }
  if (limits.maxPatches > MAX_PATCH_COUNT) {
    fail("WOGE_INVALID_RESOURCE_LIMIT", "maxPatches exceeds protocol limit");
  }
  return Object.freeze(limits);
}

export function exceedBudget(limit, threshold) {
  const problem = new WogePatchError("WOGE_RESOURCE_LIMIT_EXCEEDED", `${limit} threshold=${threshold}`);
  problem.limit = limit;
  problem.threshold = threshold;
  throw problem;
}

export const PATCH_STREAM_MEDIA_TYPE = "application/vnd.woge.patch-stream; version=1";

export function isPatchStreamMediaType(value) {
  return typeof value === "string" &&
    /^application\/vnd\.woge\.patch-stream[ \t]*;[ \t]*version[ \t]*=[ \t]*(?:1|"1")$/i.test(value.trim());
}
