# Woge Ktor adapter

`woge-ktor` connects framework-neutral Woge page use cases to ordinary Ktor routes. Applications
keep path and query decoding in `routing { ... }`; the adapter owns HTTP metadata, HTML streaming,
patch framing and request cancellation.

Start with the [Ktor adapter guide](../../docs/guides/ktor-adapter.md) and the executable
[`woge-reference-ktor`](../../examples/reference-application/ktor) application. Spring Boot remains
Woge's primary getting-started host; this module is the maintained portability proof.
