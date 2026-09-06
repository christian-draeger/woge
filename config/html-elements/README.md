# Pinned HTML element data

Woge generates its common HTML tag wrappers from
[`@webref/elements` 2.8.0](https://www.npmjs.com/package/@webref/elements/v/2.8.0), the curated
machine-readable Webref package maintained by W3C. The checked-in TSV is a deliberately small,
reviewable derivative of `package/html.json`; builds never download standards data.

## Provenance pin

| Field | Pinned value |
| --- | --- |
| Package | `@webref/elements` |
| Version | `2.8.0` |
| Repository | `https://github.com/w3c/webref` |
| Upstream commit | `48cc619e954aeae12ac70b6f34fe56056a297d92` |
| Tarball | `https://registry.npmjs.org/@webref/elements/-/elements-2.8.0.tgz` |
| Tarball SHA-1 | `9f810c3643e12c89149f45eb062b1aeaaffe4653` |
| Tarball SHA-512 integrity | `sha512-EWxcb3d2mkRU0zCfIX8no6xoywnUX3+Pxqhtt4xKwcb+vG4tQUlMZMd3THLH8M1Peg/5ssVRvlMEl/N5sHiFXw==` |
| Derived TSV SHA-256 | `96c88d54c0b1dc1801f5ae536cf63cc4a541c33347f87834c76d82354fa33e01` |
| License | MIT; see [`LICENSE.webref`](LICENSE.webref) |

The derivation keeps the 113 conforming elements in Webref's `html.json` and excludes entries linked
to WHATWG's obsolete-elements section. `name`, DOM `interface`, and specification URL are copied.
Serialization kind is assigned from the
[WHATWG HTML syntax categories](https://html.spec.whatwg.org/multipage/syntax.html#elements-2):

- the 13 standard void elements are `VOID`;
- `title` and `textarea` are `ESCAPABLE_RAW_TEXT`;
- `script` and `style` are `RAW_TEXT`;
- `iframe` is also kept behind Woge's empty-body raw-text boundary because the HTML parser switches
  its tokenizer to the RAWTEXT state;
- every other conforming HTML element is `NORMAL`.

## Updating

1. Fetch an explicit Webref package version and verify its registry integrity.
2. Recreate the TSV in upstream order, review additions, removals and syntax categories, then update
   every pin above and the checksum in `modules/woge-core/build.gradle.kts`.
3. Run `./gradlew :woge-core:check :woge-core:checkKotlinAbi --no-build-cache`.
4. Review the generated public ABI and document any compatibility decision in an ADR.

`verifyHtmlElementDataset` fails the normal `check` lifecycle on unreviewed TSV drift. The generator is
cacheable and writes only under `build/generated`, so clean and incremental builds use the same input
without committing derived Kotlin source.
