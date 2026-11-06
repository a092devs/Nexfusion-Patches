package io.github.nexfusion.nexfusion.mxplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import java.io.IOException
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.net.URL

val MxPlayerBlockAds = patch(
    name = "Block ads",
    description = "Removes startup splash ads, Google AdMob, Monetize-v2 ad engine, banner/interstitial/native ads, and player ads in MX Player.",
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
                        // In MX Player, shouldWelcomeAdEnabled is an INVERTED flag: true = Welcome Ad DISABLED.
                        "shouldWelcomeAdEnabled",
                        "ad_removed",
                        "is_ad_removed",
                        "no_ads",
                        "pro_unlocked",
                        "is_pro",
                        "is_vip",
                        "is_subscribed" -> {
                            param.result = true
                        }
                        "is_splash_ad_enabled",
                        "ad_enabled",
                        "interstitial_ad_enabled",
                        "rewarded_ad_enabled",
                        "banner_ad_enabled",
                        "native_ad_enabled",
                        "app_open_ad_enabled",
                        "bannerForPlayer",
                        "key_has_check_video_ad",
                        "local_pause_recomm",
                        "for_you_entry_enabled" -> {
                            param.result = false
                        }
                    }
                }
            }
        )
    }

    // 2. GlobalConfig pure local phone mode & feature gates
    runCatching {
        val gcClass = runCatching { ::mxGlobalConfig.clazz }.getOrNull() ?: listOf(
            "bw7",
            "com.mxtech.videoplayer.ad.GlobalConfig",
            "com.mxtech.videoplayer.GlobalConfig",
            "com.mxtech.videoplayer.ad.config.GlobalConfig",
            "com.mxtech.videoplayer.ad.common.GlobalConfig",
            "com.mxtech.videoplayer.ad.utils.GlobalConfig",
            "com.mxtech.videoplayer.utils.GlobalConfig",
        ).firstNotNullOfOrNull { runCatching { classLoader.loadClass(it) }.getOrNull() }

        if (gcClass != null) {
            gcClass.declaredMethods.filter { it.returnType == Boolean::class.javaPrimitiveType }.forEach { method ->
                when (method.name) {
                    "j", "B" -> {
                        // j() = pure local mode, B() = local music
                        XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(true))
                    }
                    "C", "i", "o", "F", "y", "A", "w", "v", "E", "D", "t", "u", "x", "f", "l" -> {
                        // C() = online mode, i/o = region OTT, F = streaming, y = live TV, A = Tube,
                        // w = games, v = shorts/fatafat, E = upcoming, D = OTT download, t = bazaar,
                        // u = download promo, x = secondary online tab, f = Amazon game, l = YT search
                        XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(false))
                    }
                }
            }
            PatcherLog.info { "NexFusion: MX Player GlobalConfig hooked on ${gcClass.name}" }
        }
    }

    // 3. WelcomeAdFeatureFlag inverted disable flags
    runCatching {
        val welcomeFlagClass = runCatching { ::mxWelcomeAdFeatureFlag.clazz }.getOrNull() ?: listOf(
            "hgl",
            "com.mxtech.videoplayer.ad.online.ad.welcome.WelcomeAdFeatureFlag",
        ).firstNotNullOfOrNull { runCatching { classLoader.loadClass(it) }.getOrNull() }

        if (welcomeFlagClass != null) {
            welcomeFlagClass.declaredMethods.filter {
                it.parameterTypes.isEmpty() && it.returnType == Boolean::class.javaPrimitiveType
            }.forEach { method ->
                // Both b() and a() return inverted disable flag (true = Welcome Ad DISABLED)
                XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(true))
            }
            PatcherLog.info { "NexFusion: MX Player WelcomeAdFeatureFlag hooked on ${welcomeFlagClass.name}" }
        }
    }

    // 4. Monetize-v2 Ad Engine suppression (BannerManager, InterstitialV2, VideoRollManager, etc.)
    runCatching {
        // Safe hook for AdInitializer (mq) to return empty list and false
        runCatching { ::mxAdInitializer.clazz }.getOrNull() ?: listOf("mq", "lq", "com.mxplay.monetize.v2.adinitializer.AdInitializer")
            .firstNotNullOfOrNull { runCatching { classLoader.loadClass(it) }.getOrNull() }
            ?.let { adInitClass ->
                adInitClass.declaredMethods.forEach { method ->
                    if (method.name == "<init>" || method.name == "<clinit>") return@forEach
                    val ret = method.returnType
                    if (ret == Boolean::class.javaPrimitiveType) {
                        XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(false))
                    } else if (java.util.List::class.java.isAssignableFrom(ret) || java.util.Collection::class.java.isAssignableFrom(ret)) {
                        XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(java.util.Collections.emptyList<Any>()))
                    }
                }
            }

        val candidateList = mutableListOf<Class<*>>()

        // BannerManager
        listOf("ki1", "com.mxplay.monetize.v2.loader.BannerManager").firstNotNullOfOrNull {
            runCatching { classLoader.loadClass(it) }.getOrNull()
        }?.let { candidateList.add(it) }

        // InterstitialV2
        runCatching { ::mxInterstitial.clazz }.getOrNull()?.let { candidateList.add(it) }
            ?: listOf("en9", "an9", "bn9", "cn9", "dn9", "com.mxplay.monetize.v2.interstitial.InterstitialV2").firstNotNullOfOrNull {
                runCatching { classLoader.loadClass(it) }.getOrNull()
            }?.let { candidateList.add(it) }

        // RewardedAdPlacement
        listOf("qzg", "com.mxplay.monetize.v2.rewarded.RewardedAdPlacement").firstNotNullOfOrNull {
            runCatching { classLoader.loadClass(it) }.getOrNull()
        }?.let { candidateList.add(it) }

        // PanelNative
        listOf("kme", "hme", "ime", "jme", "com.mxplay.monetize.v2.nativead.PanelNative").firstNotNullOfOrNull {
            runCatching { classLoader.loadClass(it) }.getOrNull()
        }?.let { candidateList.add(it) }

        // TrayNative
        listOf("n2k", "com.mxplay.monetize.v2.nativead.TrayNative").firstNotNullOfOrNull {
            runCatching { classLoader.loadClass(it) }.getOrNull()
        }?.let { candidateList.add(it) }

        // VideoRollManager
        runCatching { ::mxVideoRollManager.clazz }.getOrNull()?.let { candidateList.add(it) }
            ?: listOf("ozk", "com.mxplay.monetize.v2.videoroll.VideoRollManager").firstNotNullOfOrNull {
                runCatching { classLoader.loadClass(it) }.getOrNull()
            }?.let { candidateList.add(it) }

        // OpenAds
        listOf("v2e", "com.mxplay.monetize.v2.loader.OpenAds").firstNotNullOfOrNull {
            runCatching { classLoader.loadClass(it) }.getOrNull()
        }?.let { candidateList.add(it) }

        // AppOpenAdManager & LocalOnlyExitAdTest
        listOf(
            "com.mxtech.videoplayer.ad.online.ad.appopen.AppOpenAdManager",
            "com.mxtech.videoplayer.ad.online.abtest.LocalOnlyExitAdTest",
        ).mapNotNull { runCatching { classLoader.loadClass(it) }.getOrNull() }.forEach { candidateList.add(it) }

        for (clazz in candidateList.distinct()) {
            clazz.declaredMethods.forEach { method ->
                if (method.name == "<init>" || method.name == "<clinit>") return@forEach
                val returnType = method.returnType
                if (returnType == Boolean::class.javaPrimitiveType) {
                    XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(false))
                } else if (returnType == Void.TYPE) {
                    if (method.name.startsWith("load") ||
                        method.name.startsWith("show") ||
                        method.name.startsWith("preload") ||
                        method.name.startsWith("init") ||
                        method.name == "a" || method.name == "b" || method.name == "c" ||
                        method.name == "e" || method.name == "g" || method.name == "f" ||
                        method.name == "p" || method.name == "P" || method.name == "O" ||
                        method.parameterTypes.isEmpty()
                    ) {
                        XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING)
                    }
                } else if (!returnType.isPrimitive) {
                    if (java.util.List::class.java.isAssignableFrom(returnType) ||
                        java.util.Collection::class.java.isAssignableFrom(returnType)
                    ) {
                        XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(java.util.Collections.emptyList<Any>()))
                    } else if (java.util.Set::class.java.isAssignableFrom(returnType)) {
                        XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(java.util.Collections.emptySet<Any>()))
                    } else if (java.util.Map::class.java.isAssignableFrom(returnType)) {
                        XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(java.util.Collections.emptyMap<Any, Any>()))
                    } else if (returnType == java.lang.String::class.java) {
                        XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(""))
                    } else if (returnType.isArray) {
                        val compType = returnType.componentType ?: Any::class.java
                        XposedBridge.hookMethod(
                            method,
                            XC_MethodReplacement.returnConstant(java.lang.reflect.Array.newInstance(compType, 0))
                        )
                    } else if (method.name.startsWith("load") || method.name.startsWith("get") || method.name == "a") {
                        XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(null))
                    }
                }
            }
        }
    }

    // 4b. Guard AdManager (jr) against "instance not set" NullPointerException
    runCatching {
        val jrClass = listOf("jr", "com.mxtech.videoplayer.ad.AdManager").firstNotNullOfOrNull {
            runCatching { classLoader.loadClass(it) }.getOrNull()
        }
        if (jrClass != null) {
            jrClass.declaredMethods.filter {
                it.parameterTypes.isEmpty() && !it.returnType.isPrimitive && it.returnType != Void.TYPE
            }.forEach { method ->
                val returnType = method.returnType
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.hasThrowable()) {
                            if (returnType.isInterface) {
                                param.result = createNoOpProxy(returnType, classLoader)
                            } else {
                                param.result = null
                            }
                        } else if (param.result == null && returnType.isInterface) {
                            param.result = createNoOpProxy(returnType, classLoader)
                        }
                    }
                })
            }
        }
    }

    // 5. Welcome Screen Ad & AppOpen Ad suppression in ActivityWelcomeMX
    runCatching {
        val welcomeActivityClass = runCatching {
            classLoader.loadClass("com.mxtech.videoplayer.ad.ActivityWelcomeMX")
        }.getOrNull()
        if (welcomeActivityClass != null) {
            XposedBridge.hookAllMethods(
                welcomeActivityClass,
                "onCreate",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // Disarm boolean ad flags (q0, u0, v0, w0, m0, n0, o0) on the welcome activity instance
                        welcomeActivityClass.declaredFields.filter { it.type == Boolean::class.javaPrimitiveType }.forEach { field ->
                            runCatching {
                                field.isAccessible = true
                                field.setBoolean(param.thisObject, false)
                            }
                        }
                    }
                }
            )
            welcomeActivityClass.declaredMethods.filter {
                it.name == "s7" && it.returnType == Boolean::class.javaPrimitiveType
            }.forEach { method ->
                XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(false))
            }
        }
    }

    // 6. Video Player screen ad hooks (ActivityScreen)
    runCatching {
        val screenClasses = listOf(
            "com.mxtech.videoplayer.ad.ActivityScreen",
            "com.mxtech.videoplayer.ActivityScreen",
        )
        for (className in screenClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.forEach { method ->
                val nameLower = method.name.lowercase()
                if (nameLower.contains("preroll") ||
                    nameLower.contains("midroll") ||
                    nameLower.contains("pausead") ||
                    nameLower.contains("pause_recomm") ||
                    nameLower.contains("videoroll") ||
                    nameLower.contains("bannerforplayer") ||
                    nameLower.contains("preloadcustom")
                ) {
                    if (method.returnType == Boolean::class.javaPrimitiveType) {
                        XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(false))
                    } else if (method.returnType == Void.TYPE) {
                        XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING)
                    }
                }
                if (method.name == "isLocalToOnlineRecom") {
                    XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(false))
                }
            }
        }
    }

    // 7. Folder list native ad rows & promo shortcut strip (LocalTilesManager & AdMediaListFragment)
    runCatching {
        val tilesClass = runCatching {
            classLoader.loadClass("com.mxtech.videoplayer.ad.local.tiles.LocalTilesManager")
        }.getOrNull()
        if (tilesClass != null) {
            tilesClass.declaredMethods.forEach { method ->
                if (method.returnType == Void.TYPE) {
                    XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING)
                } else if (method.returnType == Boolean::class.javaPrimitiveType) {
                    XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(false))
                } else if (method.returnType == List::class.java) {
                    XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(emptyList<Any>()))
                }
            }
        }

        val mediaFragmentClasses = listOf(
            "com.mxtech.videoplayer.ad.a",
            "com.mxtech.videoplayer.list.MediaListFragment",
        )
        for (className in mediaFragmentClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.forEach { method ->
                val nameLower = method.name.lowercase()
                if (nameLower.contains("tile") ||
                    nameLower.contains("promo") ||
                    nameLower.contains("foryou") ||
                    nameLower.contains("nativead")
                ) {
                    if (method.returnType == Boolean::class.javaPrimitiveType) {
                        XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(false))
                    } else if (method.returnType == Void.TYPE) {
                        XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING)
                    }
                }
            }
        }
    }

    // 8. Suppress AdMob (Google Mobile Ads) views & loaders
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

    // 9. Suppress Interstitial and AdLoader calls from third-party networks
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

    // 10. Collapse ad container view layouts dynamically
    runCatching {
        val adKeywords = setOf(
            "ad_container", "native_ad", "banner_ad", "adview", "ll_ad", "fl_ad", "feed_ad",
            "splash_ad", "interstitial_ad", "ad_frame", "ad_layout", "ad_view_container",
            "ad_wrapper", "ad_banner", "native_ad_view", "pause_recomm", "preloadcustom",
            "fab_local", "card_view_ad", "ad_card", "ll_ad_root"
        )
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
                    if (adKeywords.any { lower.contains(it) }) {
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

    // 11. HTTP Network Ad & Telemetry Blocker
    runCatching {
        XposedBridge.hookAllMethods(
            URL::class.java,
            "openConnection",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val url = (param.thisObject as? URL)?.toString()?.lowercase() ?: return
                    if (url.contains("evt.mxplay.com") ||
                        url.contains("googleads") ||
                        url.contains("doubleclick.net") ||
                        url.contains("inmobi.com") ||
                        url.contains("amazon-adsystem.com") ||
                        url.contains("comscore") ||
                        url.contains("clevertap") ||
                        url.contains("appsflyer") ||
                        url.contains("crashlytics") ||
                        url.contains("tab_online")
                    ) {
                        param.throwable = IOException("Blocked by NexFusion ad filter")
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
    // 1. Suppress UMP consent dialog popups & GDPR telemetry
    runCatching {
        val umpClass = classLoader.loadClass("com.google.android.ump.UserMessagingPlatform")
        XposedBridge.hookAllMethods(
            umpClass,
            "loadAndShowConsentFormIfRequired",
            XC_MethodReplacement.DO_NOTHING
        )
    }

    runCatching {
        val gdprLogClass = classLoader.loadClass("com.mxtech.videoplayer.ad.GdprLog")
        gdprLogClass.declaredMethods.forEach { method ->
            if (method.returnType == Void.TYPE) {
                XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING)
            }
        }
    }

    runCatching {
        val nationalClass = classLoader.loadClass("com.mxtech.videoplayer.ad.NationalUtil")
        nationalClass.declaredMethods.filter { it.returnType == Boolean::class.javaPrimitiveType }.forEach { method ->
            XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(false))
        }
    }

    // 2. Suppress unread channel queries, Amazon metrics, and heavy splash network tasks
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
            "com.mxtech.videoplayer.ActivityWelcomeMX",
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
                            classLoader.loadClass("com.mxtech.videoplayer.ad.ActivityMediaList")
                        }.getOrNull() ?: runCatching {
                            classLoader.loadClass("com.mxtech.videoplayer.ActivityMediaList")
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
    description = "Disables the online streaming bottom navigation bar to restore a clean, offline-first local media player, and keeps Me accessible from top toolbar.",
) {
    // 1. Hide the online bottom bar in ActivityMediaList
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

    // 2. Hook bottom bar visibility toggles
    runCatching {
        val mediaListClasses = listOf(
            "com.mxtech.videoplayer.ad.ActivityMediaList",
            "com.mxtech.videoplayer.ActivityMediaList",
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

    // 3. Ensure top action bar Me / Settings icon is visible and wired to MXLocalMePageActivity
    runCatching {
        val mediaListClasses = listOf(
            "com.mxtech.videoplayer.ad.ActivityMediaList",
            "com.mxtech.videoplayer.ActivityMediaList",
        )
        for (className in mediaListClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            XposedBridge.hookAllMethods(
                clazz,
                "onPrepareOptionsMenu",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val menu = param.args.firstOrNull() as? Menu ?: return
                        for (i in 0 until menu.size()) {
                            val item = menu.getItem(i)
                            val title = item.title?.toString()?.lowercase() ?: ""
                            if (title.contains("me") || title.contains("account") || title.contains("setting") ||
                                item.itemId == 0x7f0b0051 /* R.id.account */
                            ) {
                                item.isVisible = true
                                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                            }
                        }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                clazz,
                "onOptionsItemSelected",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val item = param.args.firstOrNull() as? MenuItem ?: return
                        val title = item.title?.toString()?.lowercase() ?: ""
                        if (title.contains("me") || title.contains("account") || title.contains("setting") ||
                            item.itemId == 0x7f0b0051
                        ) {
                            val activity = param.thisObject as? Activity ?: return
                            val targetClass = runCatching {
                                classLoader.loadClass("com.mxtech.videoplayer.ad.subscriptions.ui.metab.activity.MXLocalMePageActivity")
                            }.getOrNull() ?: runCatching {
                                classLoader.loadClass("com.mxtech.videoplayer.ad.subscriptions.ui.metab.activity.MXMePageActivity")
                            }.getOrNull()
                            if (targetClass != null) {
                                activity.startActivity(Intent(activity, targetClass))
                                param.result = true
                            }
                        }
                    }
                }
            )
        }
    }
}

val MxPlayerCleanLayout = patch(
    name = "Clean layout & sidebar",
    description = "Removes promotional tiles, WhatsApp status scanner, cloud recycle bin upsells, and cluttered player sidebar items.",
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
        val promoKeywords = setOf(
            "whatsapp_layout", "ll_add_to_playlist", "transfer_share", "option_private_folder",
            "recycle_bin_tile", "vip_banner", "gold_banner", "promo_banner", "status_saver",
            "legal_card", "help_card", "data_privacy"
        )
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
                    if (promoKeywords.any { lower.contains(it) }) {
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

    // 3. Disarm LocalMeFragment promo and WhatsApp background scanner coroutine
    runCatching {
        val meFragmentClass = runCatching {
            classLoader.loadClass("com.mxtech.videoplayer.ad.subscriptions.ui.metab.fragment.LocalMeFragment")
        }.getOrNull()
        if (meFragmentClass != null) {
            meFragmentClass.declaredMethods.forEach { method ->
                if (method.name.contains("openNavigationPromotion", ignoreCase = true)) {
                    XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING)
                } else if (method.parameterTypes.size == 1 && method.parameterTypes[0] == Boolean::class.javaPrimitiveType && method.returnType == Boolean::class.javaPrimitiveType) {
                    XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(false))
                }
            }
        }

        val whatsappCoroutineClass = runCatching {
            classLoader.loadClass("com.mxtech.videoplayer.ad.subscriptions.ui.metab.fragment.LocalMeFragment\$initWhatsAppStatus\$1")
        }.getOrNull()
        if (whatsappCoroutineClass != null) {
            whatsappCoroutineClass.declaredMethods.forEach { method ->
                if (method.name == "invokeSuspend") {
                    XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(null))
                }
            }
        }
    }

    // 4. Player sidebar clutter removal (Bookmark, Favorite, Playlist, Tutorial, Rate Us)
    runCatching {
        val menuClasses = listOf(
            "com.mxtech.videoplayer.ad.MenuHelper",
            "com.mxtech.videoplayer.MenuHelper",
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

private fun createNoOpProxy(interfaceClass: Class<*>, classLoader: ClassLoader): Any {
    val invocationHandler = InvocationHandler { _, method, _ ->
        val ret = method.returnType
        when {
            ret == Boolean::class.javaPrimitiveType || ret == java.lang.Boolean::class.java -> false
            ret == Int::class.javaPrimitiveType || ret == java.lang.Integer::class.java -> 0
            ret == Long::class.javaPrimitiveType || ret == java.lang.Long::class.java -> 0L
            ret == Float::class.javaPrimitiveType || ret == java.lang.Float::class.java -> 0f
            ret == Double::class.javaPrimitiveType || ret == java.lang.Double::class.java -> 0.0
            ret == Void.TYPE -> null
            ret == String::class.java -> ""
            java.util.List::class.java.isAssignableFrom(ret) || java.util.Collection::class.java.isAssignableFrom(ret) -> java.util.Collections.emptyList<Any>()
            java.util.Set::class.java.isAssignableFrom(ret) -> java.util.Collections.emptySet<Any>()
            java.util.Map::class.java.isAssignableFrom(ret) -> java.util.Collections.emptyMap<Any, Any>()
            ret.isArray -> java.lang.reflect.Array.newInstance(ret.componentType ?: Any::class.java, 0)
            ret.isInterface -> createNoOpProxy(ret, classLoader)
            else -> null
        }
    }
    return Proxy.newProxyInstance(classLoader, arrayOf(interfaceClass), invocationHandler)
}

