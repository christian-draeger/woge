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
database. It checks native paging, keyboard and emulated touch selection, strict CSP, a stale
response, and fallback after a failed import or unavailable endpoint. Physical touch and
assistive-technology review remain [manual work](https://github.com/christian-draeger/woge/issues/253).

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
