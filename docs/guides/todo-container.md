# Run the Todo application in a container

The [Todo example](../../examples/todo-spring-boot/README.md) is a concrete application image.

## Run the published demo

The verified image starts without a JDK, Gradle or Maven credentials on your machine:

```shell
docker run --rm --platform linux/amd64 \
  --publish 127.0.0.1:8080:8080 \
  ghcr.io/christian-draeger/woge-todo@sha256:aca01de6edc7b4596569e95c3fe1624a9c82629d59ce9f40048f212ed1fa08c9
```

Open [localhost:8080](http://localhost:8080). All visitors share one in-memory store. Restarting
loses the data; run one instance. Durable or replicated deployments need an application-owned
shared store and session/live-event distribution.

| Release fact | Verified value |
| --- | --- |
| Image version | `ghcr.io/christian-draeger/woge-todo:0.1.0-demo.1` |
| Immutable index digest | `sha256:aca01de6edc7b4596569e95c3fe1624a9c82629d59ce9f40048f212ed1fa08c9` |
| Application source | [`ecac09a`](https://github.com/christian-draeger/woge/commit/ecac09a1cfd81f6b154fd7bbdc22a2234f4fedea) |
| Woge dependency | `0.1.0-alpha.1` |
| Tested image platform | `linux/amd64` |
| Publication and attestations | [Release workflow](https://github.com/christian-draeger/woge/actions/runs/39388988980) |

The registry image includes BuildKit provenance and an SPDX 2.3 SBOM (243 packages). The
[release evidence](../../testing/deployment/todo-0.1.0-demo.1.json) records the
actual image labels, platform manifest and attestation digests. The exact digest above passed
read-only/non-root container, nginx, native request, streaming, shutdown and three-engine browser
verification: 15 browser checks pass. Three live-update cases are intentionally skipped for native
pages, which refresh through ordinary navigation.
These attestations record the build; a cryptographic signature was not verified. Local Apple Silicon
runs use amd64 emulation; this release does not advertise an arm64 image. Woge remains alpha.

## Build your own image

Woge libraries still arrive as JVM artifacts. Build the production JAR from the example directory:

```shell
./gradlew check bootJar
docker build --tag woge-todo:local .
docker run --rm --publish 127.0.0.1:8080:8080 woge-todo:local
```

Open [localhost:8080](http://localhost:8080). The image uses a digest-pinned Java 21 JRE and user
10001, starts Java directly for SIGTERM delivery and contains only the production JAR. The build
context excludes source, browser-test tools and credentials. Set VERSION and REVISION build
arguments when recording a build; never label a working tree as an already published release.

`PORT` defaults to 8080. Health probes are `/actuator/health/liveness` and
`/actuator/health/readiness`. The app exposes only health through Actuator. Configure your cloud's
trusted proxy/TLS origin, streaming behavior and private diagnostics as described in
[production operations](production-operations.md).

## Verify the actual image

From the Woge repository root:

```shell
python3 scripts/test-todo-container.py woge-todo:local
```

The probe creates uniquely named containers and a network, runs the app with a read-only root
filesystem and a bounded temporary directory, and sends real native requests through nginx.
It checks readiness, mutation/escaping, cross-origin rejection, SSE flushing and bounded SIGTERM
shutdown. It writes the image ID, architecture, source labels and results under
`build/todo-container`, then removes only its own containers and network.

To run the complete browser matrix inside the matching Linux image, install the example's test-only
npm dependencies first and add:

```shell
python3 scripts/test-todo-container.py woge-todo:local \
  --browser-image mcr.microsoft.com/playwright:v1.62.1-noble
```

The browser container shares the proxy's network namespace and visits loopback. Production browser
traffic uses HTTPS. A plain non-loopback HTTP name does not supply the secure browser APIs used by
enhancement; native forms remain useful when enhancement cannot initialize.

## Dockerfile and Buildpacks

The pinned Dockerfile is the reference recipe: it copies the same already verified Boot JAR,
reuses the existing JVM deployment boundary and keeps the runtime/dependency inventory explicit.
For an application that already uses Cloud Native Buildpacks, Spring Boot also provides:

```shell
./gradlew bootBuildImage --imageName=woge-todo:buildpacks
```

That path needs a compatible builder/run image, Docker and buildpack downloads; its builder/platform
matrix and generated SBOM need their own verification. It is an optional alternative, not another
Woge build subsystem. The [Ktor reference recipe](../../testing/deployment/Dockerfile) packages its
ordinary JVM distribution under the same application/deployment contracts.

## Storage and release boundary

This public demo shares one process-local in-memory repository. Restarting loses the data.
Run one instance; a replicated deployment needs an application-owned durable store and live-event/
session policy. An OCI image does not supply those application decisions.

Only advertise architectures actually tested for the exact image. A release image must record its
source commit, immutable digest and provenance/SBOM. The public release above has completed those
checks. Maven/OCI publishing is an explicit release operation; a normal build or test never uploads
an image.

Maintainers can explicitly approve publication by running **Publish Todo image** from main with the
exact current main SHA and a fresh image version. The workflow requires successful main checks,
builds from published Maven dependencies, runs the real container/proxy probe, and publishes
`ghcr.io/christian-draeger/woge-todo` with SBOM/provenance and a recorded digest. Image and Woge
dependency versions are separate and both remain traceable through the source. The current workflow
publishes its verified Linux amd64 path; broader architectures need their own release verification.
An existing tag or an ambiguous registry lookup blocks publication. GitHub Packages is not an
atomic cross-artifact release transaction; do not race this serialized workflow with manual uploads.

## Inspect the release metadata

Retrieve the immutable index, provenance and SBOM from the registry without credentials:

```shell
todo_image=ghcr.io/christian-draeger/woge-todo@sha256:aca01de6edc7b4596569e95c3fe1624a9c82629d59ce9f40048f212ed1fa08c9
docker buildx imagetools inspect "$todo_image"
docker buildx imagetools inspect "$todo_image" --format '{{json .Provenance}}'
docker buildx imagetools inspect "$todo_image" --format '{{json .SBOM}}'
```

The index includes one application platform, `linux/amd64`, plus an attestation manifest with an
`unknown/unknown` platform marker. That marker is metadata, not another supported application
architecture. The provenance names the source commit and the publication workflow run.
