# Woge Spring WebFlux adapter

This module maps framework-neutral Woge page and deferred-region use cases to Spring WebFlux
functional handlers. Reactor and response-lifecycle details remain inside the adapter.

Start with [Run a Woge page with Spring WebFlux](../../docs/guides/spring-webflux-adapter.md). The
[Spring Boot auto-configuration](../../docs/guides/spring-boot-starter.md) can provide the shared
handler factory. The [executable reference application](../../docs/guides/quickstart-spring-boot.md)
shows the complete Spring Boot path.

Adapter tests invoke the framework-neutral
[server-adapter TCK](../../docs/architecture/server-adapter-parity.md) over a real Reactor Netty
connection. Focused WebFlux tests may cover framework mechanics, but they do not replace that shared
semantic contract.
