package io.github.nexfusion.nexfusion.mxplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch

val MxPlayerBlockAds = patch(
    name = "Block ads",
    description = "Removes startup splash ads, Google Mobile Ads (AdMob), InMobi, and in-feed/banner ad views in MX Player.",
) {
    // 1. SharedPreferences remote config & ad status overrides
    runCatching {
        val sharedPrefsClass = classLoader.loadClass("android.app.SharedPreferencesImpl")
        XposedBridge.hookAllMethods(
            sharedPrefsClass,
            "getBoolean",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val key = param.args.firstOrNull() as? String ?: return
                    when (key) {
                        "shouldWelcomeAdEnabled",
                        "is_splash_ad_enabled",
                        "ad_enabled",
                        "interstitial_ad_enabled" -> {
                            param.result = false
                        }
                        "ad_removed",
                        "is_ad_removed",
                        "no_ads",
                        "pro_unlocked" -> {
                            param.result = true
                        }
                    }
                }
            }
        )
    }

    // 2. Suppress AdMob (Google Mobile Ads) views & loaders
    runCatching {
        val adViewClasses = listOf(
            "com.google.android.gms.ads.AdView",
            "com.google.android.gms.ads.BaseAdView",
            "com.google.android.gms.ads.nativead.NativeAdView",
        )
        for (className in adViewClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.filter { it.name == "loadAd" || it.name == "setVisibility" }.forEach { method ->
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (param.method.name == "loadAd") {
                                param.result = null
                            }
                        }
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.method.name == "setVisibility") {
                                (param.thisObject as? View)?.visibility = View.GONE
                            }
                        }
                    }
                )
            }
        }
    }

    // 3. Suppress Interstitial and AdLoader calls
    runCatching {
        val adClasses = listOf(
            "com.google.android.gms.ads.interstitial.InterstitialAd",
            "com.google.android.gms.ads.AdLoader",
            "com.facebook.ads.AdView",
            "com.facebook.ads.InterstitialAd",
            "com.inmobi.ads.InMobiBanner",
            "com.inmobi.ads.InMobiInterstitial",
            "com.unity3d.services.ads.UnityAds",
        )
        for (className in adClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.filter { it.name.startsWith("load") || it.name.startsWith("show") }.forEach { method ->
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

    // 4. Collapse ad container view layouts dynamically
    runCatching {
        XposedBridge.hookAllMethods(
            View::class.java,
            "onAttachedToWindow",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? View ?: return
                    val res = view.resources ?: return
                    val id = view.id
                    if (id <= 0) return
                    val entryName = runCatching { res.getResourceEntryName(id) }.getOrNull() ?: return
                    val lower = entryName.lowercase()
                    if (lower.contains("ad_container") ||
                        lower.contains("native_ad") ||
                        lower.contains("banner_ad") ||
                        lower.contains("adview") ||
                        lower.contains("ll_ad") ||
                        lower.contains("fl_ad") ||
                        lower.contains("feed_ad") ||
                        lower.contains("splash_ad") ||
                        lower.contains("interstitial_ad")
                    ) {
                        view.visibility = View.GONE
                        view.layoutParams?.let { lp ->
                            lp.height = 0
                            lp.width = 0
                            view.layoutParams = lp
                        }
                    }
                }
            }
        )
    }
}

val MxPlayerInstantSplash = patch(
    name = "Instant splash",
    description = "Bypasses splash screen delay, consent dialogs, and heavy telemetry to open the media list immediately.",
) {
    // 1. Suppress UMP consent dialog popups
    runCatching {
        val umpClass = classLoader.loadClass("com.google.android.ump.UserMessagingPlatform")
        XposedBridge.hookAllMethods(
            umpClass,
            "loadAndShowConsentFormIfRequired",
            XC_MethodReplacement.DO_NOTHING
        )
    }

    // 2. Suppress unread channel queries and heavy splash network tasks
    runCatching {
        val channelHelperClass = runCatching {
            classLoader.loadClass("com.mxtech.videoplayer.ad.online.mxchannel.utils.MXChannelUnreadHelper")
        }.getOrNull()
        if (channelHelperClass != null) {
            channelHelperClass.declaredMethods.forEach { method ->
                XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING)
            }
        }
    }

    // 3. Fast-track ActivityWelcomeMX to ActivityMediaList
    runCatching {
        val welcomeActivityClasses = listOf(
            "com.mxtech.videoplayer.ad.ActivityWelcomeMX",
            "com.mxtech.videoplayer.pro.ActivityWelcomeMX",
        )
        for (className in welcomeActivityClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            XposedBridge.hookAllMethods(
                clazz,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as? Activity ?: return
                        val targetClass = runCatching {
                            if (className.contains(".pro.")) {
                                classLoader.loadClass("com.mxtech.videoplayer.pro.ActivityMediaList")
                            } else {
                                classLoader.loadClass("com.mxtech.videoplayer.ad.ActivityMediaList")
                            }
                        }.getOrNull()
                        if (targetClass != null) {
                            val intent = Intent(activity, targetClass)
                            activity.startActivity(intent)
                            activity.finish()
                        }
                    }
                }
            )
        }
    }
}

val MxPlayerLocalMode = patch(
    name = "Clean local mode",
    description = "Disables the online streaming bottom navigation bar to restore a clean, offline-first local media player.",
) {
    // Hide the online bottom bar in ActivityMediaList
    runCatching {
        XposedBridge.hookAllMethods(
            View::class.java,
            "onAttachedToWindow",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? View ?: return
                    val res = view.resources ?: return
                    val id = view.id
                    if (id <= 0) return
                    val entryName = runCatching { res.getResourceEntryName(id) }.getOrNull() ?: return
                    val lower = entryName.lowercase()
                    if (lower == "online_bottom_layout" ||
                        lower == "bottom_navigation" ||
                        lower == "navigation_bar_online" ||
                        lower == "online_tab_container"
                    ) {
                        view.visibility = View.GONE
                        view.layoutParams?.let { lp ->
                            lp.height = 0
                            view.layoutParams = lp
                        }
                    }
                }
            }
        )
    }

    // Hook bottom bar visibility toggles
    runCatching {
        val mediaListClasses = listOf(
            "com.mxtech.videoplayer.ad.ActivityMediaList",
            "com.mxtech.videoplayer.pro.ActivityMediaList",
        )
        for (className in mediaListClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.filter {
                it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == Boolean::class.javaPrimitiveType &&
                it.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                it.returnType == Void.TYPE
            }.forEach { method ->
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.args[0] = false
                            param.args[1] = false
                        }
                    }
                )
            }
        }
    }
}

val MxPlayerCleanLayout = patch(
    name = "Clean layout & sidebar",
    description = "Removes promotional tiles, cloud recycle bin upsells, and cluttered player sidebar items.",
) {
    // 1. Suppress recycle bin cloud upsell dialog
    runCatching {
        val recycleBinManager = runCatching {
            classLoader.loadClass("com.mxtech.bin.RecycleBinManager")
        }.getOrNull()
        if (recycleBinManager != null) {
            recycleBinManager.declaredMethods.filter {
                it.returnType == Boolean::class.javaPrimitiveType
            }.forEach { method ->
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (param.method.name.lowercase().contains("enable") ||
                                param.method.name.lowercase().contains("show")
                            ) {
                                param.result = false
                            }
                        }
                    }
                )
            }
        }
    }

    // 2. Hide promotional and unnecessary tiles in Me / Settings tabs
    runCatching {
        XposedBridge.hookAllMethods(
            View::class.java,
            "onAttachedToWindow",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? View ?: return
                    val res = view.resources ?: return
                    val id = view.id
                    if (id <= 0) return
                    val entryName = runCatching { res.getResourceEntryName(id) }.getOrNull() ?: return
                    val lower = entryName.lowercase()
                    if (lower == "whatsapp_layout" ||
                        lower == "ll_add_to_playlist" ||
                        lower == "transfer_share" ||
                        lower == "option_private_folder" ||
                        lower.contains("recycle_bin_tile") ||
                        lower.contains("vip_banner") ||
                        lower.contains("gold_banner") ||
                        lower.contains("promo_banner")
                    ) {
                        view.visibility = View.GONE
                    }
                }
            }
        )
    }

    // 3. Player sidebar clutter removal (Bookmark, Favorite, Playlist, Tutorial, Rate Us)
    runCatching {
        val menuClasses = listOf(
            "com.mxtech.videoplayer.ad.MenuHelper",
            "com.mxtech.videoplayer.pro.MenuHelper",
        )
        for (className in menuClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.filter {
                it.name.contains("sidebar", ignoreCase = true) ||
                it.name.contains("menu", ignoreCase = true)
            }.forEach { method ->
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val result = param.result as? MutableCollection<*> ?: return
                            result.removeIf { item ->
                                val str = item.toString().lowercase()
                                str.contains("bookmark") ||
                                str.contains("favourite") ||
                                str.contains("playlist") ||
                                str.contains("tutorial") ||
                                str.contains("rate")
                            }
                        }
                    }
                )
            }
        }
    }
}

val MxPlayerPlaybackTweaks = patch(
    name = "Playback tweaks",
    description = "Bypasses the Smart Enhance intro popup and suppresses the 2x SpeedUp HUD overlay.",
) {
    // 1. Bypass Smart Enhance intro dialog
    runCatching {
        val sharedPrefsClass = classLoader.loadClass("android.app.SharedPreferencesImpl")
        XposedBridge.hookAllMethods(
            sharedPrefsClass,
            "getBoolean",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val key = param.args.firstOrNull() as? String ?: return
                    if (key == "KEY_PLAYER_MENU_ENHANCE_NEW") {
                        param.result = true
                    }
                }
            }
        )
    }

    // 2. SpeedUp 2x HUD overlay suppression
    runCatching {
        XposedBridge.hookAllMethods(
            View::class.java,
            "onAttachedToWindow",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? View ?: return
                    val res = view.resources ?: return
                    val id = view.id
                    if (id <= 0) return
                    val entryName = runCatching { res.getResourceEntryName(id) }.getOrNull() ?: return
                    val lower = entryName.lowercase()
                    if (lower.contains("speed_ff") || lower.contains("speedup_overlay")) {
                        view.visibility = View.GONE
                    }
                }
            }
        )
    }
}

val MxPlayerPatches = arrayOf(
    MxPlayerBlockAds,
    MxPlayerInstantSplash,
    MxPlayerLocalMode,
    MxPlayerCleanLayout,
    MxPlayerPlaybackTweaks,
)

val MxPlayerProPatches = arrayOf(
    MxPlayerInstantSplash,
    MxPlayerCleanLayout,
    MxPlayerPlaybackTweaks,
)

