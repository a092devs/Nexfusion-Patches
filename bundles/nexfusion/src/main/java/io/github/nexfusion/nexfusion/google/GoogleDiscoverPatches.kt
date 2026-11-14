package io.github.nexfusion.nexfusion.google

import io.github.nexfusion.PatcherLog
import io.github.nexfusion.hookMethod
import io.github.nexfusion.patch
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// Ported from gnadgnaoh/SexAlloy (GPL-3.0) and hxreborn/discover-ads-filter (GPL-3.0).
// Filters promoted ad cards from the Google Discover feed.

val FilterDiscoverAds = patch(
    name = "Filter Discover ads",
    description = "Filters promoted ad cards from the Google Discover feed.",
) {
    ::streamRenderableListFingerprint.hookMethod {
        after { param ->
            val items = param.result as? List<*> ?: return@after
            if (items.isEmpty()) return@after

            val fp = DiscoverAdFilter.fastFingerprint(items)
            if (fp == DiscoverAdFilter.lastFingerprint) {
                DiscoverAdFilter.lastFilteredSnapshot?.let { param.result = it }
                return@after
            }

            var removed = 0
            val filtered = ArrayList<Any?>(items.size)
            for (item in items) {
                val key = item?.let(DiscoverAdFilter::stableItemKey)
                if (key != null && DiscoverAdFilter.isAdItem(key)) {
                    removed++
                    PatcherLog.info { "NexFusion: Discover: blocked ad key=$key" }
                } else {
                    filtered += item
                }
            }

            DiscoverAdFilter.lastFingerprint = fp

            if (removed == 0) {
                DiscoverAdFilter.lastFilteredSnapshot = null
            } else {
                PatcherLog.info { "NexFusion: Discover: removed $removed ad(s) from ${items.size} items" }
                DiscoverAdFilter.lastFilteredSnapshot = filtered
                param.result = filtered
            }
        }
    }
}

val GoogleDiscoverPatches = arrayOf(
    FilterDiscoverAds,
)

internal object DiscoverAdFilter {
    private val adClusterTokens = setOf("feedads")

    private val decisionCache = ConcurrentHashMap<String, Boolean>()
    private val instanceFieldsCache = ConcurrentHashMap<Class<*>, List<Field>>()
    private val nonSliceClasses = ConcurrentHashMap.newKeySet<Class<*>>()

    @Volatile
    var lastFingerprint: Long = Long.MIN_VALUE

    @Volatile
    var lastFilteredSnapshot: List<Any?>? = null

    fun isAdItem(key: String): Boolean = decisionCache.getOrPut(key) {
        val lower = key.lowercase(Locale.ROOT)
        adClusterTokens.any { it in lower }
    }

    fun stableItemKey(item: Any): String? {
        val cls = item.javaClass
        if (cls in nonSliceClasses) return null

        for (f in instanceFieldsCache.getOrPut(cls) { instanceFieldsOf(cls) }) {
            val value = runCatching { f.get(item) as? String }.getOrNull()
            if (!value.isNullOrBlank()) return "${cls.simpleName}#$value"
        }

        nonSliceClasses.add(cls)
        return null
    }

    fun fastFingerprint(items: List<*>): Long {
        var hash = items.size.toLong()
        val step = (items.size / 3).coerceAtLeast(1)
        var i = 0
        var n = 0
        while (i < items.size && n < 4) {
            hash = hash * 31L + System.identityHashCode(items[i]).toLong()
            i += step
            n++
        }
        return hash
    }

    private fun instanceFieldsOf(cls: Class<*>): List<Field> =
        generateSequence(cls) { it.superclass }
            .takeWhile { it != Any::class.java }
            .flatMap { it.declaredFields.asSequence() }
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .filter { runCatching { it.isAccessible = true }.isSuccess }
            .toList()
}

