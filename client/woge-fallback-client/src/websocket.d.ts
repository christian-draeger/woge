import type { WogePatchRuntime } from "./index.js";
export interface WogeSocketSubscription {
  id: string;
  epoch: string;
  /** Read current DOM data-woge-revision values. Called only after pending patches finish. */
  revisions(): Record<string, string>;
}
export declare function connectWogeWebSocket(url: string, options: {
  runtime: WogePatchRuntime;
  /** Fresh session-bound CSRF proof on every reconnect. Never put it in a URL. */
  proof(): string;
  subscriptions?: WogeSocketSubscription[];
  onError?(error: Error): void;
  onClose?(event: { code: number; reason: string }): void;
  maxQueuedFrames?: number;
  maxFrameBytes?: number;
  maxReconnects?: number;
  idleTimeout?: number;
  signal?: AbortSignal;
}): {
  subscribe(subscription: WogeSocketSubscription): void;
  unsubscribe(id: string): boolean;
  /** Ephemeral data only; false means it was not queued. Never retries application messages. */
  send(id: string, payload: string): boolean;
  close(): void;
};
