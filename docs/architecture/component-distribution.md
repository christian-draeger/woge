# Component distribution

Woge components produce normal HTML. A button is still a `<button>`, filtering is still a `GET` form, and application CSS is still CSS. The primary distribution model combines reusable headless behavior with application-owned visual source.

This page records the M0 contract. The production modules and installer are not released yet.

## The two layers

`woge-ui-headless` will provide typed Kotlin primitives for semantics, accessibility, output-context escaping, actions and patch identity. These are versioned binary APIs. A primitive may render useful unstyled HTML by itself.

The Woge component registry will provide optional Kotlin recipes and plain CSS or Tailwind class candidates as source. Installing a recipe copies those files into the application. From that point, the application owns them and can use its normal IDE, formatter, tests and code review.

```text
application recipe + application CSS/Tailwind
                    ↓ composes
            woge-ui-headless
                    ↓
      semantic HTML, forms and patch regions
```

This differs from a source-only catalog: security-sensitive form and region behavior can still be fixed in the binary primitive. It also differs from a MUI-style library: deep visual customization does not require overriding an opaque component or adopting a Woge styling language.

## Install and update contract

Every registry release is immutable and has a machine-readable manifest containing:

- component name and version;
- SPDX license and repository/revision/path provenance;
- supported Kotlin and headless-primitive range;
- whether hydration is required (the default catalog says `false`);
- each copied file's target, kind and SHA-256;
- Kotlin files Tailwind must scan for complete class candidates.

An install writes a lock file beside the application source. A later check compares three facts: the installed upstream hash, the current application file and the proposed upstream hash. That makes `unchanged`, `preserve-local`, `replace-safe` and `merge-required` visible. Tooling must never overwrite a modified file silently. Updates are reviewable source diffs, not hidden runtime replacement.

## Compatibility promises

- Headless public Kotlin APIs follow Woge semantic versioning and binary API checks.
- Documented semantic HTML, accessibility behavior, form method/action and patch identity are tested contracts. Styling classes are not identity.
- Registry release directories and source hashes are immutable. Copied source is application code and has no binary-compatibility promise from Woge after customization.
- The manifest schema is versioned independently. Unsupported schema versions fail clearly.
- Plain CSS and Tailwind are presentation alternatives. Switching changes assets/classes only, not actions or patch protocol.
- A packaged starter theme may be offered later, but its CSS selectors, custom properties, layers and variants must state their own compatibility policy. It cannot be the only component path.

## Module boundary

The M1 module manifest should add `woge-ui-headless` as a public module depending only on `woge-core`. Registry recipes are catalog source, not a runtime dependency. Installer/update support belongs in build tooling and may depend on the manifest format, but core, host SPI, Spring/Ktor adapters and the browser patch runtime do not depend on it.

Spring MVC, Spring WebFlux and Ktor render the same component API because components sit above the framework-neutral host boundary. Spring Boot remains the primary getting-started integration without becoming part of component semantics.

## Authoring rules

- Recipes keep real elements, attributes, URLs, forms and HTTP methods visible.
- Plain CSS uses normal `.css` files or the CSS literal contexts from the CSS authoring contract.
- Tailwind uses complete static class tokens and declares each registry candidate file explicitly.
- Initial SSR and later row/region renders share instance-qualified IDs and region names.
- Core workflows work without JavaScript. Optional controllers or islands use the separate frontend-extension contract.
- Generated examples and manifests favor enums, named arguments and explicit files so compiler diagnostics help humans and coding models in the same way.

The executable [component distribution spike](../../spikes/component-distribution/evidence.md) compares all four models. [ADR 0018](../adr/0018-hybrid-headless-and-source-owned-components.md) owns the decision.
