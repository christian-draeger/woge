# Reference application

This directory is the maintained multi-host consumer of Woge's public modules. Its project-operations
domain and journeys are defined in [ADR 0004](../../docs/adr/0004-project-operations-reference-application.md).

The application will share framework-neutral page and domain code while keeping Spring MVC, Spring
WebFlux and Ktor launchers at the outer edge. It is a build consumer and executable documentation, not
a production Woge artifact. Issue [#24](https://github.com/christian-draeger/woge/issues/24) owns the
complete walking-skeleton slice.
