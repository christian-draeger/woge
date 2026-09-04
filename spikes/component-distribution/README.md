# Component distribution spike

This spike renders the same project board four ways: a binary headless primitive, a source-owned registry component, a packaged styled component, and a hybrid of the headless primitive plus application-owned source and CSS.

Every version produces normal server HTML. Filtering is a `GET` form, archiving is a `POST` form, the table keeps native relationships, and the rows have a stable patch region. No version needs JavaScript or hydration. Plain CSS and Tailwind change presentation only.

## Why the hybrid wins

The reusable semantic and accessibility behavior belongs in `woge-ui-headless`, where an update can fix every application. The visual recipe belongs in the application as Kotlin plus ordinary CSS or Tailwind classes. It can therefore be edited as freely as a shadcn component without copying the security-sensitive form and patch machinery.

The registry manifest records the exact source hash, license, provenance, Kotlin compatibility and Tailwind candidate root. Installation creates a lock file. `check` distinguishes clean, modified and missing files. `plan-update` labels each upstream change as safe to replace, local-only, or requiring a merge; it does not overwrite an ambiguous local edit.

## Try it

Run the complete build, compiler-failure fixture, registry tests, artifact checks and measurements:

```shell
./spikes/component-distribution/validate.sh
```

The registry prototype can also be inspected directly:

```shell
cd spikes/component-distribution
node registry.mjs verify registry/project-board/0.1.0/manifest.json
node registry.mjs install registry/project-board/0.1.0/manifest.json /tmp/woge-component-demo
node registry.mjs check /tmp/woge-component-demo project-board
node registry.mjs plan-update /tmp/woge-component-demo project-board registry/project-board/0.2.0/manifest.json
```

See [the evidence](evidence.md) and [ADR 0018](../../docs/adr/0018-hybrid-headless-and-source-owned-components.md) for the trade-off and compatibility boundaries.
