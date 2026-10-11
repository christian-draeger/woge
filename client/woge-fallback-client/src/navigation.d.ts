/** Installs isolated document navigation; actions, live and deferred documents remain native. */
export function installWogeNavigation(root?: Document): {
  /** Cancels owned GETs, removes listeners and restores history.scrollRestoration. */
  dispose(): void;
};
