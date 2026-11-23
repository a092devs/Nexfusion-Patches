package io.github.a092devs.nexfusion.nexfusion.reddit

import io.github.a092devs.nexfusion.PatcherLog
import io.github.a092devs.nexfusion.patch

// Ported from MorpheApp/morphe-patches (GPL-3.0),
// patches/src/main/kotlin/app/morphe/patches/reddit/misc/privacy/SanitizeSharingLinksPatch.kt.
//
// The formatter takes the base URL and the map of tracking query parameters Reddit wants to
// append. Returning the first argument gives the caller the same URL without that map, matching
// Morphe's early return when its setting is enabled.
val SanitizeSharingLinks = patch(
    name = "Sanitize Reddit sharing links",
    description = "Removes tracking query parameters from shared links.",
    use = false,
) {
    runCatching {
        ShareLinkFormatterFingerprint.hookMethod {
            before { param ->
                param.result = param.args[0]
            }
        }
    }.onFailure {
        PatcherLog.info(it) { "NexFusion: Reddit share-link formatter anchor not found" }
    }
}
