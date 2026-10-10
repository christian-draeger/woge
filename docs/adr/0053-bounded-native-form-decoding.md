# ADR 0053: Decode bounded native forms with Kotlin serializers

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#29](https://github.com/christian-draeger/woge/issues/29), [#122](https://github.com/christian-draeger/woge/issues/122)
- Refines: [ADR 0052](0052-typed-action-executors-and-registry.md)

## Context

HTML forms submit named text values, not JSON objects. Action commands need the same parsing rules
on Spring MVC, WebFlux and Ktor. Host parameter collectors can buffer the whole body or merge query
parameters with body fields, making limits and ambiguous submissions host-dependent.

## Decision

`FormDecoder(Command.serializer())` uses a generated `kotlinx.serialization` serializer. It lives in
the existing host SPI; no new module, reflection or second KSP command generator is needed.
Commands opt into Kotlin's serialization compiler plugin and `@Serializable`.

The decoder accepts only a flat class with scalar values, enums, scalar value classes and lists.
Serialized field names and defaults come from the serializer. It validates all submitted fields
before constructing a small in-memory JSON value for the serializer. This is an internal bridge,
not a JSON HTTP endpoint. Unsupported shapes fail when the decoder is configured; serializer bugs
and custom serializer exceptions propagate rather than being disguised as input errors.

Native forms use URL encoding and UTF-8. The incremental reader decodes percent escapes and `+`
without allocating an unbounded body. Invalid escapes, invalid UTF-8, empty/control-character field
names and empty field segments are rejected. Query parameters never supply command fields.

Scalar duplicates are rejected, even if identical. Repeated list fields preserve submission order.
Missing optional properties use declared defaults; missing required nullable fields become null
and missing required lists become empty. Other missing values produce field errors. Empty nullable
scalar values become null; non-null strings keep empty text. Booleans accept exactly `true` and
`false`. Numbers use locale-independent decimal parsing and must fit their type and be finite.
Enums use lowercase serialized names with underscores changed to dashes.

Unknown fields are rejected by default. Applications can explicitly choose `IGNORE`, for example
when a host security filter owns a CSRF field. Ignored fields still consume the full request budget.
This option does not establish authentication or CSRF verification.

Each request gets its own reader and limits: 64 KiB encoded body, 128 field occurrences, 256 decoded
UTF-8 name bytes and 16 KiB decoded value bytes. A shared immutable decoder provides explicit
override points. Limits reject the first excess input; thresholds are inclusive. Adapter reads use
fixed-size chunks and stop or cancel immediately upon rejection. Host-owned transport buffering and
ingress limits remain additional protection, not a substitute for Woge's reader.

`FormResult` distinguishes a typed command from safe structured field, encoding or budget errors.
It never includes submitted values. Automatic input bindings map malformed fields/encoding to 400,
budget exhaustion to 413 and unsupported content types/charsets to 415. They do not execute the
action on rejection. Application-owned handlers can inspect results to render field errors; the
full native validation and POST/Redirect/GET flow remains #30.

## Alternatives considered

- **Host form collectors:** rejected; buffering and query merging differ between hosts.
- **Generate another command constructor with KSP:** rejected; duplicates the serializer's field
  names, defaults and type model.
- **Pass form text straight into JSON decoding:** rejected; HTML fields are strings, duplicate
  scalars are ambiguous, and JSON coercion is not the public form policy.
- **Use multipart here too:** deferred to #60; file bytes require a separate bounded stream path.

## Consequences

### Positive

- Native forms work without Node, Vite or JavaScript.
- One tested parsing and budget policy applies to all three hosts.
- Errors identify fields or limits without logging submitted values.
- Domain authorization and validation remain ordinary application code.

### Negative

- Commands used with this decoder need the Kotlin serialization plugin.
- Supported commands are intentionally flat; nested objects and maps require another explicit API.
- Custom serializers must follow their descriptors; exceptions in their implementation remain errors.
- The small bounded JSON bridge is an extra allocation after parsing, not an unbounded body buffer.
- Security integrations must leave the body readable or provide bounded replay. Servlet filters
  that consume it through `getParameter` need explicit integration in #30; no silent parameter-bag
  fallback is provided.

## Follow-up

Implement native validation rendering and POST/Redirect/GET in #30, multipart in #60 and the wider
application/session resource policy in #122. This decision defines only request-owned form budgets.
