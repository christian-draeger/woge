export const BEFORE_REPLACE_EVENT: "woge:before-replace";
export const AFTER_REPLACE_EVENT: "woge:after-replace";

export interface ReplaceLifecycleDetail {
  readonly operation: "replace";
  readonly patchId: string;
  readonly target: string;
  readonly interactionSequence: string;
  readonly baseRevision: string;
  readonly nextRevision: string;
}

export interface PatchCompletion {
  readonly patchCount: number;
}

export interface ApplyPatchStreamOptions {
  readonly signal?: AbortSignal;
}

export interface WogePatchRuntime {
  applyPatchStream(
    stream: ReadableStream<Uint8Array>,
    options?: ApplyPatchStreamOptions,
  ): Promise<PatchCompletion>;
}

export class WogePatchError extends Error {
  constructor(code: string, message: string);
  readonly code: string;
}

export class WogeRemotePatchError extends WogePatchError {
  constructor(failure: {
    readonly code: string;
    readonly correlationId: string;
    readonly recovery: "none" | "reload";
  });
  readonly correlationId: string;
  readonly recovery: "none" | "reload";
}

export function createWogePatchRuntime(root?: Document): WogePatchRuntime;

declare global {
  interface DocumentEventMap {
    "woge:before-replace": CustomEvent<ReplaceLifecycleDetail>;
    "woge:after-replace": CustomEvent<ReplaceLifecycleDetail>;
  }
}
