# XSS regression corpus

`payloads.tsv` is the versioned, shared regression corpus for Woge's Kotlin HTML/patch tests and
fallback-client browser tests. Each non-comment line has a category, a tab, and one inert test value.
The tests use values as text, quoted attributes, CSS declarations, URLs, or patch fragments; they do
not execute the samples.

Add a reviewed regression sample here when a new parser/browser bypass is found. Keep the corpus
small, deterministic, and self-contained. Do not include network callbacks or real user data. Raise
`v1` only when changing the fixture format, not when adding payload rows.
