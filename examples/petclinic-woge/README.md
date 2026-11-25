# Add Woge to Spring PetClinic

This Spring MVC reference adds Woge owner search, owner and pet details, and a native visit form
to the official [Spring PetClinic](https://github.com/spring-projects/spring-petclinic) source at
`500158f732419217507c7656904b8e6aa1bcc0d6`. It uses the existing Java entities, repository,
H2 seed data, Thymeleaf pages, and visit controller. The example builds those files from a separate
checkout and includes PetClinic's Apache-2.0 license in the application archive.

## Run the reference

Use JDK 21. The command resolves the Woge plugin and libraries anonymously from the Maven Central
snapshot repository. The snapshot follows Woge's current development build.

```bash
git clone https://github.com/spring-projects/spring-petclinic.git /tmp/petclinic-reference
git -C /tmp/petclinic-reference checkout --detach 500158f732419217507c7656904b8e6aa1bcc0d6
./gradlew -p examples/petclinic-woge bootRun \
  -PpetclinicSource=/tmp/petclinic-reference \
  -PwogeRepository=https://central.sonatype.com/repository/maven-snapshots/ \
  -PwogeVersion=0.1.0-SNAPSHOT
```

Open `http://127.0.0.1:8080/woge/owners`. Sign in as `user` using the temporary password
printed by Spring Security at startup. Search for `Franklin`, open a pet and book a future visit.

The Woge form posts to PetClinic's original MVC controller with or without JavaScript. Native
submissions use PetClinic's Thymeleaf validation page and redirect. With JavaScript enabled, the
Woge client requests a patch response. An invalid visit retains the form input and moves focus to
the error summary. A successful submission navigates to the existing owner page and shows
PetClinic's own booking confirmation. Both paths use the same Spring Security session and hidden
CSRF token, MVC date binding and visit rules.

`ClinicVisitEnhancement.kt` scopes the negotiation to the original visit POST. The interceptor runs
after Spring Security and asks for same-origin evidence on enhanced requests. It translates the
controller's validation model into a Woge replacement or its successful redirect into a same-origin
GET navigation. The Woge form uses the version-matched browser asset artifact and needs no Node.js
build. The shared controller owns the sole persistence write. Enhanced forms are limited to 4 KiB
of declared request content and 4 KiB of decoded UTF-8 field names and values.

To reproduce the browser check, book a visit with JavaScript disabled. With JavaScript enabled,
remove the date field's `min` attribute in Developer Tools and submit a past date to exercise
server validation. The error summary receives focus and the description remains in the form.
Correct the date and confirm the booked visit on the owner page.

To develop against local Woge changes, run `./gradlew publishScaffoldArtifacts -PwogeVersion=0.1.0-SNAPSHOT`
from the Woge repository and pass its absolute `build/scaffold-maven-repository` path as `wogeRepository`.
Use the same version for publication and consumption. Verify either build with the same properties
and the `test` task in place of `bootRun`.

## Try the secured owner suggestions

Enter `Fr` in the Woge search. After two characters, the island asks for up to eight matches and
renders ordinary links. Use Tab to reach the links, Escape to close the suggestions, or **Find
owners** to run the regular paged search. A search with JavaScript off can reach every page in the
fixture. See the [widget decision guide](../../docs/guides/data-display-and-complex-inputs.md#decide-how-much-autocomplete-to-add).

The server applies the existing Spring Security session and returns owner IDs and display names.
The detail page resolves the ID again. A new query cancels the old browser read, and an older
response cannot replace current results. The module loads on the Woge search page through the
existing Island loader, without a separate browser build or a second widget runtime.

Use the same Gradle arguments above with `test bootJar`. For the optional Chromium test, use JDK 21,
Node 24 and the browser client's pinned Playwright install from the Woge checkout.

```sh
npm --prefix client/woge-fallback-client ci --ignore-scripts
npm --prefix client/woge-fallback-client exec -- playwright install chromium
node examples/petclinic-woge/browser-tests/owner-suggestions.mjs
```

The browser test starts the JAR on loopback and adds 80 synthetic owner rows to a disposable H2
database. From login, it follows search to the owner, books through the original MVC controller
and verifies a single saved visit after reload. It covers native paging, keyboard and emulated
touch, strict CSP, a stale suggestion, in-place validation with focus and draft retention, and
fallback after a failed import or unavailable endpoint. Physical touch and
assistive-technology review remain [manual work](https://github.com/christian-draeger/woge/issues/253).

## Run two PetClinic instances on PostgreSQL

Build the optional cluster archive with JDK 21, using the clean pinned PetClinic checkout from
the first step. The cluster fixture uses Python 3 and a local Docker engine. PostgreSQL 18.6 is
pinned by digest. After the initial image pull, the runner uses that cached image.

```sh
./gradlew -p examples/petclinic-woge test bootJar \
  -PpetclinicCluster=true \
  -PpetclinicSource=/tmp/petclinic-reference \
  -PwogeRepository=https://central.sonatype.com/repository/maven-snapshots/ \
  -PwogeVersion=0.1.0-SNAPSHOT
docker pull postgres@sha256:d3e1620b530c944afa6e887d22eb899824da68e19c52024bf98f5220c88a65b2
python3 examples/petclinic-woge/cluster/run.py \
  examples/petclinic-woge/build/libs/woge-petclinic.jar /tmp/petclinic-reference
```

The runner applies the upstream PetClinic and Spring Session JDBC schemas once, then starts two
separate JVMs on dynamically assigned loopback ports. Both use the same PostgreSQL data, Spring
Security sessions and Woge render identity secret. The visit form still uses the original controller.
The live route at `/woge/cluster/owners/1` checks a PostgreSQL owner grant for the signed-in account
on each document, refresh and subscription request. The seeded account can use the existing
PetClinic pages under their original shared-access policy.

Each node holds one dedicated PostgreSQL listener. The visit trigger publishes an empty
invalidation on commit. The local flow keeps one pending signal per node and Woge sends region
handles over SSE. A refresh reads the authorized visit data from PostgreSQL. Client reconnect and
database listener recovery each request current state through the same route. The native HTML
page reads the same state with JavaScript disabled. References:
[PostgreSQL JDBC notifications](https://jdbc.postgresql.org/documentation/server-prepare/#listen--notify) and
[Spring Boot session integration](https://docs.spring.io/spring-boot/4.1/reference/web/spring-session.html).

The runner measures a write on node B reaching SSE on node A, a patch served by B for an A-issued
render target, and recovery of a database listener after its connection is terminated. It checks
external writes across client reconnect, missing CSRF, anonymous access, a revoked owner grant,
and rejection of captured cookies after logout on another node or JDBC session expiry. Timings
describe local single-client samples.

### Verify an uncertain write and a conflict

The cluster-only **Schedule an owner follow-up** form uses a single PostgreSQL slot for the seeded
owner. It is rendered as native HTML with Woge's HTML writer. The ordinary Spring MVC POST checks
Spring CSRF, the current owner grant and the database revision. Each submitted UUID is scoped to
the authenticated account and owner. PostgreSQL commits the receipt and the versioned date update
together. A matching duplicate redirects to the same GET. A reused UUID with different values or
a new command based on an old revision returns 409 with a link to the current form. Failed writes
roll back their receipts. The fixture retains successful receipts for the lifetime of its temporary
database. [PostgreSQL Read Committed behavior](https://www.postgresql.org/docs/18/transaction-iso.html#XACT-READ-COMMITTED)

The same runner submits concurrent native requests to both nodes, then closes a POST connection
without reading its response. It verifies the committed date on the peer with a safe GET before
explicitly resending the same command. The resend changes neither the date nor the database revision.
The runner also checks that a successful receipt cannot bypass a revoked owner grant. PetClinic's
original visit booking follows its upstream behavior. Load and lifetime limits, rolling upgrades,
rollback and production operations remain tracked in
[the multi-instance milestone](https://github.com/christian-draeger/woge/issues/289).

### Verify TLS and HTTP/2 ingress

Use OpenSSL and a curl build that lists `HTTP2` in `curl --version`. The optional ingress check uses
the same cluster JAR and PostgreSQL image. It also needs the pinned nginx 1.28.3 image in the local
Docker cache.

```sh
docker pull nginx@sha256:a8b39bd9cf0f83869a2162827a0caf6137ddf759d50a171451b335cecc87d236
python3 examples/petclinic-woge/cluster/run.py \
  examples/petclinic-woge/build/libs/woge-petclinic.jar /tmp/petclinic-reference --tls-ingress
```

nginx serves TLS on a random loopback port and forwards HTTP/1.1 to the separate JVMs. Linux uses
Docker host networking. The macOS Docker runtime needs the `host.docker.internal` mapping. The
runner generates a short-lived CA and an IP certificate for `127.0.0.1`, tests rejection without
the CA, then verifies the certificate chain and `h2` negotiation using the temporary CA.
[nginx HTTP/2 configuration](https://nginx.org/en/docs/http/ngx_http_v2_module.html)

The test routes requests to each JVM, signs in on the first and books a visit on the second using
the same Secure Spring Session cookie and CSRF token. The proxy replaces incoming forwarded
authority headers with its configured loopback authority. Spring enables forwarded headers for
this isolated ingress mode. An enhanced POST with a foreign Origin is rejected even if it supplies
its own forwarding headers. An authorized POST produces a Woge SSE invalidation on the first JVM
and a fresh patch from the second. Both SSE events arrive while one HTTP/2 stream stays open.
The test routing header and loopback certificate are fixture settings. Mobile networks, a managed
ingress and CDN behavior remain outside this test. [Spring MVC origin handling](../../docs/guides/spring-mvc-adapter.md#protect-form-actions)

## Try the Thymeleaf baseline

Set `PETCLINIC_WOGE_ENABLED=false` before startup and open `/owners/find`. The flag controls the Woge
routes and visit response negotiation. The Thymeleaf owner and visit pages, Spring Security login and CSRF checks, and the
authenticated `/api/reference/owners/1` endpoint are available in both modes.

The example installs the shared security and JSON fixture in `ClinicBaseline.kt`. The build adds a
Thymeleaf action to the upstream visit form so Spring includes its hidden CSRF token. The Woge routes
in `ClinicPages.kt` use the MVC starter, the MVC adapter, and KSP for typed page routes. The rollback
test restarts the application on the same test database and checks that a saved visit remains visible
and that another visit can be booked with the Thymeleaf form.

## What the integration adds

The measured integration at Woge commit `8ca617b56eebe9108708ea24d4cd571e98dd515c` added three
Kotlin production source files alongside the shared security and JSON fixture. The Gradle build
added KSP and the Woge plugin for typed routes, the MVC starter and adapter, and the version-matched
browser assets. It reused the existing routes, visit controller and repository. The owner-suggestions
island was added after this measurement.

The paired package build used PetClinic revision `500158f`, Spring Boot 4.1.1, Gradle 8.14.4,
JDK 21.0.6 on macOS arm64, and the local `0.1.0-beta.1` pilot artifacts.
The baseline kept the same Kotlin toolchain, Spring Security and JSON fixture, H2 seed,
and Thymeleaf CSRF wiring. It omitted the three integration source files, their tests,
the two Woge/KSP build plugins and the Woge runtime declarations.

| Spring Boot archive | Whole JAR size | Nested runtime JARs |
| --- | ---: | ---: |
| Secured Thymeleaf baseline | 65,792,964 bytes | 90 |
| Same application with Woge MVC integration | 70,583,189 bytes | 104 |
| Difference | 4,790,225 bytes (7.3%) | 14 |

Both packaged applications completed real HTTP checks for login, authenticated JSON, rejected
missing CSRF, native invalid-date validation and a corrected visit saved once. Eight Woge
artifacts and seven other runtime jars were added. A newer JetBrains annotations jar replaced
the baseline version. These counts apply to the configured packaged MVC application. Startup,
memory, load, and first-feature development time were outside this measurement.

To reproduce the package comparison, check out the measured Woge revision above and build
the application with the same properties, then duplicate the example directory. In the copy, keep `ClinicBaseline.kt` and the shared settings,
dependencies and resources. Remove the Woge production and test files, the Woge/KSP plugin
declarations, the three `dev.woge` dependencies and the Woge repository/version configuration.
Build both copies with `bootJar` and the same upstream checkout. Compare the executable file
sizes in `build/libs` and the archive entries under `BOOT-INF/lib/`.

The reference uses synthetic data. Every signed-in user may access the seeded owners. Deferred
refresh, live reconnect, reverse-proxy and WebFlux adoption evidence are tracked in
[the follow-up integration work](https://github.com/christian-draeger/woge/issues/286).
