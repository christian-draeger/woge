# Patch framing spike

This spike compares `multipart/mixed` with an explicit length-prefixed Woge stream. Both codecs carry the same Patch IR metadata and arbitrary UTF-8 HTML without treating network chunks as frame boundaries.

Run:

```shell
./spikes/patch-framing/validate.sh
```

The validator tests every possible two-chunk split, one-byte reads, gzip transport, terminal completion/error frames and malformed input, then prints reproducible size measurements. See [evidence.md](evidence.md) and [ADR 0013](../../docs/adr/0013-length-prefixed-patch-framing.md).
