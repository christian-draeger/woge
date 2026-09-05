package dev.woge.html

import dev.woge.css.CssStylesheet

/** Writes an application-owned link relation with a validated URL. */
public fun HtmlWriter.assetLink(
    rel: String,
    href: HtmlUrl,
    attributes: Attributes.() -> Unit = {},
) {
    requireAttributeTokenList(rel, "link relation")
    voidElement(
        "link",
        attributes = {
            attribute("rel", rel)
            url("href", href)
            attributes()
        },
    )
}

/** Writes an external stylesheet link with explicit CSP, SRI and CORS hooks. */
public fun HtmlWriter.stylesheet(
    href: HtmlUrl,
    media: String? = null,
    nonce: CspNonce? = null,
    integrity: SubresourceIntegrity? = null,
    crossOrigin: CrossOrigin? = null,
) {
    requireCrossOriginForExternalIntegrity(href, integrity, crossOrigin)
    assetLink("stylesheet", href) {
        media?.let { attribute("media", it) }
        nonce?.let { attribute("nonce", it.value) }
        integrity?.let { attribute("integrity", it.value) }
        crossOrigin?.let { attribute("crossorigin", it.htmlValue) }
    }
}

/** Writes a complete inline stylesheet; the caller owns the corresponding CSP policy. */
public fun HtmlWriter.style(
    css: CssStylesheet,
    nonce: CspNonce? = null,
    media: String? = null,
) {
    require(!css.source.contains(STYLE_END, ignoreCase = true)) {
        "An inline stylesheet must not contain an HTML </style sequence"
    }
    rawTextElement(
        name = "style",
        attributes = {
            nonce?.let { attribute("nonce", it.value) }
            media?.let { attribute("media", it) }
        },
        content = css.source,
    )
}

/** Writes one external JavaScript module. Inline script source requires a separate unsafe boundary. */
public fun HtmlWriter.moduleScript(
    src: HtmlUrl,
    nonce: CspNonce? = null,
    integrity: SubresourceIntegrity? = null,
    crossOrigin: CrossOrigin? = null,
) {
    requireCrossOriginForExternalIntegrity(src, integrity, crossOrigin)
    rawTextElement(
        name = "script",
        attributes = {
            attribute("type", "module")
            url("src", src)
            nonce?.let { attribute("nonce", it.value) }
            integrity?.let { attribute("integrity", it.value) }
            crossOrigin?.let { attribute("crossorigin", it.htmlValue) }
        },
        content = "",
    )
}

/** Writes a preload hint for an application-owned asset without restricting future `as` values. */
public fun HtmlWriter.preload(
    href: HtmlUrl,
    asType: String,
    mimeType: String? = null,
    integrity: SubresourceIntegrity? = null,
    crossOrigin: CrossOrigin? = null,
) {
    requireAttributeTokenList(asType, "preload as value")
    requireCrossOriginForExternalIntegrity(href, integrity, crossOrigin)
    assetLink("preload", href) {
        attribute("as", asType)
        mimeType?.let { attribute("type", it) }
        integrity?.let { attribute("integrity", it.value) }
        crossOrigin?.let { attribute("crossorigin", it.htmlValue) }
    }
}

/** Writes ordinary named document metadata. */
public fun HtmlWriter.metadata(
    name: String,
    content: String,
) {
    requireAttributeTokenList(name, "metadata name")
    voidElement("meta") {
        attribute("name", name)
        attribute("content", content)
    }
}

/** Writes property-based metadata such as Open Graph values. */
public fun HtmlWriter.propertyMetadata(
    property: String,
    content: String,
) {
    requireAttributeTokenList(property, "metadata property")
    voidElement("meta") {
        attribute("property", property)
        attribute("content", content)
    }
}

private fun requireAttributeTokenList(
    value: String,
    label: String,
) {
    require(value.isNotBlank() && value.none { it.isISOControl() }) { "$label must not be blank or contain controls" }
}

private const val STYLE_END: String = "</style"
