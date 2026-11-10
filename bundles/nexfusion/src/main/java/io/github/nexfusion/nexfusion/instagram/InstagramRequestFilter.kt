package io.github.nexfusion.nexfusion.instagram

import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.PatcherLog
import java.io.IOException
import java.lang.reflect.Field
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Tigon and network request interceptor for Instagram API endpoints.
 * Ported and adapted from SysAdminDoc/HushGram and Piko (GPL-3.0).
 */
object InstagramRequestFilter {
    enum class Rule {
        ADS,
        SUGGESTED,
        REELS_ADS,
        STORY_SEEN,
        LIVE_VIEWER,
        ANALYTICS,
        RECENT_SEARCHES,
        REEL_WATCH_HISTORY,
        META_AI,
    }

    private val enabledRules: MutableSet<Rule> = ConcurrentHashMap.newKeySet()
    @Volatile
    private var hooksInstalled = false

    fun enable(rule: Rule) {
        enabledRules.add(rule)
    }

    fun isEnabled(rule: Rule): Boolean = rule in enabledRules

    fun ruleFor(uri: URI): Rule? {
        val host = uri.host ?: ""
        val path = uri.path ?: return null
        return when {
            host.contains("graph.instagram.com") ||
                host.contains("graph.facebook.com") ||
                path.contains("/logging_client_events") -> Rule.ANALYTICS

            path.contains("/api/v2/media/seen/") -> Rule.STORY_SEEN

            path.contains("/heartbeat_and_get_viewer_count/") -> Rule.LIVE_VIEWER

            path.contains("/feed/injected_reels_media/") -> Rule.REELS_ADS

            path.contains("/async_ads/") ||
                path.contains("/api/v1/ads/graphql/") ||
                path.contains("profile_ads/get_profile_ads/") -> Rule.ADS

            path.contains("/discover/ayml") ||
                path.contains("/discover/chaining") ||
                path.contains("/suggested_users") ||
                path.contains("/discover/suggested_businesses") -> Rule.SUGGESTED

            path.contains("/fbsearch/register_recent_search_click/") -> Rule.RECENT_SEARCHES

            path.contains("/clips/write_seen_state/") -> Rule.REEL_WATCH_HISTORY

            path.contains("/meta_ai") ||
                path.contains("/genai") ||
                path.contains("/aistudio") ||
                path.contains("/muse") ||
                path.contains("/hatch") -> Rule.META_AI

            else -> null
        }
    }

    fun shouldBlock(uri: URI): Boolean {
        if (enabledRules.isEmpty()) return false
        val rule = ruleFor(uri) ?: return false
        return rule in enabledRules
    }

    fun blockedUri(args: Array<Any?>): URI? = uriSlots(args).firstOrNull()?.get()?.takeIf(::shouldBlock)

    fun redirect(args: Array<Any?>) {
        uriSlots(args).firstOrNull()?.let { slot ->
            slot.get()?.let { slot.set(unreachable(it)) }
        }
    }

    private class UriSlot(val get: () -> URI?, val set: (URI) -> Unit)

    private fun uriSlots(args: Array<Any?>): Sequence<UriSlot> = args.indices.asSequence().flatMap { index ->
        when (val arg = args[index]) {
            null -> emptySequence()
            is URI -> sequenceOf(UriSlot({ args[index] as? URI }, { args[index] = it }))
            else -> uriFields(arg.javaClass).asSequence()
                .filter { it.get(arg) != null }
                .map { field -> UriSlot({ field.get(arg) as? URI }, { field.set(arg, it) }) }
        }
    }

    private fun unreachable(uri: URI) = URI("https", null, "127.0.0.1", 9, uri.path, null, null)

    private val uriFields = ConcurrentHashMap<Class<*>, Array<Field>>()

    private fun uriFields(clazz: Class<*>) = uriFields.getOrPut(clazz) {
        generateSequence(clazz) { it.superclass }
            .takeWhile { it != Any::class.java }
            .flatMap { it.declaredFields.asSequence() }
            .filter { it.type == URI::class.java }
            .onEach { it.isAccessible = true }
            .toList().toTypedArray()
    }

    fun ensureHooks(classLoader: ClassLoader) {
        if (hooksInstalled) return
        synchronized(this) {
            if (hooksInstalled) return
            hooksInstalled = true
            runCatching {
                val tigonClass = classLoader.loadClass("com.instagram.api.tigon.TigonServiceLayer")
                var count = 0
                tigonClass.declaredMethods.filter { it.name == "startRequest" }.forEach { method ->
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val uri = blockedUri(param.args) ?: return
                            PatcherLog.info { "NexFusion Instagram: Blocked request: $uri" }
                            redirect(param.args)
                        }
                    })
                    count++
                }
                PatcherLog.info { "NexFusion Instagram: Hooked $count TigonServiceLayer methods" }
            }.onFailure {
                PatcherLog.info(it) { "NexFusion Instagram: TigonServiceLayer not loaded or not hookable" }
            }
        }
    }
}


