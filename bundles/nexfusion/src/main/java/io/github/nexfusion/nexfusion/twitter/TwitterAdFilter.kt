package io.github.nexfusion.nexfusion.twitter

import io.github.nexfusion.compat.XposedHelpers
import io.github.nexfusion.PatcherLog

// Ported from kveld9/kveld-extra-morphe-patches (GPL-3.0), TwitterRemoveAdsPatch.kt.
// Upstream prepends the same promoted-item test to the timeline item presenter and the post detail
// presenter and returns the empty presenter for suppressed items. This port runs that test as a
// before-hook on X 12.35's unified presenter funnel (com.x.urt.b.a) and returns the empty presenter
// singleton when it matches.

/** One promoted-item probe, mirroring a branch of the upstream adItemTestSmali test. */
internal data class AdProbe(val modelClass: String, val field: String, val call: String? = null)

/** The promoted shapes the Remove Ads preamble tests across X versions. */
internal val PROMOTED_ITEM_PROBES = listOf(
    // X 12.38+
    AdProbe("com.x.models.timelines.items.k1", "e"), // promoted post marker
    AdProbe("com.x.models.timelines.items.q1", "j"), // promoted rtb marker
    AdProbe("com.x.models.timelines.items.e2", "h"), // promoted item variant
    AdProbe("com.x.models.timelines.items.z1", "a", call = "b"), // promoted trend verdict
    AdProbe("com.x.models.timelines.items.a2", "h"), // promoted user marker
    // X 12.37
    AdProbe("com.x.models.timelines.items.l1", "e"), // promoted post marker
    AdProbe("com.x.models.timelines.items.r1", "j"), // promoted rtb marker
    AdProbe("com.x.models.timelines.items.f2", "h"), // promoted item variant
    // X 12.35 - 12.36
    AdProbe("com.x.models.timelines.items.j1", "e"), // legacy promoted post marker
    AdProbe("com.x.models.timelines.items.p1", "j"), // legacy promoted rtb marker
    // Shared across versions
    AdProbe("com.x.models.timelines.items.m0", "a", call = "a"), // event item verdict
    AdProbe("com.x.models.timelines.items.y1", "a", call = "b"), // promoted trend verdict
    AdProbe("com.x.models.timelines.items.b2", "h"), // promoted user marker
)

/** Entry-id substring the upstream test checks after the model-shape probes. */
internal const val PROMOTED_ENTRY_ID = "promoted"

/** X no-op presenter singletons across versions (implements com.x.presenter.a, renders nothing). */
private val EMPTY_PRESENTER_CLASSES = listOf(
    "com.x.urt.g0", // X 12.38+
    "com.x.urt.b0", // X 12.37
    "com.x.urt.f0", // X 12.35 - 12.36
)

internal class TwitterAdFilter(private val classLoader: ClassLoader) {
    private val models = HashMap<String, Class<*>?>()
    private val warnedReads = HashSet<String>()
    private var emptyPresenter: Any? = null
    private var emptyPresenterResolved = false

    @Synchronized
    private fun model(name: String): Class<*>? {
        if (models.containsKey(name)) return models[name]
        return XposedHelpers.findClassIfExists(name, classLoader).also { models[name] = it }
    }

    /** True when the upstream Remove Ads test would suppress this item. */
    fun isPromoted(item: Any?): Boolean {
        if (item == null) return false
        for (probe in PROMOTED_ITEM_PROBES) {
            val type = model(probe.modelClass) ?: continue
            if (!type.isInstance(item)) continue
            if (readMarker(item, probe) != null) return true
        }
        val entryId = runCatching { XposedHelpers.callMethod(item, "c") as? String }.getOrNull()
        return entryId?.contains(PROMOTED_ENTRY_ID) == true
    }

    private fun readMarker(item: Any, probe: AdProbe): Any? {
        val holder = runCatching { XposedHelpers.getObjectField(item, probe.field) }
            .onFailure {
                synchronized(warnedReads) {
                    if (warnedReads.add("${probe.modelClass}.${probe.field}")) {
                        PatcherLog.info(it) {
                            "NexFusion: X promoted-item probe read failed: ${probe.modelClass}.${probe.field}"
                        }
                    }
                }
            }
            .getOrNull() ?: return null
        if (probe.call == null) return holder
        return runCatching { XposedHelpers.callMethod(holder, probe.call) }.getOrNull()
    }

    /** The empty presenter singleton; null when this build renames it (items then stay visible). */
    @Synchronized
    fun emptyPresenter(): Any? {
        if (!emptyPresenterResolved) {
            emptyPresenterResolved = true
            val presenterInterface = runCatching { classLoader.loadClass("com.x.presenter.a") }.getOrNull()
            for (clsName in EMPTY_PRESENTER_CLASSES) {
                emptyPresenter = runCatching {
                    val cls = classLoader.loadClass(clsName)
                    if (presenterInterface != null && !presenterInterface.isAssignableFrom(cls)) {
                        return@runCatching null
                    }
                    XposedHelpers.getStaticObjectField(cls, "a")
                }.getOrNull()
                if (emptyPresenter != null) {
                    PatcherLog.info { "NexFusion: resolved X empty presenter via $clsName.a" }
                    break
                }
            }
            if (emptyPresenter == null && presenterInterface != null) {
                // Dynamic fallback across common urt presenter suffixes
                for (name in listOf("g0", "b0", "f0", "h0", "i0", "j0", "k0", "l0", "m0", "n0", "a0", "c0", "d0", "e0")) {
                    emptyPresenter = runCatching {
                        val cls = classLoader.loadClass("com.x.urt.$name")
                        if (presenterInterface.isAssignableFrom(cls)) {
                            XposedHelpers.getStaticObjectField(cls, "a")
                        } else null
                    }.getOrNull()
                    if (emptyPresenter != null) {
                        PatcherLog.info { "NexFusion: dynamically resolved X empty presenter via com.x.urt.$name.a" }
                        break
                    }
                }
            }
            if (emptyPresenter == null) {
                PatcherLog.info { "NexFusion: X empty presenter not found; suppressed items stay visible" }
            }
        }
        return emptyPresenter
    }

    /** Probe classes missing from this build, for one install-time log line. */
    @Synchronized
    fun missingProbeClasses(): List<String> {
        val present = PROMOTED_ITEM_PROBES.filter { model(it.modelClass) != null }
        // If at least one active probe set is matched, we're good
        if (present.isNotEmpty()) return emptyList()
        return PROMOTED_ITEM_PROBES.map { it.modelClass }.distinct()
    }
}

