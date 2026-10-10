export const BEFORE_REPLACE_EVENT: "woge:before-replace";
export const AFTER_REPLACE_EVENT: "woge:after-replace";
export const BEFORE_APPEND_EVENT: "woge:before-append";
export const AFTER_APPEND_EVENT: "woge:after-append";
export const BEFORE_REMOVE_EVENT: "woge:before-remove";
export const AFTER_REMOVE_EVENT: "woge:after-remove";
export const WOGE_PATCH_PROTOCOL_VERSION: 1;
export const ACTION_ERROR_EVENT: "woge:action-error";

declare const browserStateBrand: unique symbol;
export interface WogeBrowserState {
  readonly [browserStateBrand]: true;
}

/** In-memory only: may contain sensitive values. Never serialize, persist or log. */
export function captureWogeBrowserState(root: Document | Element): WogeBrowserState;
export function prepareWogeBrowserState(
  snapshot: WogeBrowserState,
  destination: DocumentFragment | Element,
  options?: { readonly reset?: boolean },
): {
  commit(): void;
  restoreFocus(fallback: Element): void;
};

export interface WogeActionForms {
  /** Removes the submit listener, cancels owned requests and restores busy state. Never replays a POST. */
  dispose(): void;
}

/** Enhances explicitly marked same-origin URL-encoded POST forms; other forms remain native. */
export function installWogeActionForms(root: Document, runtime: WogePatchRuntime): WogeActionForms;

export interface ReplaceLifecycleDetail {
  readonly operation: "replace";
  readonly patchId: string;
  readonly target: string;
  readonly interactionSequence: string;
  readonly baseRevision: string;
  readonly nextRevision: string;
}

export interface AppendLifecycleDetail extends Omit<ReplaceLifecycleDetail, "operation"> {
  readonly operation: "append";
  readonly itemId: string;
}

export interface RemoveLifecycleDetail extends Omit<ReplaceLifecycleDetail, "operation"> {
  readonly operation: "remove";
  readonly itemId: string;
  readonly focusTarget: string;
}

export interface PatchCompletion {
  readonly patchCount: number;
  /** Present when valid wire frames were ignored as superseded work; they are included in patchCount. */
  readonly stalePatchCount?: number;
}

export interface ApplyPatchStreamOptions {
  readonly signal?: AbortSignal;
}

export interface WogeObservationContext {
  readonly pageEpoch: string;
  readonly target: string;
  readonly patchId: string;
}

/** Browser ordering context only; never grants authorization or duplicate-mutation permission. */
export interface WogeInteraction {
  readonly pageEpoch: string;
  readonly interactionSequence: string;
  readonly targets: readonly { readonly target: string; readonly baseRevision: string }[];
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
      readonly code?: "WOGE_STALE_PATCH";
    };

export interface WogePatchRuntimeOptions {
  readonly observer?: (event: WogeObservationEvent) => void;
}

export interface WogePatchRuntime {
  /** Registers latest intent before starting a request; pass the returned context through its typed input. */
  beginInteraction(targets: readonly string[]): WogeInteraction;
  /** One safe authoritative replacement per target revision; never use this loader for mutations. */
  refetchRegion(
    target: string,
    load: (context: WogeInteraction, options: ApplyPatchStreamOptions) => Promise<ReadableStream<Uint8Array>>,
    options?: ApplyPatchStreamOptions,
  ): Promise<PatchCompletion>;
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

/** Failure groups from the canonical failure and recovery model (ADR 0047). */
export type WogeFailureCategory =
  | "request-decoding"
  | "security"
  | "domain-conflict"
  | "rendering"
  | "transport"
  | "protocol"
  | "stale"
  | "incompatible-client"
  | "browser-apply"
  | "resource-exhaustion"
  | "cancelled"
  | "unknown";

/** The single bounded reaction for one failure. */
export type WogeRecoveryOutcome =
  | "fail-closed"
  | "error-response"
  | "ignore-stale"
  | "refetch-region"
  | "reload-page"
  | "retry-safe";

export interface WogeFailureClassification {
  readonly code: string;
  readonly category: WogeFailureCategory;
  readonly outcome: WogeRecoveryOutcome;
}

export interface ClassifyWogeFailureOptions {
  /** True only for idempotent requests without side effects, such as a deferred-region GET. */
  readonly safeRequest?: boolean;
}

export function classifyWogeFailure(
  problem: unknown,
  options?: ClassifyWogeFailureOptions,
): WogeFailureClassification;

export interface WogeRecoveryBudget {
  /** One reload for the current page URL and tab, even when navigation creates a fresh epoch. */
  tryReload(pageEpoch: string): boolean;
  /** True at most once per request key. */
  tryRetry(requestKey: string): boolean;
}

export function createWogeRecoveryBudget(options?: {
  readonly storage?: Pick<Storage, "getItem" | "setItem">;
  /** Defaults to the browser's full page URL; supply a stable key outside a browser. */
  readonly pageUrl?: string;
}): WogeRecoveryBudget;

export function createWogePatchRuntime(root?: Document, options?: WogePatchRuntimeOptions): WogePatchRuntime;

declare global {
  interface DocumentEventMap {
    "woge:action-error": CustomEvent<{ readonly code: string }>;
    "woge:before-replace": CustomEvent<ReplaceLifecycleDetail>;
    "woge:after-replace": CustomEvent<ReplaceLifecycleDetail>;
    "woge:before-append": CustomEvent<AppendLifecycleDetail>;
    "woge:after-append": CustomEvent<AppendLifecycleDetail>;
    "woge:before-remove": CustomEvent<RemoveLifecycleDetail>;
    "woge:after-remove": CustomEvent<RemoveLifecycleDetail>;
  }
}
