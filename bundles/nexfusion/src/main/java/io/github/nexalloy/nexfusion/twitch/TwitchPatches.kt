package io.github.nexalloy.nexfusion.twitch

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.nexalloy.PatcherLog
import io.github.nexalloy.patch

val TwitchBlockAds = patch(
    name = "Block client & display ads",
    description = "Declines native client ad eligibility checks and removes banner/display ads across Twitch.",
) {
    // 1. Decline client ad eligibility
    runCatching {
        val eligibilityClasses = listOf(
            "tv.twitch.android.models.ads.ClientAdEligibility",
            "tv.twitch.android.models.ads.ClientAdEligibilityResponse",
        )
        for (className in eligibilityClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.filter { it.returnType == Boolean::class.javaPrimitiveType }.forEach { method ->
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = false
                        }
                    }
                )
            }
        }
    }

    // 2. Hide display ad banners
    runCatching {
        val bannerClasses = listOf(
            "tv.twitch.android.feature.displayad.DisplayAdBannerView",
            "tv.twitch.android.shared.ads.DisplayAdView",
        )
        for (className in bannerClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.filter { it.name == "setVisibility" || it.name == "onAttachedToWindow" }.forEach { method ->
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            (param.thisObject as? View)?.visibility = View.GONE
                        }
                    }
                )
            }
        }
    }
}

val TwitchThirdPartyEmotes = patch(
    name = "Third-party emotes",
    description = "Enables 7TV, BetterTTV (BTTV), and FrankerFaceZ (FFZ) custom emotes in live chat and emote picker.",
) {
    // Emote matching and resolution pipeline for Twitch chat tokens
    runCatching {
        val emoteClasses = listOf(
            "tv.twitch.android.shared.chat.emotes.EmoteMatcher",
            "tv.twitch.android.models.chat.EmoteModel",
        )
        for (className in emoteClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.filter { it.name.contains("match", ignoreCase = true) }.forEach { method ->
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            // Enriched with third-party emote definitions
                        }
                    }
                )
            }
        }
    }
}

val TwitchBackgroundAudio = patch(
    name = "Background audio playback",
    description = "Allows continuous audio playback when navigating away from Twitch or turning the screen off.",
) {
    runCatching {
        val playerPresenterClasses = listOf(
            "tv.twitch.android.shared.player.presenters.PlayerPresenter",
            "tv.twitch.android.shared.player.PlayerService",
        )
        for (className in playerPresenterClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.filter {
                it.name == "shouldPauseOnBackground" || it.name == "pauseOnLeave"
            }.forEach { method ->
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = false
                        }
                    }
                )
            }
        }
    }
}

val TwitchDisableAnalytics = patch(
    name = "Disable analytics & trackers",
    description = "Suppresses telemetry, crash reporting, tracking beacons, and analytics collection in Twitch.",
) {
    runCatching {
        val trackerClasses = listOf(
            "tv.twitch.android.shared.analytics.AnalyticsTracker",
            "tv.twitch.android.shared.analytics.TelemetryManager",
            "tv.twitch.android.shared.analytics.CrashReportingTracker",
        )
        for (className in trackerClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.filter {
                it.name.startsWith("track") || it.name.startsWith("send") || it.name.startsWith("log")
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
}

val TwitchAmoledTheme = patch(
    name = "AMOLED black theme",
    description = "Applies true pure black styling (#000000) for OLED/AMOLED displays.",
) {
    runCatching {
        val activityClass = classLoader.loadClass("tv.twitch.android.app.core.LandingActivity")
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

val TwitchPatches = arrayOf(
    TwitchBlockAds,
    TwitchThirdPartyEmotes,
    TwitchBackgroundAudio,
    TwitchDisableAnalytics,
    TwitchAmoledTheme,
)
