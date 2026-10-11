# Version 1 wire fixtures

These files are shared by JVM protocol tests and the fallback browser-client tests. Keep them small
and review byte-level changes deliberately.

Adding an optional field, event name or request field is additive only when old readers safely ignore
it and old writers remain accepted. Removing or reinterpreting a field, changing framing, encoding,
required fields or existing event meaning is breaking and needs a new protocol version. Never update
a fixture just to make a test pass: document the compatibility decision in an ADR first.

`patch-stream-v1.hex` and `collection-patch-stream-v1.hex` are complete framed responses;
`action-form-v1.txt` is the URL-encoded request body; `live-events-v1.txt` is an SSE event block.
