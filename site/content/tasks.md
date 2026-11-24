---
title: Everyday Woge tasks
description: Start with a useful document and plain CSS. Add server-side validation and browser updates where your users need them.
---

## Render and style a page

A normal GET can return a complete document. This compiled example declares a route, writes semantic HTML and links a stylesheet. [Complete example and route binding](https://github.com/christian-draeger/woge/tree/main/examples/complexity-gradient/src/main/kotlin/dev/woge/examples/gradient/t01staticpage) · [Style pages and load assets](https://github.com/christian-draeger/woge/blob/main/docs/guides/css-and-assets.md)

<!-- snippet: examples/complexity-gradient/src/main/kotlin/dev/woge/examples/gradient/t01staticpage/StaticPage.kt -->

## Submit a form and show errors

Use a typed action for the POST and validate on the server. Return a redirect after success. On failure, render the submitted values, a summary and field errors linked to the inputs. The HTML path remains useful if the browser client is unavailable. [Typed actions and native errors](https://github.com/christian-draeger/woge/blob/main/docs/guides/typed-actions.md#render-native-field-errors) · [Localized invoice example](https://github.com/christian-draeger/woge/blob/main/docs/guides/enhanced-uploads-and-dynamic-forms.md#validate-a-localized-invoice)

## Update parts of a page

Enhance a form when a full reload interrupts the user's work. The task board prepares updates for its list, count and status while the input keeps its place. For independent slow data, the project page loads sections as they finish and offers a normal link to the complete page. [Try the task board and project page](https://github.com/christian-draeger/woge/blob/main/examples/reference-application/README.md#follow-the-web-flow)

## Keep data fresh

Use ordinary links and GETs to refresh data. For a live view, server-sent events can signal that a region changed. The browser requests current HTML from its regular authorized route. [Live updates](https://github.com/christian-draeger/woge/blob/main/docs/guides/live-updates.md)

## Work with an editor or coding agent

People and agents use the same public API, application instructions and verification commands. The scaffold includes version-matched guidance. The development loop rebuilds and reloads after edits. Its optional MCP endpoint is experimental and local. [Application agent guidance](https://github.com/christian-draeger/woge/blob/main/docs/ai-dx/application-agent-guidance.md) · [Framework index](https://github.com/christian-draeger/woge/blob/main/docs/ai-dx/woge-framework-index.json) · [Development loop](https://github.com/christian-draeger/woge/blob/main/docs/guides/development-loop.md) · [Experimental MCP](https://github.com/christian-draeger/woge/blob/main/docs/guides/development-mcp.md)
