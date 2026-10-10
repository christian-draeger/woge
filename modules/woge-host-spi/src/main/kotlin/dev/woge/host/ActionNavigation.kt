package dev.woge.host

import dev.woge.html.ApplicationUrl

public const val ACTION_NAVIGATION_HEADER: String = "Woge-Navigate"

public fun acceptsActionPatches(accept: String?): Boolean =
    accept != null && ACTION_PATCH_MEDIA_TYPE.matches(accept.trim())

/** Only explicit versioned action requests may translate a canonical 303 into a GET navigation. */
public fun PageResult.Redirect.enhancedActionNavigation(accept: String?): ApplicationUrl? =
    if (metadata.status == ResponseStatus.SEE_OTHER &&
        acceptsActionPatches(accept)
    ) {
        (location as? ApplicationRedirectLocation)?.url
    } else {
        null
    }

private val ACTION_PATCH_MEDIA_TYPE =
    Regex("""application/vnd\.woge\.patch-stream[ \t]*;[ \t]*version[ \t]*=[ \t]*(?:1|"1")""", RegexOption.IGNORE_CASE)
