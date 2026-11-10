package io.github.nexfusion.nexfusion.spotify.privacy

import android.content.ClipData
import io.github.nexfusion.hookMethod
import io.github.nexfusion.patch
import io.github.nexfusion.scopedHook
import io.github.nexfusion.nexfusion.extension.spotify.SanitizeSharingLinksPatch
import io.github.nexfusion.nexfusion.spotify.formatAndroidShareSheetUrlMethod
import io.github.nexfusion.nexfusion.spotify.shareCopyUrlMethod

val SanitizeSharingLinks = patch(
    name = "Sanitize sharing links",
    description = "Removes the tracking parameters (si, utm_source) from shared and copied links.",
) {
    // "Copy link": sanitize the clip created while copying.
    val newPlainText = ClipData::class.java.getMethod(
        "newPlainText", CharSequence::class.java, CharSequence::class.java
    )
    ::shareCopyUrlMethod.hookMethod(scopedHook(newPlainText) {
        before { param ->
            (param.args[1] as? CharSequence)?.let { param.args[1] = SanitizeSharingLinksPatch.sanitizeText(it) }
        }
    })

    // Share sheet: sanitize the url parameter of the share message formatter.
    ::formatAndroidShareSheetUrlMethod.hookMethod {
        before { param ->
            (param.args[1] as? String)?.let { param.args[1] = SanitizeSharingLinksPatch.sanitizeUrl(it) }
        }
    }
}
