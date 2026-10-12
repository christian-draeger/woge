# Use Woge from GitHub Packages

Woge's first Maven registry is `https://maven.pkg.github.com/christian-draeger/woge`.
Maven Central and the Gradle Plugin Portal are not release destinations yet.
The publication setup does not mean a version has already been uploaded: check the repository's
Packages page for an available version before choosing it.

## Authenticate once in your shell

GitHub's Maven registry requires authentication **even for public packages**.
Use your GitHub username and a personal access token (classic) with `read:packages`, authorized
for this repository (including any required organization SSO). For private packages your account
also needs access to the package. Do not put the token in source files, URLs or command arguments.

Gradle's named credentials provider reads these environment variables:

```shell
export ORG_GRADLE_PROJECT_githubPackagesUsername=your-github-username
read -rs ORG_GRADLE_PROJECT_githubPackagesPassword
export ORG_GRADLE_PROJECT_githubPackagesPassword
```

The `read` command waits for your token without echoing it or saving it in shell history.
Clear both variables when finished. CI consumers may use a repository secret or a `GITHUB_TOKEN`
with `packages:read` and explicit package access; a token from another repository does not
automatically have access.

## Configure a standalone Gradle application

Add the registry in **both** sections of your application's `settings.gradle.kts`.
You do not need the Woge checkout, an included Woge build or a local Maven repository.
Keep your normal Kotlin, Spring Boot and other plugin repositories.

```kotlin
pluginManagement {
    repositories {
        maven {
            name = "githubPackages"
            url = uri("https://maven.pkg.github.com/christian-draeger/woge")
            credentials(PasswordCredentials::class)
            content { includeGroupByRegex("dev\\.woge.*") }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            name = "githubPackages"
            url = uri("https://maven.pkg.github.com/christian-draeger/woge")
            credentials(PasswordCredentials::class)
            content { includeGroup("dev.woge") }
        }
        mavenCentral()
    }
}
```

The first repository resolves plugin markers such as `dev.woge.spring-boot`; the second resolves
libraries such as `dev.woge:woge-spring-boot-starter`. Set the same published `wogeVersion` for
the Woge plugins, libraries and `dev.woge:woge-fallback-client-assets`.
The [Spring Boot scaffold](../../scaffolds/spring-boot/README.md) already has both sections.
Its `-PwogeRepository=/path/to/repository` override still uses only the build-local registry for
Woge and does not require GitHub credentials.

## Browser assets and source

`woge-fallback-client-assets` serves the checked-in browser modules from the JVM classpath.
Its Maven version and embedded package metadata match the JVM libraries, including snapshots.
There is no npm publication in this release path. The existing `@woge/fallback-client` npm package
and its separate bundling commands remain opt-in; its package.json version is not a claim of npm
availability.

Each implementation artifact includes a sources JAR and a documentation JAR (`javadoc` classifier).
The latter contains the maintained Markdown guides and architecture documentation, not a generated
Kotlin API HTML site. Dependency-only starters include their build definition as source. Plugin
markers are POM-only pointers to the implementation; plugin sources belong to `woge-gradle-plugin`.

For maintainers, see [publication and release checks](../development/publishing.md).
