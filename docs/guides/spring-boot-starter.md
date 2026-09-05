# Configure Woge with Spring Boot

The Woge starter configures shared policy and selects one server adapter. You still write normal
Spring routes, choose normal HTTP methods and use the browser as a browser.

For a complete runnable page first, use the [Spring Boot WebFlux quickstart](quickstart-spring-boot.md).

## Add one web stack

For the implemented WebFlux path, an application uses these three dependencies:

```kotlin
dependencies {
    implementation("dev.woge:woge-spring-boot-starter:<version>")
    implementation("dev.woge:woge-spring-webflux:<version>")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
}
```

The general Woge starter deliberately includes neither MVC nor WebFlux. This makes it safe to replace
the transport adapter without pulling two server stacks into the application. The MVC equivalent is
planned but not implemented yet.

## Connect a page to familiar routes

Declare your `PageUseCase` as a Spring bean. Boot discovers it for diagnostics and provides a
`WogeWebFluxHandlers` factory with the configured request-context and deferred-work policy:

```kotlin
@Configuration(proxyBeanMethods = false)
class ProjectRoutes {
    @Bean
    fun routes(
        projectPage: ProjectPage,
        handlers: WogeWebFluxHandlers,
    ): RouterFunction<ServerResponse> {
        val input = WebFluxPageInput { request ->
            ProjectInput(request.pathVariable("project"))
        }
        val page = handlers.page(projectPage, input)
        val patches = handlers.deferred(projectPage, input)

        return coRouter {
            GET("/projects/{project}", page::handle)
            GET("/projects/{project}/woge-patches", patches::handle)
        }
    }
}
```

The paths and decoder stay visible because they are application behavior. Generated typed route
descriptors can remove this small amount of repetition later without changing the page port.

HTML components are normal Kotlin functions or values called by the page. They do not need an
annotation or Spring component scan.

## Tune only measured limits

Defaults work without configuration. When measurements show a need, Spring's normal duration syntax
and IDE metadata are available:

```yaml
woge:
  deferred:
    max-concurrency: 6
    region-timeout: 2s
```

These limits apply per deferred patch-stream request. Invalid non-positive values fail while the
application context starts.

## Make security context explicit

The default `WebFluxRequestContextFactory` permits safe page methods and creates an anonymous Woge
request snapshot. A Spring Security application should expose its own bean that translates the
authenticated principal, capabilities and verified request facts into Woge-owned values. The default
backs off automatically.

## Resolve mixed Spring stacks

With one stack, `woge.adapter=auto` needs no property. If both Spring MVC and WebFlux are on the
classpath, Woge refuses to guess. Select the intended adapter and make Boot create the same web
application type:

```yaml
woge:
  adapter: webflux
spring:
  main:
    web-application-type: reactive
```

At startup, `WogeRuntimeInfo` and one log line report the selected adapter, patch protocol and runtime
versions, and the number of page/deferred use-case beans. A missing adapter error names the dependency
that must be added.

See [the WebFlux adapter guide](spring-webflux-adapter.md) for response and cancellation behavior and
[ADR 0029](../adr/0029-neutral-spring-boot-starter-and-explicit-adapter-selection.md) for the dependency
and ambiguity tradeoffs.
