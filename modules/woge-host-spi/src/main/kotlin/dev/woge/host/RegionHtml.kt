package dev.woge.host

import dev.woge.html.HtmlWriter
import dev.woge.protocol.PatchHtml

/** Typed HTML for deferred loading and failure content, without protocol-package imports. */
public fun regionHtml(content: HtmlWriter.() -> Unit): PatchHtml = dev.woge.protocol.patchHtml(content)
