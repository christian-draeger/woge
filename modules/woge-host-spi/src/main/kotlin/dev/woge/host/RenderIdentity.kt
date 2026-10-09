package dev.woge.host

import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Secret used to derive opaque rendered IDs, so raw business keys never appear in the HTML.
 *
 * Use one key per deployment (all instances behind one load balancer share it). Load it from your
 * secret store; [random] is only suitable for a single process such as tests or local development.
 */
public class RenderIdentitySecret private constructor(
    private val bytes: ByteArray,
) {
    internal fun mac(): Mac = Mac.getInstance(HMAC_ALGORITHM).apply { init(SecretKeySpec(bytes, HMAC_ALGORITHM)) }

    override fun toString(): String = "RenderIdentitySecret(redacted)"

    public companion object {
        /** Wraps at least 32 secret bytes. The array is copied. */
        public fun of(bytes: ByteArray): RenderIdentitySecret {
            require(bytes.size >= MIN_SECRET_BYTES) { "Render identity secret needs at least $MIN_SECRET_BYTES bytes" }
            return RenderIdentitySecret(bytes.copyOf())
        }

        /** Creates a process-local random secret. IDs change when the process restarts. */
        public fun random(): RenderIdentitySecret =
            RenderIdentitySecret(ByteArray(MIN_SECRET_BYTES).also(SecureRandom()::nextBytes))
    }
}

/** Stable name of a component or region kind, such as `ProjectCard`. Never contains user data. */
@JvmInline
public value class IdentityName private constructor(
    public val value: String,
) {
    override fun toString(): String = value

    public companion object {
        /** Accepts a letter followed by up to 127 ASCII letters, digits, `_`, `-` or `.`. */
        public fun of(value: String): IdentityName {
            require(
                value.length in 1..MAX_NAME_LENGTH && value.first().isAsciiLetter() && value.all(::isNameCharacter),
            ) {
                "Identity name must start with an ASCII letter and contain only letters, digits, '_', '-' or '.'"
            }
            return IdentityName(value)
        }
    }
}

/**
 * Application key that tells repeated siblings apart, for example a project ID.
 *
 * Use a value that stays the same while the item exists, never a list position. The key is hashed
 * before it reaches the page and is never printed in diagnostics.
 */
public class IdentityKey private constructor(
    internal val canonical: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is IdentityKey && canonical.contentEquals(other.canonical)

    override fun hashCode(): Int = canonical.contentHashCode()

    override fun toString(): String = "IdentityKey(redacted)"

    public companion object {
        public fun of(value: String): IdentityKey {
            require(value.isNotEmpty()) { "Identity key must not be empty" }
            return IdentityKey(byteArrayOf(KEY_STRING) + value.toByteArray(StandardCharsets.UTF_8))
        }

        public fun of(value: Long): IdentityKey =
            IdentityKey(
                byteArrayOf(KEY_LONG) + ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array(),
            )

        public fun of(value: UUID): IdentityKey =
            IdentityKey(
                byteArrayOf(KEY_UUID) +
                    ByteBuffer
                        .allocate(UUID_BYTES)
                        .putLong(value.mostSignificantBits)
                        .putLong(value.leastSignificantBits)
                        .array(),
            )
    }
}

/** Raised while rendering when two siblings would get the same identity. */
public class DuplicateIdentityException internal constructor(
    message: String,
) : IllegalStateException(message)

/**
 * Root of all rendered identities for one page epoch.
 *
 * Create one per rendered document. The same epoch, secret and component path always produce the same
 * IDs, so a later request for that page can address a region again without server-side page state.
 */
public class PageIdentity(
    public val epoch: PageEpoch,
    secret: RenderIdentitySecret,
) {
    private val mac: Mac = secret.mac()
    private val issued: MutableSet<String> = mutableSetOf()

    /** Scope for top-level components and regions of the page. */
    public val root: IdentityScope = IdentityScope(this, parentId = ROOT_ID, path = "page")

    internal fun derive(
        parentId: String,
        kind: Char,
        slot: IdentityName,
        name: IdentityName,
        key: IdentityKey?,
    ): String {
        val input = ByteArrayOutputStream()
        input.field(PROTOCOL_VERSION.toByteArray(StandardCharsets.US_ASCII))
        input.field(epoch.value.toByteArray(StandardCharsets.UTF_8))
        input.field(parentId.toByteArray(StandardCharsets.US_ASCII))
        input.field(byteArrayOf(kind.code.toByte()))
        input.field(slot.value.toByteArray(StandardCharsets.US_ASCII))
        input.field(name.value.toByteArray(StandardCharsets.US_ASCII))
        input.field(key?.canonical ?: byteArrayOf())
        val digest = synchronized(mac) { mac.doFinal(input.toByteArray()) }
        val id = ID_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(digest.copyOf(ID_DIGEST_BYTES))
        if (!synchronized(issued) { issued.add(id) }) {
            throw DuplicateIdentityException(
                "Two different rendered instances produced the same ID; rendering stopped.",
            )
        }
        return id
    }
}

/**
 * One level of nesting. Components and regions created here are siblings.
 *
 * Every sibling must be unique by slot, name and key. A component or region that appears more than
 * once in the same slot needs an [IdentityKey]; a list position is never used.
 */
public class IdentityScope internal constructor(
    private val page: PageIdentity,
    private val parentId: String,
    private val path: String,
) {
    private val siblings: MutableSet<Sibling> = mutableSetOf()

    /** Opens a nested component instance whose children get their own scope. */
    public fun component(
        name: IdentityName,
        key: IdentityKey? = null,
        slot: IdentityName = DEFAULT_SLOT,
    ): RenderedComponent {
        val id = register(COMPONENT, slot, name, key)
        return RenderedComponent(IdentityScope(page, id, describe(name, slot, key)))
    }

    /** Returns the patch target for a region at this level. */
    public fun region(
        name: IdentityName,
        key: IdentityKey? = null,
        slot: IdentityName = DEFAULT_SLOT,
    ): PatchTarget = PatchTarget(page.epoch, RegionTargetId.of(register(REGION, slot, name, key)))

    private fun register(
        kind: Char,
        slot: IdentityName,
        name: IdentityName,
        key: IdentityKey?,
    ): String {
        val added = synchronized(siblings) { siblings.add(Sibling(kind, slot, name, key)) }
        if (!added) {
            val location = describe(name, slot, key)
            throw DuplicateIdentityException(
                if (key == null) {
                    "$location is rendered more than once. " +
                        "Pass a stable IdentityKey (for example the item ID) to each repeated sibling."
                } else {
                    "$location uses the same IdentityKey twice. Keys must be unique among siblings in one slot."
                },
            )
        }
        return page.derive(parentId, kind, slot, name, key)
    }

    private fun describe(
        name: IdentityName,
        slot: IdentityName,
        key: IdentityKey?,
    ): String =
        buildString {
            append(path).append(" > ")
            if (slot != DEFAULT_SLOT) append(slot.value).append(':')
            append(name.value)
            if (key != null) append("[key]")
        }

    private data class Sibling(
        val kind: Char,
        val slot: IdentityName,
        val name: IdentityName,
        val key: IdentityKey?,
    )
}

/** A rendered component instance. Use [children] for the components and regions inside it. */
public class RenderedComponent internal constructor(
    public val children: IdentityScope,
)

private fun ByteArrayOutputStream.field(bytes: ByteArray) {
    write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
    write(bytes)
}

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

private fun isNameCharacter(character: Char): Boolean =
    character.isAsciiLetter() || character in '0'..'9' || character == '_' || character == '-' || character == '.'

private val DEFAULT_SLOT: IdentityName = IdentityName.of("default")
private const val HMAC_ALGORITHM: String = "HmacSHA256"
private const val MIN_SECRET_BYTES: Int = 32
private const val MAX_NAME_LENGTH: Int = 128
private const val PROTOCOL_VERSION: String = "woge-identity-v1"
private const val ROOT_ID: String = "root"
private const val ID_PREFIX: String = "w1"
private const val ID_DIGEST_BYTES: Int = 18
private const val UUID_BYTES: Int = 16
private const val KEY_STRING: Byte = 1
private const val KEY_LONG: Byte = 2
private const val KEY_UUID: Byte = 3
private const val COMPONENT: Char = 'c'
private const val REGION: Char = 'r'
