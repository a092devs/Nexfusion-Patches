package io.github.a092devs.nexfusion.nexfusion.linkedin

// Ported from heyymichii/michii-patches (GPL-3.0),
// extensions/extension/src/main/java/app/linkedin/extension/LinkCleaner.java.
// Plain Kotlin (no Android types) so it can be tested off device.

/**
 * Removes tracking parameters from LinkedIn links inside shared or copied text.
 */
internal object LinkCleaner {
    private val URL = Regex("""https?://[^\s"'<>]+""")

    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term", "utm_id",
        "trk", "trkinfo", "trkemail", "rcm", "lipi", "trackingid", "refid", "midtoken", "midsig",
        "eid", "li_fat_id", "originalsubdomain", "sharetype", "src",
    )

    /** Returns [text] with every LinkedIn URL cleaned, or the same instance when nothing changed. */
    fun clean(text: String?): String? {
        if (text == null || !text.contains("http")) return text
        val out = StringBuilder(text.length)
        var last = 0
        var changed = false
        for (match in URL.findAll(text)) {
            val url = match.value
            val cleaned = cleanUrl(url)
            if (cleaned == url) continue
            changed = true
            out.append(text, last, match.range.first).append(cleaned)
            last = match.range.last + 1
        }
        if (!changed) return text
        out.append(text, last, text.length)
        return out.toString()
    }

    /** Removes tracking parameters from [url]; returns it unchanged when it is not a LinkedIn URL. */
    fun cleanUrl(url: String): String {
        val hostStart = url.indexOf("://") + 3
        val pathStart = indexOfAny(url, hostStart, '/', '?', '#')
        val host = url.substring(hostStart, if (pathStart < 0) url.length else pathStart).lowercase()
        if (host != "linkedin.com" && !host.endsWith(".linkedin.com") && host != "lnkd.in") return url

        val query = url.indexOf('?', hostStart)
        if (query < 0) return url
        val fragment = url.indexOf('#', query)
        val base = url.substring(0, query)
        val params = url.substring(query + 1, if (fragment < 0) url.length else fragment)
        val tail = if (fragment < 0) "" else url.substring(fragment)

        val kept = StringBuilder()
        for (param in params.split("&")) {
            if (param.isEmpty()) continue
            val eq = param.indexOf('=')
            val key = (if (eq < 0) param else param.substring(0, eq)).lowercase()
            if (key in TRACKING_PARAMS) continue
            if (kept.isNotEmpty()) kept.append('&')
            kept.append(param)
        }
        return if (kept.isEmpty()) base + tail else "$base?$kept$tail"
    }

    private fun indexOfAny(text: String, from: Int, vararg chars: Char): Int {
        for (i in from until text.length) {
            for (c in chars) {
                if (text[i] == c) return i
            }
        }
        return -1
    }
}
