# Build and test Woge

Woge uses the checked-in Gradle wrapper. A fresh clone needs a JDK 21 installation and network access for the first dependency download; produced JVM libraries target Java 17.

Run the complete local gate from the repository root:

```shell
./gradlew check
```

The gate compiles the convention plugins, verifies explicit API mode, runs tests with XML and HTML reports, checks Kotlin formatting with ktlint, analyzes Kotlin with Detekt, validates ADRs and module boundaries, and checks local documentation/snippet links.

## Focused commands

```shell
./gradlew test
./gradlew detekt
./gradlew ktlintCheck
./gradlew ktlintFormat
./gradlew validateDocumentation
./gradlew validateAdrs
./gradlew validateModuleBoundaries
```

`ktlintFormat` changes source files; the other commands are checks. Test reports are written below each project's `build/test-results` and `build/reports/tests` directories. Detekt writes machine-readable XML/SARIF and an HTML report below `build/reports/detekt`.

## Source ownership

- Hand-written production Kotlin belongs in a module's `src/main/kotlin` directory.
- Hand-written tests belong in `src/test/kotlin`.
- Generated Kotlin belongs below `build/generated`; do not copy it into `src/main` or edit it manually.
- Executable documentation applications and snippets belong below [`examples`](../../examples/README.md) and participate in the root build once introduced.
- Architecture experiments remain isolated below [`spikes`](../../spikes/README.md). Production code must not depend on them.

The version catalog owns tool and dependency versions. Convention plugins in `build-logic` own shared compilation, quality and reporting behavior. A module build file should describe only that module's role and dependencies.

The scaffold contains no publishing credentials, automatic snapshots or template-specific release assumptions. Publication is added with its own threat and release review in the M3 release work.
Its selectively adopted template patterns and deliberate omissions are recorded in
[Repository scaffold provenance](scaffold-provenance.md).
