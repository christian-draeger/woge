/** Values and optimistic transition inputs must be treated as immutable. */
export interface ReadSignal<T> { get(): T }
export interface Signal<T> extends ReadSignal<T> { set(value: T): void }
export interface Computed<T> extends ReadSignal<T> { dispose(): void }
export function signal<T>(initial: T): Signal<T>;
/** Lazy cached computation; dispose after its consuming effects to release dependencies. */
export function computed<T>(fn: () => T): Computed<T>;
/** Runs immediately and after invalidation. Cleanup runs before rerun and on disposal. */
export function effect(fn: () => void | (() => void), options?: { signal?: AbortSignal }): () => void;
/** Synchronous only; reads inside the batch see writes immediately; effects flush at its end. */
export function batch<T>(fn: () => T): T;
export interface OptimisticMutation<T> {
  readonly id: number;
  readonly base: T;
  /** Only after a non-stale authoritative patch has applied; newer success supersedes older intent. */
  resolve(authoritative: T): boolean;
  reject(code: string): boolean;
  /** Drop superseded/cancelled work without reporting a user failure. Never replay a POST. */
  stale(): boolean;
}
export interface OptimisticAction<T> {
  readonly pendingCount: ReadSignal<number>;
  readonly failure: ReadSignal<Readonly<{ id: number; code: string }> | null>;
  submit(apply: (base: T) => T): OptimisticMutation<T>;
}
/** Owns writes to state for one latest-intent scope. Not a general concurrent persistence log. */
export function createOptimisticAction<T>(state: Signal<T>): OptimisticAction<T>;
