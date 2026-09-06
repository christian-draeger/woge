# Woge Spring MVC adapter

This adapter runs framework-neutral Woge page and deferred-region use cases on the conventional
Spring Servlet stack. Applications keep their normal MVC URL mappings and do not own response or
coroutine lifecycle controllers.

Start with [Run a Woge page with Spring MVC](../../docs/guides/spring-mvc-adapter.md). The executable
consumer is [`woge-reference-spring-mvc`](../../examples/reference-application/spring-mvc).

The real-server TCK verifies response metadata, page-frame flushes, patch completion order and safe
pre-commit failures. Servlet disconnect detection differs from WebFlux: an idle disconnected client
is observable only when a later write fails. Timeout, container-error and failed-write cancellation
are still propagated to the request coroutine.
