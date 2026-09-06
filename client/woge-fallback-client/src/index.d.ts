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

export interface WogeObservationContext {
  readonly pageEpoch: string;
  readonly target: string;
  readonly patchId: string;
}

export type WogeObservationEvent =
  | {
      readonly phase: "started";
      readonly observationId: number;
      readonly operation: "patch.apply";
      readonly context: WogeObservationContext;
    }
  | {
      readonly phase: "finished";
      readonly observationId: number;
      readonly operation: "patch.apply";
      readonly outcome: "succeeded" | "failed" | "cancelled" | "rejected" | "stale";
      readonly durationMs: number;
      readonly context: WogeObservationContext;
    };

export interface WogePatchRuntimeOptions {
  readonly observer?: (event: WogeObservationEvent) => void;
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

export function createWogePatchRuntime(root?: Document, options?: WogePatchRuntimeOptions): WogePatchRuntime;

declare global {
  interface DocumentEventMap {
    "woge:before-replace": CustomEvent<ReplaceLifecycleDetail>;
    "woge:after-replace": CustomEvent<ReplaceLifecycleDetail>;
  }
}
