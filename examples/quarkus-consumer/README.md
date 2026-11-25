# Quarkus JVM consumer

The application resolves `dev.woge:woge-quarkus:0.1.0-quarkus-preview.1` from a local Maven
repository built from the same Woge checkout. This candidate is unpublished. It uses
Quarkus 3.40.1.2 LTS, Vert.x 4.5.35, Kotlin 2.4.10, Gradle 8.14.4 and JDK 21.

## Run the application

At the Woge repository root:

```sh
./gradlew publishScaffoldArtifacts :woge-quarkus:publishCandidatePublicationToCandidateRepository \
  -PwogeVersion=0.1.0-quarkus-preview.1
./gradlew -p examples/quarkus-consumer test quarkusBuild standaloneConsumer \
  -PwogeRepository="$PWD/build/scaffold-maven-repository"
java -jar examples/quarkus-consumer/build/quarkus-app/quarkus-run.jar
```

Open [the validated notes page](http://127.0.0.1:8080/validated-notes), submit whitespace and
correct the value in the native form. The [enhanced notes page](http://127.0.0.1:8080/enhanced-notes)
uses a second maintained portable example for incremental updates. Open the
[forecast](http://127.0.0.1:8080/forecast) to watch its deferred patch or follow the complete link
with JavaScript disabled. The examples share their source and assets with the
[complexity gradient](../complexity-gradient).

Unpack `build/distributions/woge-quarkus-consumer-0.1.0-quarkus-preview.1.zip` from the consumer
build outside the Woge checkout to try a separate application build:

```sh
./gradlew test quarkusBuild -PwogeRepository=/absolute/path/to/the/candidate/repository
java -jar build/quarkus-app/quarkus-run.jar
```

Only the named candidate coordinates resolve from the supplied Woge repository. The application
build has its own settings and includes the portable sources it uses.

## Configure the host

Register the [routes](src/main/kotlin/example/quarkus/Routes.kt) on the managed Vert.x Router.
When mapping Quarkus authentication, enable `quarkus.http.auth.propagate-security-identity=true`
and copy the authorized principal and roles before invoking portable work. The identity route
returns 403 for anonymous visitors and for users without the `reader` role. The test uses an
isolated Quarkus security realm. Configure a real identity provider when adapting this example
for an application that requires sign-in.

Keep forwarded headers under Quarkus's proxy trust configuration. The Woge same-origin check
uses the effective URI resolved by the host. For shared render identities, configure one
`RenderIdentitySecret` explicitly when creating the handler factory. Store it outside source
control and keep it aligned across participating nodes. The demo binds on loopback and stores
notes only in process memory.

[Adapter contracts and support scope](../../adapters/woge-quarkus) ·
[Quarkus Router and security](https://quarkus.io/version/3.40/guides/security-customization#securityidentity-injection-in-vert-x-route-handlers)

## Smoke-test a Linux native build

Generate the local candidate as above, then use the pinned Mandrel JDK 25 builder with Docker.
The JVM application and Gradle toolchain still use JDK 21. This profile verifies the static
HTML page, classpath CSS and a complete streamed forecast on Linux ARM64.

```sh
image=quay.io/quarkus/ubi9-quarkus-mandrel-builder-image@sha256:d5b570190cfd2cce0ebef2e197782a67b3b3f9364ccc1cf2b60db79648057515
./gradlew -p examples/quarkus-consumer quarkusBuild \
  -PwogeRepository="$PWD/build/scaffold-maven-repository" \
  -Dquarkus.native.enabled=true -Dquarkus.package.jar.enabled=false \
  -Dquarkus.native.container-build=true -Dquarkus.native.container-runtime=docker \
  -Dquarkus.native.builder-image="$image" -Dquarkus.native.native-image-xmx=3g
```

The [native CI job](../../.github/workflows/quarkus-adapter.yml) runs the executable in Linux
and calls the [HTTP smoke check](verify-native.mjs). The Mandrel JDK 21 builder stops at
Quarkus's Native Image 25.0.0 minimum-version check. The smoke scope covers the application
configuration above. [Quarkus Native Image guide](https://quarkus.io/version/3.40/guides/building-native-image/)
