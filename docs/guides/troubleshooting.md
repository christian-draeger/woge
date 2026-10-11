# Troubleshoot HTML, updates and development

Start in the browser Network panel: did the document GET work, did the module load, and what status
and media type did the POST or stream return? Try the ordinary full-page GET with JavaScript disabled.
Do not repeatedly submit a POST to diagnose a lost response.

| Symptom | Check and next step |
| --- | --- |
| All regions arrive at once; SSE stays pending without notices | Compare the direct server with the proxy using `curl -N -i URL`. Check proxy/CDN buffering, compression and idle timeouts. A pending SSE request is normal; absent expected events are not. Use the tested [proxy profiles and timing evidence](streaming-behind-proxies.md). |
| Stale or rejected patch | Read the stable diagnostic code, not raw HTML. `WOGE_STALE_PATCH` means an older update was refused, not an instruction to replay a mutation. Check tab/page identity and request ordering; use a safe GET or full navigation. See [browser checks](browser-replace-runtime.md) and [semantic observations](semantic-observability.md). |
| Recovery stops | Recovery is deliberately bounded across fresh page identities. Keep the page usable and offer manual navigation; do not reset budgets in a retry loop. A POST is never retried. Check [recovery classification](../adr/0062-latest-intent-and-bounded-region-recovery.md). |
| 406, unsupported protocol, or missing browser module after deployment | Align Woge artifacts, browser assets and KSP output. Retain old content-hashed assets during rollout. See [version skew](compatibility-and-upgrades.md); native HTML/forms remain the fallback. |
| Generated descriptor missing or referring to a renamed function | Check the KSP plugin, matching processor version and serialization plugin for commands. Run the affected module's `compileKotlin`; if stale output persists, run its `clean compileKotlin`. Fix the declaration in `src`, never generated files. `wogeDev` attempts one full regeneration, not endless retries. |
| Last working page remains after a save | A failed compile intentionally leaves the old app serving. Read the source diagnostic and fix it. It is not JVM Hot Reload. |
| `wogeDev` does not become Ready | Read startup/occupied-port/crash-loop diagnostics. Use `--port=9000`; Woge never evicts another process. Restart after dependency, plugin or source-set changes. |
| Stale dev overlay or shutdown trouble | Check the terminal's Ready URL and SSE connection. Ctrl+C stops the owned child/channel. If interrupted, inspect this app's `build/woge-dev/session.properties` before stopping only that PID. See the [lifecycle checklist](development-loop.md#troubleshooting). |
| Action returns 403 | Check same-origin evidence, trusted proxy headers, authentication and the host's CSRF policy. Do not disable checks to make fetch work. See [action security](typed-actions.md#register-a-post-endpoint). |

Production has no dev diagnostic channel or relaxed dev CSP. Raw compiler/application logs can
contain secrets; inspect locally and redact before sharing. Never log form bodies, tokens, render
secrets or patch HTML to diagnose a revision problem.
