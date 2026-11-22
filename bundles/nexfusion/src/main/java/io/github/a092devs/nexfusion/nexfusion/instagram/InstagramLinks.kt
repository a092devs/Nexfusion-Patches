package io.github.a092devs.nexfusion.nexfusion.instagram

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URI
import java.util.Locale

/**
 * Link sanitizer and external browser dispatcher ported from SysAdminDoc/HushGram (GPL-3.0).
 */
object InstagramLinks {
    private const val FIRST_PARTY_DOMAIN = "instagram.com"

    /** Query parameters of Instagram links that change what is opened. */
    private val functionalParameters = setOf("comment_id", "img_index", "open_comments", "story_media_id")

    /** Query parameters of links that only track the user or share context. */
    private val trackingParameters = setOf(
        "igsh", "igsi", "utm_source", "utm_medium", "utm_campaign",
        "utm_content", "utm_term", "fbclid", "si", "ref", "ref_src"
    )

    /**
     * Removes tracking parameters from external URLs and preserves only functional
     * parameters for first-party Instagram URLs.
     */
    fun sanitizeUrl(url: String): String = try {
        val uri = URI(url)
        val host = uri.host
        val rawQuery = uri.rawQuery
        if (host == null || rawQuery == null) {
            url
        } else {
            val normalizedHost = host.lowercase(Locale.ROOT)
            val firstParty = normalizedHost == FIRST_PARTY_DOMAIN || normalizedHost.endsWith(".$FIRST_PARTY_DOMAIN")
            val parameters = rawQuery.split('&').filter { parameter ->
                val name = parameter.substringBefore('=')
                when {
                    parameter.isEmpty() -> !firstParty
                    firstParty -> name in functionalParameters
                    else -> name !in trackingParameters
                }
            }
            val query = parameters.joinToString("&")
            if (query == rawQuery) url else replaceQuery(url, query, parameters.isNotEmpty())
        }
    } catch (_: Exception) {
        url
    }

    private fun replaceQuery(url: String, query: String, keepQueryDelimiter: Boolean): String {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url
        val fragmentStart = url.indexOf('#', queryStart)
        val fragment = if (fragmentStart < 0) "" else url.substring(fragmentStart)
        val base = url.substring(0, queryStart)
        return if (keepQueryDelimiter) "$base?$query$fragment" else "$base$fragment"
    }

    /**
     * The redirect hosts Instagram and Facebook wrap outbound links in, by exact host: a link in a
     * bio opens as `https://l.instagram.com/?u=<the page>&e=<a click token>`. Only these hosts are
     * unwrapped, so a URL that merely carries a `u` parameter of its own is opened as it is.
     */
    private val shimHosts =
        listOf("l.instagram.com", "l.facebook.com", "l.alpha.facebook.com", "lm.alpha.facebook.com")

    /** How many shims deep a link is followed before it is opened as it is. */
    private const val MAX_SHIMS = 4

    /**
     * The page [url] forwards to through every redirect shim it is wrapped in, or [url] itself
     * when it is no shim or forwards to anything but a web page.
     */
    fun unwrapShims(url: String): String {
        var target = url
        for (depth in 0 until MAX_SHIMS) {
            target = shimDestination(target) ?: break
        }
        return target
    }

    /**
     * The page [url] forwards to when it is one of [shimHosts], read from its `u` the way
     * Instagram reads it, or null when it is no shim or forwards to anything but a web page.
     */
    private fun shimDestination(url: String): String? {
        val uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            return null
        }
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (host !in shimHosts) return null
        val wrapped = uri.getQueryParameter("u") ?: return null
        val target = try {
            Uri.parse(wrapped)
        } catch (_: Exception) {
            return null
        }
        val scheme = target.scheme?.lowercase(Locale.ROOT)
        return if ((scheme == "http" || scheme == "https") && !target.host.isNullOrEmpty()) target.toString() else null
    }

    /**
     * Removes Meta's click id (fbclid) from [url], like HushGram does: the site it opens could
     * otherwise tell Meta that the link was followed. Nothing else in the link changes.
     */
    fun withoutClickId(url: String): String {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return url
        }
        val rawQuery = uri.rawQuery ?: return url
        val parts = rawQuery.split('&')
        val parameters = parts.filter { !it.substringBefore('=').equals("fbclid", ignoreCase = true) }
        return if (parameters.size == parts.size) url
        else replaceQuery(url, parameters.joinToString("&"), parameters.isNotEmpty())
    }

    /**
     * Sends [url] to the system browser: only an app that declares it opens web links takes it,
     * and the link needs a task of its own once the in-app browser closes.
     */
    fun openExternally(context: Context, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}

