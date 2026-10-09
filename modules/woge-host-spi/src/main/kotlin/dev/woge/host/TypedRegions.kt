package dev.woge.host

import dev.woge.html.Attributes
import dev.woge.html.HtmlWriter
import dev.woge.protocol.PatchHtml
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.TargetRevision
import dev.woge.protocol.patchHtml
import kotlin.reflect.KClass

/**
 * Marks an `HtmlWriter` function as a region that Woge can replace later.
 *
 * The Woge KSP processor generates a descriptor named after the function: `fun HtmlWriter.summary(...)`
 * becomes `SummaryRegion`. Leave [component] empty for a region that appears once per page. Name a
 * [WogeComponent] class when the region belongs to a repeated part of the page, such as a table row.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class WogeRegion(
    val component: KClass<*> = Unit::class,
)

/**
 * Marks a class as a component: a part of the page that owns regions.
 *
 * Mark one constructor parameter with [WogeKey] when the component can appear more than once.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class WogeComponent

/**
 * Marks the constructor parameter that tells repeated components apart, such as a task ID.
 *
 * Supported types are `String`, `Long`, `Int`, `java.util.UUID` and value classes that wrap one of them.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
public annotation class WogeKey

/**
 * Base of every generated region descriptor. Application code uses the generated object, never this type.
 *
 * [name] and [component] are stable identity names; they are hashed and never reach the page.
 */
public abstract class RegionDescriptor<Input> internal constructor(
    name: String,
    component: String?,
) {
    internal val name: IdentityName = IdentityName.of(name)
    internal val component: IdentityName? = component?.let(IdentityName::of)

    /** Writes the region's content for [input]. Calls the annotated function. */
    public abstract fun render(
        writer: HtmlWriter,
        input: Input,
    )

    override fun toString(): String = component?.let { "$it > $name" } ?: name.toString()
}

/** Generated descriptor of a region that appears once per page. */
public abstract class PageRegion<Input>(
    name: String,
    component: String? = null,
) : RegionDescriptor<Input>(name, component) {
    /** The target of this region on the page identified by [page]. */
    public fun target(page: PageIdentity): RegionTarget<Input> = page.target(this, key = null)
}

/** Generated descriptor of a region inside a repeated component, addressed by the component's key. */
public abstract class KeyedRegion<Key, Input>(
    name: String,
    component: String,
) : RegionDescriptor<Input>(name, component) {
    /** Converts the component key into the hashed identity key. */
    protected abstract fun identityKey(key: Key): IdentityKey

    /** The target of this region inside the component identified by [key]. */
    public fun target(
        page: PageIdentity,
        key: Key,
    ): RegionTarget<Input> = page.target(this, identityKey(key))
}

/**
 * One region on one page, together with the input type its content needs.
 *
 * Get it from a generated descriptor, for example `SummaryRegion.target(page)`.
 */
public class RegionTarget<Input> internal constructor(
    public val target: PatchTarget,
    private val region: RegionDescriptor<Input>,
) {
    /** Renders replacement content for this region. */
    public fun render(input: Input): PatchHtml = patchHtml { region.render(this, input) }

    internal fun write(
        writer: HtmlWriter,
        input: Input,
    ) {
        region.render(writer, input)
    }

    override fun toString(): String = "RegionTarget($region, target=$target)"
}

/**
 * Writes a region element with its content. Later patches replace the content of this element.
 *
 * Choose a normal HTML element and add ordinary attributes. Woge adds only the region and revision
 * data attributes that the browser runtime needs.
 */
public fun <Input> HtmlWriter.region(
    target: RegionTarget<Input>,
    input: Input,
    elementName: String = "div",
    revision: TargetRevision = TargetRevision.INITIAL,
    attributes: Attributes.() -> Unit = {},
) {
    element(
        elementName,
        attributes = {
            data("woge-region", target.target.region.value)
            data("woge-revision", revision.value.toString())
            attributes()
        },
    ) {
        target.write(this, input)
    }
}

/**
 * Declares a deferred region whose [content] loads the region's input.
 *
 * The region's own function renders the loaded input, so the content always matches the target.
 */
public fun <Input> deferredRegion(
    target: RegionTarget<Input>,
    initialRevision: TargetRevision = TargetRevision.INITIAL,
    loading: HtmlWriter.() -> Unit,
    onFailure: (DeferredRegionFailure) -> PatchHtml,
    content: suspend () -> Input,
): DeferredRegion = DeferredRegion(target.target, initialRevision, loading, onFailure) { target.render(content()) }
