package io.github.nexfusion.nexfusion.discord

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedHelpers
import io.github.nexfusion.IHook
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Method

// Ported from VenusIsJaded/Venus-Patches (GPL-3.0), patches/src/main/kotlin/app/venus/patches/PrivacyPatches.kt.
// See PrivacyTargets.kt for the tables and NOTES on the two obfuscated upstream targets that are not
// ported. Venus patches the same entry points at build time; this bundle installs runtime hooks that
// replace the method bodies with the same results.

val DisableCrashReporting = patch(
    name = "Disable crash reporting",
    description = "Stops crash reports, screenshots and system logs from being sent to Sentry. Always on once patched.",
) {
    installPrivacyStubs(PrivacyTargets.crash)
}

val DisableTelemetry = patch(
    name = "Disable telemetry and touch logging",
    description = "Stops performance tracking, per-screen smoothness stats and records of what you tap. Always on once patched.",
) {
    installPrivacyStubs(PrivacyTargets.telemetry)
}

val DisableInstallAttribution = patch(
    name = "Disable install attribution",
    description = "Stops AppsFlyer from tracking how you installed the app. Some invite links that open before install may not work.",
) {
    installPrivacyStubs(PrivacyTargets.attribution)
}

val DisableAdvertisingIdentifiers = patch(
    name = "Disable advertising identifiers",
    description = "Stops Discord and AppsFlyer from reading your Google advertising ID. Always on once patched.",
) {
    installPrivacyStubs(PrivacyTargets.advertising)
}

val KeepBluetoothAudioClear = patch(
    name = "Keep Bluetooth audio clear in calls",
    description = "Prevents Discord from forcing Bluetooth SCO mode during calls, keeping high-quality music/audio profile.",
    use = false,
) {
    for (className in listOf("com.discord.audio.DiscordAudioManager", "com.discord.audio.DiscordAudioManager2")) {
        val cls = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
        for (m in cls.declaredMethods) {
            if (m.name == "setCommunicationModeOn") {
                runCatching {
                    de.robv.android.xposed.XposedBridge.hookMethod(m, de.robv.android.xposed.XC_MethodReplacement.DO_NOTHING)
                }
            }
        }
    }
}

val DiscordPatches = arrayOf(
    DisableCrashReporting,
    DisableTelemetry,
    DisableInstallAttribution,
    DisableAdvertisingIdentifiers,
    UsePhoneFont,
    KeepBluetoothAudioClear,
)

private fun IHook.installPrivacyStubs(targets: List<PrivacyTarget>) {
    for (target in targets) {
        try {
            DexMethod(target.descriptor).hookMethod(object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    applyPrivacyResult(param, target.result)
                }
            })
        } catch (t: Throwable) {
            PatcherLog.info(t) { "NexFusion: Discord entry point not found: ${target.descriptor}" }
        }
    }
}

/** Replaces the hooked method's body with what Venus's early-return prefix returns. */
private fun applyPrivacyResult(param: MethodHookParam, result: PrivacyResult) {
    when (result) {
        PrivacyResult.VOID -> param.setResult(null)
        PrivacyResult.TRUE -> param.setResult(true)
        PrivacyResult.FALSE -> param.setResult(false)
        PrivacyResult.NULL -> param.setResult(null)
        PrivacyResult.BOXED_FALSE -> param.setResult(java.lang.Boolean.FALSE)
        PrivacyResult.SELF -> param.setResult(param.thisObject)
        PrivacyResult.EMPTY_STRING -> param.setResult("")
        PrivacyResult.THROWABLE_STRING ->
            param.setResult((param.args.getOrNull(0) as? Throwable)?.toString() ?: "")
        PrivacyResult.CALLBACK_NULL -> param.invokeCallback(null)
        PrivacyResult.CALLBACK_FALSE -> param.invokeCallback(false)
        PrivacyResult.CALLBACK_EMPTY_STRING -> param.invokeCallback("")
        PrivacyResult.PROMISE_NULL -> param.resolvePromise(null)
        PrivacyResult.PROMISE_TRUE -> param.resolvePromise(true)
        PrivacyResult.PROMISE_FALSE -> param.resolvePromise(false)
        PrivacyResult.PROMISE_EMPTY_ARRAY -> param.resolvePromise(param.newWritableArray())
        PrivacyResult.EMPTY_MAP -> param.newWritableMap()?.let { param.setResult(it) }
        PrivacyResult.PROFILE_START -> param.newWritableMap()?.let {
            XposedHelpers.callMethod(it, "putBoolean", "started", false)
            param.setResult(it)
        }
        PrivacyResult.AD_ID_MAP -> {
            // The Ads bridge reads the map's fields, so resolve the limited-tracking object Android
            // reports after "Delete advertising ID" instead of null, like Venus does.
            val map = param.newWritableMap()
            if (map != null) {
                XposedHelpers.callMethod(map, "putNull", "googleAdvertisingId")
                XposedHelpers.callMethod(map, "putBoolean", "isLimitAdTrackingEnabled", true)
            }
            param.resolvePromise(map)
        }
        PrivacyResult.NO_AD_ID ->
            (param.method as? Method)?.returnType?.let { stubAdIdInfo(it) }?.let { param.setResult(it) }
        PrivacyResult.AF_SUCCESS -> {
            val listener = param.args.lastOrNull()
            if (listener != null) {
                runCatching { XposedHelpers.callMethod(listener, "onSuccess") }
            }
            param.setResult(null)
        }
    }
}

/**
 * Builds the "no advertising ID" object Venus creates: the constructor `(String, boolean)` with
 * `("", true)`. Returns null (leaving the original method alone) when the shape differs.
 */
private fun stubAdIdInfo(returnType: Class<*>): Any? {
    val constructor = returnType.declaredConstructors.firstOrNull {
        it.parameterTypes.size == 2 && it.parameterTypes[0] == String::class.java &&
            (it.parameterTypes[1] == java.lang.Boolean.TYPE || it.parameterTypes[1] == java.lang.Boolean::class.java)
    } ?: return null
    return runCatching {
        constructor.isAccessible = true
        constructor.newInstance("", true)
    }.getOrNull()
}

/** `new Promise.resolve(value)` for the promise the bridge passed last; the original body is skipped. */
private fun MethodHookParam.resolvePromise(value: Any?) {
    val promise = args.lastOrNull()
    if (promise != null) {
        runCatching { XposedHelpers.callMethod(promise, "resolve", value) }
    }
    setResult(null)
}

/** `new Callback.invoke(value)` for the callback the bridge passed last; the original body is skipped. */
private fun MethodHookParam.invokeCallback(value: Any?) {
    val callback = args.lastOrNull()
    if (callback != null) {
        runCatching {
            XposedHelpers.callMethod(callback, "invoke", *arrayOf<Any?>(arrayOf<Any?>(value)))
        }
    }
    setResult(null)
}

private fun MethodHookParam.reactArguments(): Class<*>? =
    XposedHelpers.findClassIfExists("com.facebook.react.bridge.Arguments", method.declaringClass.classLoader)

private fun MethodHookParam.newWritableMap(): Any? =
    reactArguments()?.let { runCatching { XposedHelpers.callStaticMethod(it, "createMap") }.getOrNull() }

private fun MethodHookParam.newWritableArray(): Any? =
    reactArguments()?.let { runCatching { XposedHelpers.callStaticMethod(it, "createArray") }.getOrNull() }
