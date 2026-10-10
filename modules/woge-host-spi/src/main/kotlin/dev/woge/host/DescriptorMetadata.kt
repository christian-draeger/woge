package dev.woge.host

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Compile-time structural metadata. It contains no instances, source bodies or rendered content. */
@Serializable
public data class DescriptorMetadata(
    public val kind: DescriptorKind,
    public val id: String,
    public val declaration: String,
    public val inputType: String? = null,
    public val path: String? = null,
    public val component: String? = null,
    public val keyType: String? = null,
)

@Serializable
public enum class DescriptorKind {
    @SerialName("page")
    PAGE,

    @SerialName("action")
    ACTION,

    @SerialName("component")
    COMPONENT,

    @SerialName("region")
    REGION,
}
