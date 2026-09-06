package dev.woge.invalid

import org.springframework.web.reactive.function.server.ServerRequest

// WOGE-COMPILE-PORT-001: portable application code has no host-framework classpath.
internal fun invalidPortableBoundary(request: ServerRequest): String = request.path()
