package dev.woge.protocol

/** Renders an already materialized loading fragment without executing its DSL program twice. */
public fun regionLoadingFrame(
    target: PatchTarget,
    revision: TargetRevision,
    loading: PatchHtml,
): HtmlFrame =
    HtmlFrame { sink ->
        sink.write("<div data-woge-region=\"${target.region.value}\" data-woge-revision=\"${revision.value}\">")
        sink.write(loading.value)
        sink.write("</div>")
    }
