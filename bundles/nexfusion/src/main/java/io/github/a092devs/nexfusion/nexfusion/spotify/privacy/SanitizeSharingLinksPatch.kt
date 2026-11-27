package io.github.a092devs.nexfusion.nexfusion.spotify.privacy

import android.content.ClipData
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.scopedHook
import io.github.a092devs.nexfusion.nexfusion.extension.spotify.SanitizeSharingLinksPatch
import io.github.a092devs.nexfusion.nexfusion.spotify.formatAndroidShareSheetUrlMethod
import io.github.a092devs.nexfusion.nexfusion.spotify.shareCopyUrlMethod

val SanitizeSharingLinks = patch(
    name = "Sanitize sharing links",
    description = "Removes the tracking parameters (si, utm_source) from shared and copied links.",
    use = false,
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
