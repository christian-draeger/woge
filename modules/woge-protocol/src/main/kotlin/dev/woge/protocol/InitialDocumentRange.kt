package dev.woge.protocol

import java.util.UUID

/** A typed, experimental parser range. Only passive, provenance-checked HTML can enter it. */
public class InitialDocumentRange private constructor(
    private val name: String,
    private val target: PatchTarget,
    private val revision: TargetRevision,
) {
    /** Writes a stable region wrapper and the range while the original document parser is active. */
    public fun startFrame(loading: PatchHtml): HtmlFrame {
        require(isInitialDocumentHtmlEligible(loading)) { "Ineligible initial document loading" }
        return HtmlFrame { sink ->
            sink.write("<?start name=\"$name\">")
            sink.write("<div data-woge-region=\"${target.region.value}\" data-woge-revision=\"${revision.value}\">")
            sink.write(loading.value)
            sink.write("</div><?end>")
        }
    }

    /** Writes one terminal replacement; ineligible HTML is rejected before any template bytes. */
    public fun replaceFrame(patch: ReplacePatch): HtmlFrame {
        require(patch.target == target) { "Initial document replacement must address its declared region" }
        require(
            patch.revision == TargetRevisionStep.after(revision) &&
                patch.interactionSequence == InteractionSequence.INITIAL,
        ) { "Initial document replacement must advance its declared initial revision once" }
        require(isInitialDocumentHtmlEligible(patch.html)) { "Ineligible initial document replacement" }
        return HtmlFrame { sink ->
            sink.write("<template for=\"$name\">")
            sink.write("<div data-woge-region=\"${target.region.value}\" ")
            sink.write("data-woge-revision=\"${patch.revision.next.value}\">${patch.html.value}</div></template>")
        }
    }

    public companion object {
        /** Returns no range for unsafe or active loading HTML, including in forced native mode. */
        public fun create(
            target: PatchTarget,
            revision: TargetRevision,
            loading: PatchHtml,
        ): InitialDocumentRange? =
            if (isInitialDocumentHtmlEligible(loading)) {
                InitialDocumentRange("woge-${UUID.randomUUID()}", target, revision)
            } else {
                null
            }
    }
}

/** Opens a normal HTML document. The head is DSL-rendered, never assembled by application strings. */
public fun initialDocumentStart(head: HtmlFrame): HtmlFrame =
    HtmlFrame { sink ->
        sink.write("<!doctype html><html><head>")
        head.writeTo(sink)
        sink.write("</head><body>")
    }

/** Closes the document only after its streamed updates have completed. */
public fun initialDocumentEnd(): HtmlFrame = HtmlFrame { it.write("</body></html>") }
