# Architecture decision records

ADRs preserve why Woge made a consequential choice, which alternatives were considered and what the choice costs. They complement tutorials and API reference; they do not replace them.

## Lifecycle

1. Copy [`0000-template.md`](0000-template.md) to the next free four-digit number and a short lowercase slug.
2. Start with `Proposed`. Link the issue or pull request that supplies evidence.
3. Record concrete alternatives and consequences before changing the status.
4. Use one of: `Proposed`, `Accepted`, `Rejected`, `Deprecated`, or `Superseded`.
5. Never rewrite an accepted decision to hide history. Add a new ADR and mark the old one `Superseded`, with links in both directions.
6. Add every ADR to the index below.

An ADR is required for changes to public APIs, protocols, module boundaries, security defaults, supported platforms or compatibility promises. A narrow implementation detail with no effect on those contracts is exempt.

## Validation

Run:

```shell
./scripts/validate-adrs.sh
```

The check validates filenames, required sections, metadata, duplicate numbers, index entries and local Markdown links.

## Index

| ADR | Status | Decision |
| --- | --- | --- |
| [0001](0001-web-native-product-boundary.md) | Accepted | Keep Woge web-native and progressively enhanced |
| [0002](0002-framework-neutral-host-boundary.md) | Accepted | Put server frameworks behind Woge host adapters |
| [0003](0003-web-first-documentation-and-ai-dx.md) | Accepted | Use web-first documentation and compiler-guided AI DX |
| [0004](0004-project-operations-reference-application.md) | Accepted | Use one project operations dashboard as the reference application |
| [0005](0005-server-host-use-case-ports.md) | Accepted | Model server adapters as Woge use-case exchanges |
| [0006](0006-initial-module-boundaries.md) | Accepted | Enforce a small inward-pointing initial module graph |
| [0007](0007-browser-support-and-progressive-enhancement.md) | Accepted | Guarantee an HTML baseline before browser enhancement |
