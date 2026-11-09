package io.github.nexfusion.nexfusion.twitter

import de.robv.android.xposed.XposedHelpers
import io.github.nexfusion.HookDsl
import io.github.nexfusion.IHook
import io.github.nexfusion.IHookCallback
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.hookMethod
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Method
import java.util.concurrent.locks.ReentrantLock

private val blackholeLock = ReentrantLock()
private var blackholeUrl: Any? = null
private var blackholeResolved = false

// Ported from kveld9/kveld-extra-morphe-patches (GPL-3.0), TwitterBlockTelemetryPatch.kt (the
// bytecode side). Upstream replaces the tracker URL string literals in place and drops the
// Firebase Performance getInstance null checks; this port filters the URLs at the network layer and
// stubs the same measurement and trace entry points at runtime. The upstream manifest purge
// (permission strips, component disables, Sentry metadata removal) has no runtime equivalent.

internal fun IHook.installTelemetryFilters() {
    installOkhttpFilters()
    installJavaUrlFilter()
    stubMeasurementAndTraces()
}

/** okhttp request building: rewrite every blocked URL, whatever form the caller passes. */
private fun IHook.installOkhttpFilters() {
    hookMethodsIfExists("okhttp3.Request\$Builder", "url", "okhttp request builder") {
        before = { param ->
            when (val value = param.args.getOrNull(0)) {
                is String -> {
                    if (TwitterTelemetryHosts.isBlocked(value)) param.args[0] = TELEMETRY_BLACKHOLE_URL
                }

                is java.net.URL -> {
                    if (TwitterTelemetryHosts.isBlocked(value.toString())) {
                        runCatching { param.args[0] = java.net.URL(TELEMETRY_BLACKHOLE_URL) }
                    }
                }

                else -> {
                    if (value != null) {
                        val host = runCatching { XposedHelpers.callMethod(value, "host") as? String }
                            .getOrNull()
                        if (host != null && TwitterTelemetryHosts.isBlocked(host)) {
                            val blackhole = blackholeHttpUrl()
                            if (blackhole != null) param.args[0] = blackhole
                        }
                    }
                }
            }
        }
    }

    // Parser-level filter so SDKs that cache an HttpUrl at initialization resolve the blackhole.
    val parserBlock: HookDsl<IHookCallback>.() -> Unit = {
        before = { param ->
            val value = param.args.getOrNull(0)
            if (value is String && TwitterTelemetryHosts.isBlocked(value)) {
                val blackhole = blackholeHttpUrl()
                if (blackhole != null) param.result = blackhole
            }
        }
    }
    for (className in listOf("okhttp3.HttpUrl", "okhttp3.HttpUrl\$Companion")) {
        for (methodName in listOf("get", "parse")) {
            hookMethodsIfExists(className, methodName, "okhttp url parser", block = parserBlock)
        }
    }
}

/** java.net.URL constructors, used by the Sentry and GMS SDKs directly. */
private fun IHook.installJavaUrlFilter() {
    var hooked = 0
    for (ctor in java.net.URL::class.java.declaredConstructors) {
        runCatching {
            ctor.hookMethod {
                before = { param ->
                    val types = param.parameterTypes
                    val index = when {
                        types.size == 1 && types[0] == String::class.java -> 0
                        types.size == 3 && types.all { it == String::class.java } -> 1 // (protocol, host, file)
                        else -> -1
                    }
                    if (index >= 0) {
                        val value = param.args.getOrNull(index) as? String
                        if (value != null && TwitterTelemetryHosts.isBlocked(value)) {
                            param.args[index] = if (index == 0) TELEMETRY_BLACKHOLE_URL else "0.0.0.0"
                        }
                    }
                }
            }
        }.onSuccess { hooked++ }
    }
    PatcherLog.info { "NexFusion: X java.net.URL filter on $hooked constructor(s)" }
}

/** The upstream getInstance guards leave the collectors inert; stubbing the entry points does the same. */
private fun IHook.stubMeasurementAndTraces() {
    stubVoidMethods("com.google.android.gms.measurement.AppMeasurement", "logEventInternal")
    stubVoidMethods(
        "com.google.firebase.perf.metrics.Trace",
        "start", "stop", "putAttribute", "removeAttribute", "putMetric", "incrementMetric",
    )
    stubVoidMethods(
        "com.google.firebase.analytics.FirebaseAnalytics",
        "logEvent", "setUserProperty", "setCurrentScreen", "setUserId", "setAnalyticsCollectionEnabled"
    )
    stubVoidMethods(
        "com.google.firebase.crashlytics.FirebaseCrashlytics",
        "recordException", "log", "setCustomKey", "setUserId", "setCustomKeys", "setCrashlyticsCollectionEnabled"
    )
    stubVoidMethods(
        "com.google.android.gms.analytics.GoogleAnalytics",
        "getInstance", "getLogger", "setLocalDispatchPeriod", "dispatchLocalHits"
    )
    stubVoidMethods(
        "com.google.android.gms.tagmanager.TagManager",
        "getInstance", "dispatch", "dispatchSynchronously"
    )
    stubVoidMethods(
        "com.google.android.gms.tagmanager.ContainerHolder",
        "refresh", "setContainerAvailable"
    )
}

private fun IHook.stubVoidMethods(className: String, vararg methodNames: String) {
    val cls = runCatching { classLoader.loadClass(className) }.getOrNull()
        ?: run {
            PatcherLog.info { "NexFusion: X telemetry class not found: $className" }
            return
        }
    var hooked = 0
    for (name in methodNames) {
        for (method in cls.declaredMethods) {
            if (method.name != name) continue
            runCatching {
                method.hookMethod {
                    before = { param -> param.result = null }
                }
            }.onSuccess { hooked++ }
        }
    }
    PatcherLog.info { "NexFusion: X $className telemetry stubs on $hooked method(s)" }
}

/** A `http://0.0.0.0` okhttp3.HttpUrl for the app's okhttp build, or null when unobtainable. */
internal fun IHook.blackholeHttpUrl(): Any? {
    synchronized(blackholeLock) {
        if (blackholeResolved) return blackholeUrl
        blackholeResolved = true
        val httpUrlClass = runCatching { classLoader.loadClass("okhttp3.HttpUrl") }.getOrNull()
        if (httpUrlClass == null) {
            PatcherLog.info { "NexFusion: okhttp3.HttpUrl not found; HttpUrl overrides skipped" }
            return null
        }
        for (methodName in listOf("get", "parse")) {
            blackholeUrl = runCatching {
                XposedHelpers.callStaticMethod(httpUrlClass, methodName, TELEMETRY_BLACKHOLE_URL)
            }.getOrNull()
            if (blackholeUrl != null) return blackholeUrl
        }
        val companion = runCatching {
            XposedHelpers.getStaticObjectField(httpUrlClass, "Companion")
        }.getOrNull()
        if (companion != null) {
            for (methodName in listOf("get", "parse")) {
                blackholeUrl = runCatching {
                    XposedHelpers.callMethod(companion, methodName, TELEMETRY_BLACKHOLE_URL)
                }.getOrNull()
                if (blackholeUrl != null) return blackholeUrl
            }
        }
        PatcherLog.info { "NexFusion: could not build the okhttp blackhole URL" }
        return null
    }
}