# HTML writer strategy spike

This spike compares `kotlinx.html` 0.12.0 with a small Woge-owned streaming writer. Both render the same utility-heavy custom-element task card and write incrementally without an in-memory DOM.

Run:

```shell
./spikes/html-writer-strategy/validate.sh
```

The validator runs seven behavior tests and two deliberately invalid compile fixtures. See [evidence.md](evidence.md) and [ADR 0012](../../docs/adr/0012-html-writer-and-kotlinx-interop.md).
