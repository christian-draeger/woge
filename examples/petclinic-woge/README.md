# Add Woge to Spring PetClinic

This Spring MVC reference adds a Woge owner search, owner and pet details, and a native visit form
to the official [Spring PetClinic](https://github.com/spring-projects/spring-petclinic) source at
`500158f732419217507c7656904b8e6aa1bcc0d6`. The existing Java entities, repository, H2 seed data,
Thymeleaf pages, and visit controller are used directly. The example builds those files from a
separate checkout. PetClinic is an Apache-2.0 Spring project, and it is attributed in the application
archive.

## Run the reference

Build Woge's local Maven artifacts with JDK 21, then build this standalone consumer. The PetClinic
checkout path and Woge repository path are absolute.

```bash
git clone https://github.com/spring-projects/spring-petclinic.git /tmp/petclinic-reference
git -C /tmp/petclinic-reference checkout --detach 500158f732419217507c7656904b8e6aa1bcc0d6
./gradlew publishScaffoldArtifacts -PwogeVersion=0.1.0-SNAPSHOT
./gradlew -p examples/petclinic-woge bootRun \
  -PpetclinicSource=/tmp/petclinic-reference \
  -PwogeRepository="$PWD/build/scaffold-maven-repository" \
  -PwogeVersion=0.1.0-SNAPSHOT
```

Open `http://127.0.0.1:8080/woge/owners`. Sign in as `user` using the temporary password
printed by Spring Security at startup. Search for `Franklin`, open a pet and book a future visit.
The native form posts to PetClinic's original MVC controller. Invalid input returns the original
Thymeleaf validation view. A successful submission redirects to the existing owner page. Return
to Woge owner details to see the saved visit. The original `/owners/find` and the example's
authenticated `/api/reference/owners/1` route use the same application.

The reference currently verifies the native adoption path. A Woge action and enhanced update for
the visit belong to [the next part of the adoption proof](https://github.com/christian-draeger/woge/issues/286).
It uses synthetic data in a single in-memory database.

Verify the candidate with the same properties and the `test` task in place of `bootRun`.
