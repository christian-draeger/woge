package dev.woge.host

/** Untrusted part metadata, available before temporary storage or file content is accepted. */
public data class UploadMetadata(
    public val field: String,
    public val filename: String,
    public val mediaType: String?,
)

/**
 * Explicit upload admission and request-owned cleanup. Authentication and CSRF still run at host ingress.
 * [temporaryFiles] must create a fresh store for each request; its close hook runs on every exit path.
 * [authorize] is additional part authorization, not a substitute for action/domain authorization.
 */
public class UploadPolicy(
    public val limits: UploadLimits = UploadLimits(),
    public val temporaryFiles: () -> UploadTemporaryFiles = { DirectoryUploadTemporaryFiles() },
    public val authorize: (UploadMetadata) -> Boolean = { true },
)
