package io.github.a092devs.nexfusion.nexfusion.cloudflare

import io.github.a092devs.nexfusion.compat.XC_MethodHook
import io.github.a092devs.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.a092devs.nexfusion.compat.XposedBridge
import io.github.a092devs.nexfusion.patch
import java.lang.reflect.Modifier

// Ported from rushiranpise/morphe-patches (GPL-3.0):
// - Spoof WARP+ Unlimited UI via AccountData constructor and field interception
// - Disable telemetry via AnalyticsService and Firebase dispatch suppression
// - Disable SSL pinning via OkHttp CertificatePinner bypass

val CloudflareWarpUnlockWarpPlus = patch(
    name = "Spoof WARP+ Unlimited UI",
    description = "Forces WarpPlusState to UNLIMITED on every AccountData instance in the 1.1.1.1 user interface.",
    use = false,
) {
    runCatching {
        val accountDataClass = classLoader.loadClass("com.cloudflare.app.data.warpapi.AccountData")
        val warpPlusStateClass = classLoader.loadClass("com.cloudflare.app.data.warpapi.WarpPlusState")
        val unlimitedEnum = java.lang.Enum.valueOf(warpPlusStateClass.asSubclass(Enum::class.java), "UNLIMITED")

        accountDataClass.declaredConstructors.forEach { constructor ->
            XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    for (i in param.args.indices) {
                        val arg = param.args[i]
                        if (arg != null && warpPlusStateClass.isInstance(arg)) {
                            param.args[i] = unlimitedEnum
                        }
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    accountDataClass.declaredFields.filter { it.type == warpPlusStateClass }.forEach { field ->
                        field.isAccessible = true
                        field.set(param.thisObject, unlimitedEnum)
                    }
                }
            })
        }

        accountDataClass.declaredMethods.filter { it.returnType == warpPlusStateClass }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = unlimitedEnum
                }
            })
        }
    }
}

val CloudflareWarpDisableTelemetry = patch(
    name = "Disable telemetry",
    description = "Disables Cloudflare analytics and Firebase telemetry event dispatchers.",
    use = false,
) {
    // 1. Hook AnalyticsService static event dispatcher
    runCatching {
        val analyticsServiceClass = classLoader.loadClass("com.cloudflare.app.domain.analytics.AnalyticsService")
        analyticsServiceClass.declaredMethods.filter {
            Modifier.isStatic(it.modifiers) &&
            it.parameterTypes.size == 2 &&
            it.parameterTypes[0] == analyticsServiceClass &&
            it.parameterTypes[1].name == "android.os.Bundle"
        }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }

    // 2. Hook FirebaseAnalytics logEvent
    runCatching {
        val firebaseAnalyticsClass = classLoader.loadClass("com.google.firebase.analytics.FirebaseAnalytics")
        XposedBridge.hookAllMethods(firebaseAnalyticsClass, "logEvent", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = null
            }
        })
    }
}

val CloudflareWarpDisableSslPinning = patch(
    name = "Disable SSL pinning",
    description = "Bypasses OkHttp certificate pinning on Cloudflare API calls to allow TLS traffic inspection.",
    use = false,
) {
    runCatching {
        val certPinnerClass = classLoader.loadClass("okhttp3.CertificatePinner")
        certPinnerClass.declaredMethods.filter {
            it.name == "check" &&
            it.parameterTypes.size == 2 &&
            it.parameterTypes[0] == String::class.java &&
            java.util.List::class.java.isAssignableFrom(it.parameterTypes[1])
        }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }
}

val CloudflareWarpPatches = arrayOf(
    CloudflareWarpUnlockWarpPlus,
    CloudflareWarpDisableTelemetry,
    CloudflareWarpDisableSslPinning,
)
