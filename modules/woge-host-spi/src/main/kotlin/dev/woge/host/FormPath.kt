package dev.woge.host

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlin.reflect.KProperty1

/** A stable, nonnegative row key. Removing a row does not rename the remaining rows. */
@JvmInline
public value class FormRowKey private constructor(
    public val value: Int,
) {
    public companion object {
        public fun of(value: Int): FormRowKey {
            require(value >= 0) { "A form row key must be nonnegative" }
            return FormRowKey(value)
        }
    }
}

/** Serializer-checked nested property path. Use [field] for native control names and errors. */
public class FormPath<Command : Any, Value> internal constructor(
    public val name: String,
    internal val descriptor: SerialDescriptor,
) {
    public fun field(id: FormElementId): FormField<Command> = FormField(name, id)
}

public fun <Command : Any, Parent, Value> FormPath<Command, Parent>.child(
    property: KProperty1<Parent, Value>,
    serializedName: String = property.name,
): FormPath<Command, Value> = FormPath("$name.$serializedName", descriptor.formChild(serializedName))

@OptIn(ExperimentalSerializationApi::class)
public fun <Command : Any, Row> FormPath<Command, List<Row>>.row(key: FormRowKey): FormPath<Command, Row> {
    require(descriptor.kind == StructureKind.LIST) { "A row needs a list property" }
    return FormPath("$name[${key.value}]", descriptor.getElementDescriptor(0))
}

@OptIn(ExperimentalSerializationApi::class)
internal fun SerialDescriptor.formChild(name: String): SerialDescriptor {
    require(kind == StructureKind.CLASS) { "A child needs a class property" }
    val index = getElementIndex(name)
    require(index != CompositeDecoder.UNKNOWN_NAME) { "Form field '$name' is not present in the command serializer" }
    return getElementDescriptor(index)
}
