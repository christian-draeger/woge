# Application pilots

Use PetClinic to evaluate gradual adoption in a secured Spring MVC application. Use the invoice
desk to evaluate a complete Ktor workflow with localization, attachments and shared edits. Each
pilot uses synthetic data and includes commands to reproduce its automated checks.

| Pilot | Start here | What it exercises |
| --- | --- | --- |
| Spring PetClinic | [Build, sign in and book a visit](../../examples/petclinic-woge/README.md) | Existing login and MVC controller, typed routes, a search island with native fallback, in-place validation, SSE and patch refresh, PostgreSQL, session continuity and TLS ingress |
| Invoice desk | [Run the Ktor distribution and enter a localized invoice](../../examples/invoice-desk/README.md) | Typed nested fields and repeated values, Arabic RTL and localized decimals, exact amounts and DST validation, native and enhanced multipart, durable H2 storage and conflicting edits |

## Internal run on 2026-11-25

PetClinic used the clean upstream revision `500158f732419217507c7656904b8e6aa1bcc0d6`, Spring Boot
4.1.2 and locally staged Woge `0.1.0-SNAPSHOT` artifacts from
`e2e43c1a4839f79bcb459779e6b836c6a81594b7`. The invoice desk used this change's source build,
Ktor 3.5.2 and H2 2.4.240. Both ran on macOS arm64 with JDK 21.0.6, Gradle 8.14.4,
Node 24.13.1 and the repository's Playwright 1.62.1 Chromium. The cluster run used the pinned
PostgreSQL 18.6 and nginx 1.28.3 images in the PetClinic instructions.

### Spring PetClinic

- The Chromium journey signed in, found a real seeded PetClinic owner and booked a visit through
  the original MVC controller in each JavaScript mode. Reload showed one visit per submission.
  Enhanced validation focused the error summary and kept the description while the date was
  corrected. The fixture reached its 80th synthetic match through native paging and capped the
  suggestion list at eight. Keyboard, emulated touch, a stale response, strict CSP, and two
  unavailable-suggestion paths passed. [Browser replay](../../examples/petclinic-woge/browser-tests/owner-suggestions.mjs)
- Two JVMs shared PostgreSQL and JDBC sessions. The run verified a native write on one node and
  a Woge region refresh on the peer, a safe GET before retrying an uncertain follow-up write,
  a single commit for a duplicate submission and one winner at a shared revision. It also
  exercised session expiry, logout and grant revocation. [Cluster replay](../../examples/petclinic-woge/README.md#run-two-petclinic-instances-on-postgresql)
- The local samples were 81.1 ms from write to peer SSE, 166.2 ms from write to peer patch,
  1,145.7 ms from disconnecting the database listener to its recovery patch, and 45.4 ms from
  POST to peer SSE through TLS 1.3 and HTTP/2. The Chromium suggestion sample from changing the
  query to seeing the link was 28.3 ms. These were individual observations on one workstation.

### Ktor invoice desk

- Chromium exercised `de-DE`, `en-US` and `ar-EG` with native and enhanced submission. Each
  journey corrected invalid repeated values, saved a large exact decimal with a UTF-8 receipt,
  rejected a replacement receipt, edited the invoice and reloaded it. Arabic also exercised a
  narrow viewport. A visual check found that the left-to-right timestamp example broke across
  lines in the right-to-left label. The pilot now isolates the example in a left-to-right inline element.
  [Browser replay](../../examples/invoice-desk/README.md#replay-the-packaged-browser-pilot)
- The packaged process retained the local draft and file when an upload was cancelled before
  delivery. Two isolated editors opened one revision. The first saved and the second received
  HTTP 409 with its input still visible. Restarting the distribution on the same H2 database
  recovered all six invoices and their receipt metadata.
- The native HTTP suite on Ktor and WebFlux also checked exact stored receipt bytes and reopened
  data on the other host. It covered a stale locale and Berlin daylight-saving gaps and overlaps.
  [HTTP checks](../../examples/invoice-desk/src/test/kotlin/dev/woge/examples/invoice/)

## Findings for Woge

| Observation | Implication for an application team | Next question to test |
| --- | --- | --- |
| PetClinic reused the existing controller, database, session and CSRF flow in both browser modes. The island failed independently of native search. | Adopt one HTML workflow at a time. The application still wires the MVC response negotiation and owns domain rules. | Try a second existing MVC application, compare changes with its ordinary Thymeleaf implementation, and measure onboarding and rollback effort. |
| The cluster recovered current state through SSE invalidation and an authorized fresh read. The follow-up write needed a database receipt and revision check. | Region freshness and write consistency are separate. Keep durable idempotency and authorization in the application and its store. | Exercise a rolling upgrade and reconnect with a sustained multi-client workload. Document the write receipt pattern for other hosts. |
| Typed invoice fields survived invalid and repeated values across both hosts. The application provided number/date formats, locale policy and translations. | Reuse decoding and validation structure across hosts. Keep locale and domain choices with the application. | Try variable-length line items with add/remove and reordering. Check validation and focus by stable row key with a screen reader. |
| Enhanced multipart preserved the file and draft after HTTP 400, while detailed field errors appeared on a deliberate native submit. That navigation cleared the file selection. | Enhanced upload adds feedback and cancellation. It takes a second submission and file selection to recover from a validation error in this design. The sample asks the user to check saved state before retrying an unconfirmed write. | Show structured field errors in place on a confirmed rejection, preserve the selected file, and keep an explicit recovery path for an uncertain commit without automatically replaying a POST. |

These are maintainer-run, source-version checks. The [beta exit issue](https://github.com/christian-draeger/woge/issues/50)
tracks independent users of the pinned candidate and the release decision. The
[manual verification issue](https://github.com/christian-draeger/woge/issues/253) tracks work with
physical devices and assistive technology. The
[comparative evidence register](../performance/claim-evidence.md) tracks the separate baseline studies.
