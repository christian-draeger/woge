# Woge fallback client

This directory owns Woge's small browser-side adapter for the versioned patch protocol. It is not a
JVM module and does not define component semantics, application state or a general client framework.

Issues [#20](https://github.com/christian-draeger/woge/issues/20) and
[#21](https://github.com/christian-draeger/woge/issues/21) introduce its production protocol fixtures,
package metadata, JavaScript source and cross-browser tests. Until then, the executable M0 prototype
remains evidence only in the
[`fallback-patch-runtime` report](../../spikes/fallback-patch-runtime/evidence.md).
