package io.github.nexfusion.nexfusion.twitter

import io.github.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.nexfusion.compat.XposedHelpers
import io.github.nexfusion.HookDsl
import io.github.nexfusion.IHook
import io.github.nexfusion.IHookCallback
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.hookMethod
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Constructor
import java.lang.reflect.Method

// Shared runtime helpers for the X (Twitter) ports of kveld9/kveld-extra-morphe-patches (GPL-3.0).

/**
 * Tracker endpoints the telemetry patch redirects, transcribed from the upstream target URL sets
 * (Braze, Sentry, Google Ads/Crashlytics and Firebase Analytics).
 */
internal val BLOCKED_TRACKER_HOSTS = listOf(
    "sdk.iad-01.braze.com",
    "sondheim.braze.com",
    "o438986.ingest.sentry.io",
    "googleads.g.doubleclick.net",
    "pubads.g.doubleclick.net",
    "static.doubleclick.net",
    "firebase-settings.crashlytics.com",
    "app-measurement.com",
)

/** Where the upstream patch points blocked endpoints; connections fail fast against it. */
internal const val TELEMETRY_BLACKHOLE_URL = "http://0.0.0.0"

internal object TwitterTelemetryHosts {
    /** True when [value] (a URL or a bare host) targets one of the blocked tracker hosts. */
    fun isBlocked(value: String): Boolean {
        if (value.isEmpty()) return false
        val lower = value.lowercase()
        return BLOCKED_TRACKER_HOSTS.any { lower.contains(it) }
    }
}

/**
 * Scopes the Premium-gated download checks to the app's download entry points. Upstream forces the
 * result of the Premium check at the three download call sites; this port marks the same methods
 * (`com.x.media.n.b`, `com.x.urt.items.post.k5.invoke` and `com.x.video.tab.d0.e`) instead, so the
 * override only applies while a download surface is building.
 */
internal object TwitterDownloadGate {
    private val depth = ThreadLocal.withInitial { 0 }

    fun enterSite() {
        depth.set((depth.get() ?: 0) + 1)
    }

    fun exitSite() {
        depth.set(((depth.get() ?: 0) - 1).coerceAtLeast(0))
    }

    fun isInsideSite(): Boolean = (depth.get() ?: 0) > 0
}

/** Hooks [descriptor] through the DexKit member resolver, logging when the target is absent. */
internal fun IHook.hookCatching(descriptor: String, block: HookDsl<IHookCallback>.() -> Unit) {
    runCatching { DexMethod(descriptor).hookMethod(block) }
        .onFailure { PatcherLog.info(it) { "NexFusion: X hook target missing: $descriptor" } }
}

/** Hooks every constructor of [className]; returns the number of hooks installed. */
internal fun IHook.hookAllConstructorsIfExists(
    className: String,
    label: String,
    block: HookDsl<IHookCallback>.() -> Unit,
): Int {
    val cls = runCatching { classLoader.loadClass(className) }.getOrNull()
        ?: run {
            PatcherLog.info { "NexFusion: X class not found for $label: $className" }
            return 0
        }
    var hooked = 0
    for (ctor in cls.declaredConstructors) {
        runCatching { ctor.hookMethod(block) }.onSuccess { hooked++ }
    }
    if (hooked == 0) PatcherLog.info { "NexFusion: no constructors hooked for $label: $className" }
    return hooked
}

/** Hooks every method of [className] named [methodName] whose parameter types pass [shape]. */
internal fun IHook.hookMethodsIfExists(
    className: String,
    methodName: String,
    label: String,
    shape: (Array<Class<*>>) -> Boolean = { true },
    block: HookDsl<IHookCallback>.() -> Unit,
): Int {
    val cls = runCatching { classLoader.loadClass(className) }.getOrNull()
        ?: run {
            PatcherLog.info { "NexFusion: X class not found for $label: $className" }
            return 0
        }
    var hooked = 0
    for (method in cls.declaredMethods) {
        if (method.name != methodName || !shape(method.parameterTypes)) continue
        runCatching { method.hookMethod(block) }.onSuccess { hooked++ }
    }
    if (hooked == 0) PatcherLog.info { "NexFusion: no $methodName hook for $label in $className" }
    return hooked
}

/** Parameter types of the hooked member; empty when the member type is unexpected. */
internal val MethodHookParam.parameterTypes: Array<Class<*>>
    get() = when (val member = method) {
        is Method -> member.parameterTypes
        is Constructor<*> -> member.parameterTypes
        else -> emptyArray()
    }

private val appUnitLock = Any()
private var appUnitInstance: Any? = null
private var appUnitResolved = false

/**
 * kotlin.Unit.INSTANCE from the app's own dex, so suspend hooks return an instance the app's own
 * `kotlin.Unit` checks recognize; null when this build keeps the class elsewhere.
 */
internal fun IHook.appUnit(): Any? {
    synchronized(appUnitLock) {
        if (!appUnitResolved) {
            appUnitResolved = true
            appUnitInstance = runCatching {
                XposedHelpers.getStaticObjectField(classLoader.loadClass("kotlin.Unit"), "INSTANCE")
            }.onFailure {
                PatcherLog.info(it) { "NexFusion: kotlin.Unit not resolvable; suspend-result overrides skipped" }
            }.getOrNull()
        }
    }
    return appUnitInstance
}

