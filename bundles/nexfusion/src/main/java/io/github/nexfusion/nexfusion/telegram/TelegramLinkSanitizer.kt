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

    private data class PreviewRule(
        val host: String,
        val pathPrefix: List<String>?,
        val targetHost: String
    )

    private val PREVIEW_RULES = listOf(
        PreviewRule("twitter.com", listOf("*", "status"), "fixupx.com"),
        PreviewRule("mobile.twitter.com", listOf("*", "status"), "fixupx.com"),
        PreviewRule("x.com", listOf("*", "status"), "fixupx.com"),
        PreviewRule("instagram.com", listOf("p"), "ddinstagram.com"),
        PreviewRule("instagram.com", listOf("reel"), "ddinstagram.com"),
        PreviewRule("instagram.com", listOf("reels"), "ddinstagram.com"),
        PreviewRule("instagram.com", listOf("tv"), "ddinstagram.com"),
        PreviewRule("www.instagram.com", listOf("p"), "ddinstagram.com"),
        PreviewRule("www.instagram.com", listOf("reel"), "ddinstagram.com"),
        PreviewRule("www.instagram.com", listOf("reels"), "ddinstagram.com"),
        PreviewRule("www.instagram.com", listOf("tv"), "ddinstagram.com"),
        PreviewRule("tiktok.com", listOf("*", "video"), "vxtiktok.com"),
        PreviewRule("tiktok.com", listOf("*", "photo"), "vxtiktok.com"),
        PreviewRule("tiktok.com", listOf("t"), "vxtiktok.com"),
        PreviewRule("www.tiktok.com", listOf("*", "video"), "vxtiktok.com"),
        PreviewRule("www.tiktok.com", listOf("*", "photo"), "vxtiktok.com"),
        PreviewRule("www.tiktok.com", listOf("t"), "vxtiktok.com"),
        PreviewRule("vm.tiktok.com", null, "vm.vxtiktok.com"),
        PreviewRule("vt.tiktok.com", null, "vt.vxtiktok.com"),
        PreviewRule("reddit.com", listOf("r", "*", "comments"), "rxddit.com"),
        PreviewRule("reddit.com", listOf("r", "*", "s"), "rxddit.com"),
        PreviewRule("www.reddit.com", listOf("r", "*", "comments"), "rxddit.com"),
        PreviewRule("www.reddit.com", listOf("r", "*", "s"), "rxddit.com"),
        PreviewRule("threads.net", listOf("*", "post"), "vxthreads.net"),
        PreviewRule("www.threads.net", listOf("*", "post"), "vxthreads.net"),
        PreviewRule("pixiv.net", null, "phixiv.net"),
        PreviewRule("www.pixiv.net", null, "phixiv.net"),
    )

    fun rewritePreviewUrl(url: String): String {
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return url
        }
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val host = uri.host?.lowercase() ?: return url
        val segments = uri.pathSegments.orEmpty()
        for (rule in PREVIEW_RULES) {
            if (rule.host != host) continue
            val prefix = rule.pathPrefix
            if (prefix != null) {
                if (segments.size < prefix.size) continue
                var matches = true
                for (i in prefix.indices) {
                    if (prefix[i] != "*" && !prefix[i].equals(segments[i], ignoreCase = true)) {
                        matches = false
                        break
                    }
                }
                if (!matches) continue
            }
            return uri.buildUpon().authority(rule.targetHost).build().toString()
        }
        return url
    }

    fun formatNumber(number: Int): String {
        return String.format(java.util.Locale.US, "%,d", number)
    }
}
