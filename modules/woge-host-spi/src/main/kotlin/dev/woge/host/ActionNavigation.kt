package dev.woge.host

import dev.woge.html.ApplicationUrl
import dev.woge.protocol.PatchStreamV1

public const val ACTION_NAVIGATION_HEADER: String = "Woge-Navigate"

/** Only explicit versioned action requests may translate a canonical 303 into a GET navigation. */
public fun PageResult.Redirect.enhancedActionNavigation(accept: String?): ApplicationUrl? =
    if (metadata.status == ResponseStatus.SEE_OTHER &&
        accept?.trim()?.equals(PatchStreamV1.MEDIA_TYPE, ignoreCase = true) == true
    ) {
        (location as? ApplicationRedirectLocation)?.url
    } else {
        null
    }
