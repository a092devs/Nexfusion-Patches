package io.github.nexfusion.nexfusion.telegram

import android.net.Uri

/**
 * Sanitizes URLs opened or shared in Telegram by stripping marketing and analytics tracking parameters.
 */
object TelegramLinkSanitizer {

    private val TRACKING_PARAMS = setOf(
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "utm_id",
        "utm_name",
        "gclid",
        "fbclid",
        "igshid",
        "si",
        "mc_eid",
        "yclid",
        "msclkid",
        "dclid",
        "twclid"
    )

    fun sanitize(url: String): String {
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return url
        }

        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val queryNames = runCatching { uri.queryParameterNames }.getOrNull()
        if (queryNames.isNullOrEmpty()) return url

        val hasTracking = queryNames.any { it.lowercase() in TRACKING_PARAMS }
        if (!hasTracking) return url

        val builder = uri.buildUpon().clearQuery()
        for (name in queryNames) {
            if (name.lowercase() !in TRACKING_PARAMS) {
                for (value in uri.getQueryParameters(name)) {
                    builder.appendQueryParameter(name, value)
                }
            }
        }

        return builder.build().toString()
    }

    private val PREVIEW_REDIRECT_HOSTS = mapOf(
        "twitter.com" to "fxtwitter.com",
        "mobile.twitter.com" to "fxtwitter.com",
        "x.com" to "fixupx.com",
        "instagram.com" to "ddinstagram.com",
        "www.instagram.com" to "ddinstagram.com",
        "tiktok.com" to "vxtiktok.com",
        "www.tiktok.com" to "vxtiktok.com",
        "reddit.com" to "rxddit.com",
        "www.reddit.com" to "rxddit.com",
        "threads.net" to "vxthreads.net",
        "www.threads.net" to "vxthreads.net"
    )

    fun rewritePreviewUrl(url: String): String {
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return url
        }
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val host = uri.host?.lowercase() ?: return url
        val redirectHost = PREVIEW_REDIRECT_HOSTS[host] ?: return url
        return uri.buildUpon().authority(redirectHost).build().toString()
    }

    fun formatNumber(number: Int): String {
        return String.format(java.util.Locale.US, "%,d", number)
    }
}
