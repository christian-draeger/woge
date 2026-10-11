package dev.woge.example.project

/** Production policy shared by the reference hosts; no development origins or inline execution. */
public const val REFERENCE_CONTENT_SECURITY_POLICY: String =
    "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self'; " +
        "base-uri 'none'; form-action 'self'; object-src 'none'; frame-ancestors 'none'; " +
        "require-trusted-types-for 'script'; trusted-types woge"
