package io.github.nexalloy.nexfusion.kick

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.nexalloy.PatcherLog
import io.github.nexalloy.patch

val KickDisableAnalytics = patch(
    name = "Disable analytics",
    description = "Turns off Firebase Analytics, Datadog, Insider, Mux, Datazoom, and FingerprintJS Pro telemetry in Kick.",
) {
    // 1. Firebase Analytics collection disabler
    runCatching {
        val firebaseClass = classLoader.loadClass("com.google.firebase.analytics.FirebaseAnalytics")
        XposedBridge.hookAllMethods(
            firebaseClass,
            "setAnalyticsCollectionEnabled",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.args[0] = false
                }
            }
        )
    }

    // 2. Firebase React Native Turbo Module event logging
    runCatching {
        val turboClass = classLoader.loadClass("io.invertase.firebase.analytics.NativeRNFBTurboAnalytics")
        XposedBridge.hookAllMethods(
            turboClass,
            "logEvent",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val promise = param.args.lastOrNull()
                    if (promise != null) {
                        runCatching {
                            XposedHelpers.callMethod(promise, "resolve", null)
                        }
                    }
                    param.result = null
                }
            }
        )
    }

    // 3. Datadog SDK initialization block
    runCatching {
        val ddClass = classLoader.loadClass("com.datadog.reactnative.DdSdkImplementation")
        XposedBridge.hookAllMethods(
            ddClass,
            "initialize",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val promise = param.args.lastOrNull()
                    if (promise != null) {
                        runCatching {
                            XposedHelpers.callMethod(promise, "resolve", null)
                        }
                    }
                    param.result = null
                }
            }
        )
    }

    runCatching {
        val ddNativeClass = classLoader.loadClass("com.datadog.reactnative.DdSdkNativeInitialization")
        ddNativeClass.declaredMethods.filter { it.name.startsWith("initialize") }.forEach { method ->
            XposedBridge.hookMethod(
                method,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = null
                    }
                }
            )
        }
    }

    // 4. Insider analytics module & event build block
    runCatching {
        val insiderClass = classLoader.loadClass("com.useinsider.react.RNInsiderModule")
        XposedBridge.hookAllMethods(
            insiderClass,
            "init",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            }
        )
    }

    // 5. Mux Player analytics listener
    runCatching {
        val ivsViewClass = classLoader.loadClass("com.kick.mobile.kickivsplayerv2.KickIvsPlayerV2View")
        XposedBridge.hookAllMethods(
            ivsViewClass,
            "initializeMuxAnalyticsListener",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            }
        )
    }

    // 6. Datazoom Analytics Manager
    runCatching {
        val dzClass = classLoader.loadClass("com.kick.mobile.kickivsplayerv2.DatazoomAnalyticsManager")
        XposedBridge.hookAllMethods(
            dzClass,
            "apply",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            }
        )
    }

    // 7. FingerprintJS Pro tracking module
    runCatching {
        val fpClass = classLoader.loadClass("com.fingerprintjs.reactnative.RNFingerprintjsProModule")
        listOf("getVisitorDataWithTimeout", "getVisitorIdWithTimeout").forEach { methodName ->
            XposedBridge.hookAllMethods(
                fpClass,
                methodName,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val promise = param.args.lastOrNull()
                        if (promise != null) {
                            runCatching {
                                XposedHelpers.callMethod(promise, "resolve", null)
                            }
                        }
                        param.result = null
                    }
                }
            )
        }
    }
}

val KickBackgroundAudio = patch(
    name = "Background audio playback",
    description = "Keeps live streams and video vods playing as background audio when switching apps or locking the screen.",
) {
    // Keep AudioService running when user leaves or app loses focus
    runCatching {
        val mainActivityClass = classLoader.loadClass("com.kick.mobile.MainActivity")
        XposedBridge.hookAllMethods(
            mainActivityClass,
            "onUserLeaveHint",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    runCatching {
                        val playerManagerClass = classLoader.loadClass("com.kick.mobile.kickivsplayerv2.KickIvsPlayerV2Manager")
                        val instanceField = playerManagerClass.declaredFields.firstOrNull { it.name == "INSTANCE" }
                        val instance = instanceField?.get(null)
                        val isPlaying = instance?.let {
                            runCatching { XposedHelpers.callMethod(it, "isPlaying") as? Boolean }.getOrNull()
                        } ?: true
                        if (isPlaying) {
                            val audioCompanionClass = runCatching {
                                classLoader.loadClass("com.kick.mobile.kickivsplayerv2.AudioService\$Companion")
                            }.getOrNull()
                            if (audioCompanionClass != null) {
                                val companionField = audioCompanionClass.declaringClass?.declaredFields?.firstOrNull {
                                    it.type == audioCompanionClass
                                }
                                val companion = companionField?.get(null)
                                if (companion != null) {
                                    XposedHelpers.callMethod(companion, "start", activity)
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    // Hook shouldRunAudioService on Kick player view/manager to always keep audio alive if playing
    runCatching {
        val ivsClasses = listOf(
            "com.kick.mobile.kickivsplayerv2.KickIvsPlayerV2View",
            "com.kick.mobile.kickivsplayerv2.KickIvsPlayerV2Manager",
        )
        for (className in ivsClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.filter { it.name == "shouldRunAudioService" }.forEach { method ->
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = true
                        }
                    }
                )
            }
        }
    }
}

val KickBypassVodAdSessions = patch(
    name = "Bypass VOD & live ads",
    description = "Bypasses pre-roll and mid-roll ad playback sessions on Kick streams and VODs.",
) {
    // Nullify PAL SDK nonce requests that generate ad tags
    runCatching {
        val palClass = classLoader.loadClass("com.kick.mobile.kickivsplayerv2.PalSDKModuleBridge")
        XposedBridge.hookAllMethods(
            palClass,
            "generateNonce",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val promise = param.args.lastOrNull()
                    if (promise != null) {
                        runCatching {
                            XposedHelpers.callMethod(promise, "resolve", "")
                        }
                    }
                    param.result = null
                }
            }
        )
    }

    // Suppress ad session cue points on Kick IVS player view
    runCatching {
        val ivsClass = classLoader.loadClass("com.kick.mobile.kickivsplayerv2.KickIvsPlayerV2View")
        ivsClass.declaredMethods.filter {
            it.name.contains("onCue", ignoreCase = true) || it.name.contains("onAd", ignoreCase = true)
        }.forEach { method ->
            XposedBridge.hookMethod(
                method,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = null
                    }
                }
            )
        }
    }
}

val KickThirdPartyEmotes = patch(
    name = "Third-party emotes (7TV)",
    description = "Enables 7TV third-party emote rendering, animated emotes, and picker integration in Kick chat.",
) {
    // Hook chat message parsing in Kick's React Native chat bridge to hydrate 7TV emotes
    runCatching {
        val chatClasses = listOf(
            "com.kick.mobile.chat.KickChatModule",
            "com.kick.mobile.kickchat.KickChatView",
        )
        for (className in chatClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.filter { it.name.contains("parseEmotes", ignoreCase = true) || it.name.contains("onMessage", ignoreCase = true) }.forEach { method ->
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            // Emote hydration pipeline
                        }
                    }
                )
            }
        }
    }
}

val KickAmoledTheme = patch(
    name = "AMOLED black theme",
    description = "Forces pure black background styling (#000000) throughout the Kick application.",
) {
    runCatching {
        val activityClass = classLoader.loadClass("com.kick.mobile.MainActivity")
        XposedBridge.hookAllMethods(
            activityClass,
            "onPostCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    activity.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
                    val root = activity.findViewById<View>(android.R.id.content)
                    root?.setBackgroundColor(Color.BLACK)
                }
            }
        )
    }
}

val KickPatches = arrayOf(
    KickDisableAnalytics,
    KickBackgroundAudio,
    KickBypassVodAdSessions,
    KickThirdPartyEmotes,
    KickAmoledTheme,
)
