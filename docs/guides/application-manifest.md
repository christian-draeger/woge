# Application manifest

`./gradlew wogeManifest` writes `.woge/manifest.json`. `./gradlew build` also runs it.
The file describes your application's compiler-generated web declarations for development tools,
tests and coding agents. It does not need a running server, Node.js or source-code scanning.

## Configure it

The Spring Boot scaffold already configures the task. In another Spring Boot application, the
`dev.woge.spring-boot` plugin adds it automatically. Supply the actual Kotlin version and selected
host, alongside the same version used in your Kotlin plugin configuration:

```kotlin
tasks.named<dev.woge.gradle.WogeManifestTask>("wogeManifest") {
    kotlinVersion.set("2.4.10")
    hostAdapter.set("spring-mvc") // Or "spring-webflux".
    capabilities.set(listOf("pages", "actions", "regions"))
    frontendMode.set("server-html")
}
```

If you use the scaffold's `kotlinVersion` and `wogeSpringAdapter` Gradle properties, those supply
the version and host automatically. Its temporary MVC override also changes the manifest.
For Ktor, apply `dev.woge.application` next to `org.jetbrains.kotlin.jvm` and configure
`hostAdapter.set("ktor")`. Apply KSP and add `dev.woge:woge-ksp` to the `ksp` configuration to
generate descriptors, just as for typed routes/actions/regions.

Only list capabilities that your app enables. They are descriptive settings, not permissions or
proof that an endpoint is bound. `server-html` is the default frontend mode; use a descriptive
non-secret label if you deliberately configure a frontend pipeline. No pipeline is installed.
Empty capabilities/descriptors are valid for an app without declared enhancements/annotations.

## Schema version 1

The required top-level fields are:

| Field | Meaning |
| --- | --- |
| `schemaVersion` | Integer `1` |
| `wogeVersion`, `kotlinVersion` | Build versions, not runtime guesses |
| `hostAdapter` | `spring-mvc`, `spring-webflux` or `ktor` |
| `capabilities` | Sorted unique list of explicitly enabled capabilities |
| `frontendMode` | The configured frontend mode; default `server-html` |
| `documentation` | Canonical `home`, `guides` and `manifest` links |
| `descriptors` | Sorted list of structural compiler descriptors |

Each descriptor has `kind`, `id` and `declaration`. `kind` is `page`, `action`, `component` or
`region`. The declaration is the fully qualified generated descriptor (or component class).
Pages retain their route-template ID and `path`; actions retain their public action ID and POST
`path`. Component/region IDs are their existing fully qualified identities. `inputType`, `component`
and `keyType` are included only when applicable. Types are names, never instantiated values.

```json
{
  "schemaVersion": 1,
  "wogeVersion": "0.1.0-SNAPSHOT",
  "kotlinVersion": "2.4.10",
  "hostAdapter": "spring-webflux",
  "capabilities": ["pages"],
  "frontendMode": "server-html",
  "documentation": {
    "home": "https://github.com/christian-draeger/woge",
    "guides": "https://github.com/christian-draeger/woge/tree/main/docs/guides",
    "manifest": "https://github.com/christian-draeger/woge/blob/main/docs/guides/application-manifest.md"
  },
  "descriptors": [
    {"kind": "page", "id": "/", "declaration": "example.woge.HomeRoute",
     "inputType": "example.woge.HomeInput", "path": "/"}
  ]
}
```

Descriptors sort by kind then ID; object fields and capabilities also have stable ordering.
There are no timestamps, absolute local paths or random build IDs. KSP maintains its generated
catalogues during incremental builds; removed declarations cannot remain in a successful manifest.
Duplicate descriptor identities across modules fail the build, rather than silently picking one.

Consumers must check `schemaVersion`, reject missing required fields and unknown descriptor kinds,
and tolerate additive object fields within version 1. A changed meaning, removed field or new kind
requires a new schema version. Unsupported versions fail explicitly, never as an empty application.

## Use it in-process

The host-neutral public model reads the file directly, not terminal output:

```kotlin
import dev.woge.host.ApplicationManifest
import java.nio.file.Files
import java.nio.file.Path

val manifest = ApplicationManifest.decode(
    Files.readString(Path.of(".woge/manifest.json")),
)
val descriptors = manifest.descriptors
```

For just a package's descriptors, use its generated `wogeDescriptors` list directly. It contains
the same `DescriptorMetadata` values and needs no JSON parsing. Neither API loads page/action
objects, scans the classpath or registers endpoints.

## Lifetime and production

Do not edit or commit `.woge/manifest.json`. `clean` removes it, and a main compile invalidates
the previous file before processing so a failed compile cannot leave an apparently current manifest.
After a standalone `classes`/`bootRun`/development rebuild, run `wogeManifest` when you need a fresh
file. Build consumers should depend on that task, not read an old file independently of the build.

The default file contains no source bodies, rendered HTML, secrets, cookies or request/session data.
Do not put secrets in its explicit build settings. It is not copied into production JARs or served
over HTTP. Compiler catalogues and typed lists contain structural library metadata and can be
packaged with libraries for downstream tools. If you deliberately copy the application JSON into
your production resources, review that exposure yourself; Woge never installs a discovery endpoint.
