package io.github.a092devs.nexfusion.nexfusion.linkedin

// Ported from heyymichii/michii-patches (GPL-3.0),
// extensions/extension/src/main/java/app/linkedin/extension/OpenLinksDirectlyPatch.java.
// Plain Kotlin (no Android types) so it can be tested off device.

/**
 * Unwraps LinkedIn's external-link redirects:
 * `https://www.linkedin.com/safety/go/?url=<target>` shows a warning page before the target.
 */
internal object SafetyLink {

    /** Returns the target inside a `linkedin.com/safety/go` link, or null when there is none. */
    fun unwrapTarget(host: String?, path: String?, url: String?): String? {
        if (host == null || path == null) return null
        if (host != "linkedin.com" && !host.endsWith(".linkedin.com")) return null
        if (!path.startsWith("/safety/go")) return null
        if (url.isNullOrEmpty()) return null
        return url
    }

    fun isWebLink(scheme: String?): Boolean =
        scheme.equals("https", ignoreCase = true) || scheme.equals("http", ignoreCase = true)
}
