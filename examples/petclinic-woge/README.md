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

## Try the Thymeleaf baseline

Set `PETCLINIC_WOGE_ENABLED=false` before startup and open `/owners/find`. The flag controls the Woge
routes and visit response negotiation. The Thymeleaf owner and visit pages, Spring Security login and CSRF checks, and the
authenticated `/api/reference/owners/1` endpoint are available in both modes.

The example installs the shared security and JSON fixture in `ClinicBaseline.kt`. The build adds a
Thymeleaf action to the upstream visit form so Spring includes its hidden CSRF token. The Woge routes
in `ClinicPages.kt` use the MVC starter, the MVC adapter, and KSP for typed page routes. The rollback
test restarts the application on the same test database and checks that a saved visit remains visible
and that another visit can be booked with the Thymeleaf form.

The reference uses synthetic data. Every signed-in user may access the seeded owners. Deferred
refresh, live reconnect, reverse-proxy and WebFlux adoption evidence are tracked in
[the follow-up integration work](https://github.com/christian-draeger/woge/issues/286).
