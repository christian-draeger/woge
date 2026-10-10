# Vite frontend spike

Time-boxed evidence for [#142](https://github.com/christian-draeger/woge/issues/142): should Woge integrate
Vite through the OpenSavvy Gradle plugins, through direct Vite invocation, or not at all?

The decision is [ADR 0070](../../docs/adr/0070-optional-direct-vite-frontend-adapter.md). The
measurements are in [evidence.md](evidence.md).

## Layout

- `src/plain/`: the Node-free variant. Plain CSS and browser-native JavaScript modules.
- `src/frontend/`: the Vite variant of the same page. TypeScript entry, lazy chunk, plain CSS and
  Tailwind input.
- `src/kotlin/`: a Kotlin file with Tailwind classes, scanned as text.
- `gradle-probe/`: a Gradle build with the Node-free copy task and the `dev.opensavvy.vite.base`
  `ViteExec` task.

## Run

```bash
npm ci --ignore-scripts
./validate.sh
```

The spike has its own build and is not part of the root Gradle graph. Production code must not
depend on it.
