package dev.woge.tck

import dev.woge.host.PageIdentity
import dev.woge.host.RenderIdentitySecret
import dev.woge.host.WogeRegion
import dev.woge.html.HtmlWriter
import dev.woge.html.p
import dev.woge.protocol.PageEpoch

@WogeRegion
public fun HtmlWriter.actionCount(count: Int) {
    p { text("Completed mutations: $count") }
}

@WogeRegion
public fun HtmlWriter.actionStatus(message: String) {
    text(message)
}

internal fun actionPageIdentity(): PageIdentity = PageIdentity(PageEpoch.of("tck-action-epoch"), actionIdentitySecret)

private val actionIdentitySecret = RenderIdentitySecret.random()
