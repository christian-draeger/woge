export interface IslandContext {
  readonly signal: AbortSignal;
  readonly propsVersion: number;
}
export type IslandProps = Readonly<Record<string, string>>;
export interface IslandController {
  update?(props: IslandProps): void;
  dispose?(): void;
}
export type IslandMount = (root: HTMLElement, props: IslandProps, context: IslandContext) => IslandController | void;
export interface IslandEntry { readonly url: string; readonly mount: string; readonly propsVersion: number; }
export declare function parseIslandManifest(text: string): Map<string, IslandEntry>;
export declare function parseIslandProps(text: string, version: number): IslandProps;
export declare function installWogeIslands(options?: {
  document?: Document;
  importModule?: (url: string) => Promise<Record<string, unknown>>;
}): Readonly<{ scan(scope?: Document | Element): void; update(root: HTMLElement, props: IslandProps): void; dispose(): void }>;
