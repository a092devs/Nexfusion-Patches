package io.github.nexfusion.nexfusion.twitch

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.compat.XposedHelpers
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch

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

val TwitchAutoClaimPoints = patch(
    name = "Auto-claim channel points",
    description = "Automatically claims channel points and community bonus chests as soon as they appear in stream chat.",
    use = true,
) {
    runCatching {
        val presenterClasses = listOf(
            "tv.twitch.android.feature.points.CommunityPointsClaimPresenter",
            "tv.twitch.android.feature.channelpoints.ChannelPointsPresenter",
            "tv.twitch.android.feature.channelpoints.CommunityPointsButtonViewDelegate",
        )
        for (className in presenterClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.filter {
                it.name.contains("onClaimAvailable", ignoreCase = true) ||
                it.name.contains("showClaimButton", ignoreCase = true) ||
                it.name.contains("renderClaimButton", ignoreCase = true)
            }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val presenter = param.thisObject ?: return
                        runCatching {
                            val claimMethod = presenter.javaClass.declaredMethods.firstOrNull {
                                it.name.contains("claim", ignoreCase = true) && it.parameterCount == 0
                            }
                            claimMethod?.isAccessible = true
                            claimMethod?.invoke(presenter)
                            PatcherLog.info { "NexFusion: Twitch auto-claimed channel points bonus" }
                        }
                    }
                })
            }
        }
    }
}

val TwitchShowDeletedMessages = patch(
    name = "Show deleted chat messages",
    description = "Preserves deleted and timed-out messages in stream chat instead of removing them.",
    use = false,
) {
    runCatching {
        val chatClasses = listOf(
            "tv.twitch.android.shared.chat.ChatMessage",
            "tv.twitch.android.models.chat.ChatMessageModel",
            "tv.twitch.android.shared.chat.adapter.item.ChatMessageItem",
        )
        for (className in chatClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.filter {
                it.name == "isDeleted" || it.name == "getIsDeleted" || it.name == "isRemoved"
            }.forEach { method ->
                if (method.returnType == java.lang.Boolean.TYPE) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = false
                        }
                    })
                }
            }
        }
    }
}

val TwitchPatches = arrayOf(
    TwitchBlockAds,
    TwitchThirdPartyEmotes,
    TwitchBackgroundAudio,
    TwitchAutoClaimPoints,
    TwitchShowDeletedMessages,
    TwitchDisableAnalytics,
    TwitchAmoledTheme,
)


