package io.github.nexfusion.nexfusion.twitter

import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.nexfusion.compat.XC_MethodReplacement
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.compat.XposedHelpers
import io.github.nexfusion.HookDsl
import io.github.nexfusion.IHook
import io.github.nexfusion.IHookCallback
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import java.lang.reflect.Method

// Ported from kveld9/kveld-extra-morphe-patches (GPL-3.0),
// patches/src/main/kotlin/app/morphe/patches/twitter/ - the upstream patches rewrite smali at build
// time against X 12.34.0-prod.01; this bundle installs equivalent runtime hooks against
// X 12.35.0-prod.01 (com.twitter.android).
//
// Not ported, with reasons: the upstream manifest and resource edits (permission strips, component
// disables, Sentry metadata removal) have no runtime equivalent. The Timeline UI, Post & Profile UI,
// Drawer Items, deep-link, share-link, font and other UI patches are separate upstream patches that
// this port does not cover yet.

val TwitterRemoveAds = patch(
    name = "Remove Ads",
    description = "Removes promoted posts, trends, and ads from timeline feeds.",
) {
    installTwitterAdSuppressor()
}

val TwitterUnlockVideoDownloads = patch(
    name = "Unlock Video Downloads",
    description = "Lets you save videos and GIFs with the app's built-in download option without Premium.",
) {
    installVideoDownloadUnlock()
}

val TwitterBlockUpdateScreen = patch(
    name = "Block Update Screen",
    description = "Blocks the outdated app screen and suppresses in-app update prompts on launch.",
) {
    installUpdateScreenBlock()
}

val TwitterBlockTelemetry = patch(
    name = "Block Telemetry & Trackers",
    description = "Redirects Braze, Sentry, Google Ads, Crashlytics and Firebase Analytics endpoints " +
        "to a blackhole address and drops Firebase Performance traces.",
) {
    installTelemetryFilters()
}

val TwitterRemovePremiumUpsell = patch(
    name = "Remove Premium Upsell",
    description = "Removes Premium subscription prompts and verification upsell surfaces across the app.",
) {
    neutralizeTwitterFlags(
        "subscriptions_upsells_premium_home_nav_enabled",
        "subscriptions_upsells_premium_home_nav_offer_enabled",
    )
    installProfileUpsellDisabler()
}

val TwitterControlVideoAutoScroll = patch(
    name = "Control Video Auto Scroll",
    description = "Prevents automatic scrolling to the next video in immersive view.",
    use = false,
) {
    installAutoScrollDisabler()
}

val TwitterDmDebloat = patch(
    name = "DM Debloat",
    description = "Hides call buttons in direct message conversations (disables outgoing DM calls).",
    use = false,
) {
    installDmCallButtonHider()
}

val TwitterPauseSearchSuggestions = patch(
    name = "Pause Search Suggestions",
    description = "Stops persisting search suggestions locally without breaking search.",
    use = false,
) {
    installSearchSuggestionsPauser()
}

val TwitterShowSensitiveMedia = patch(
    name = "Show Sensitive Media",
    description = "Displays sensitive media directly without requiring tap to reveal.",
    use = false,
) {
    installSensitiveMediaDisabler()
}

val TwitterRemoveViewCount = patch(
    name = "Remove View Count",
    description = "Removes public tweet view counters across timelines and tweet detail views.",
    use = false,
) {
    neutralizeTwitterFlags("view_counts_public_visibility_enabled")
}

val TwitterHideTimelineBookmarks = patch(
    name = "Hide Timeline Bookmarks",
    description = "Hides the inline bookmark button from tweet action bars in the timeline.",
    use = false,
) {
    neutralizeTwitterFlags("bookmarks_in_timelines_enabled")
}

val TwitterDisableImmersivePlayer = patch(
    name = "Disable Immersive Player",
    description = "Disables the full-screen vertical swipe video player in explore and timelines.",
    use = false,
) {
    neutralizeTwitterFlags("explore_relaunch_enable_immersive_player_across_twitter")
}

val TwitterSanitizeShareLinks = patch(
    name = "Sanitize share links",
    description = "Removes tracking query parameters (such as ?s=... and &t=...) when sharing tweets or copying links.",
) {
    runCatching {
        val clipboardManagerCls = classLoader.loadClass("android.content.ClipboardManager")
        for (m in clipboardManagerCls.declaredMethods) {
            if (m.name == "setPrimaryClip") {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val clip = param.args.firstOrNull() as? android.content.ClipData ?: return
                        if (clip.itemCount > 0) {
                            val text = clip.getItemAt(0)?.text?.toString() ?: return
                            if (text.contains("twitter.com") || text.contains("x.com")) {
                                val clean = text.replace(Regex("[?&](s|t|ref_src|ref_url|mx)=[a-zA-Z0-9_-]+"), "")
                                    .replace("?&", "?")
                                    .trimEnd('?', '&')
                                if (clean != text) {
                                    param.args[0] = android.content.ClipData.newPlainText(clip.description.label, clean)
                                }
                            }
                        }
                    }
                })
            }
        }
    }
    runCatching {
        val intentCls = android.content.Intent::class.java
        for (m in intentCls.declaredMethods) {
            if (m.name == "putExtra" && m.parameterTypes.getOrNull(0) == String::class.java && m.parameterTypes.getOrNull(1) == String::class.java) {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        if (key == android.content.Intent.EXTRA_TEXT) {
                            val text = param.args[1] as? String ?: return
                            if (text.contains("twitter.com") || text.contains("x.com")) {
                                val clean = text.replace(Regex("[?&](s|t|ref_src|ref_url|mx)=[a-zA-Z0-9_-]+"), "")
                                    .replace("?&", "?")
                                    .trimEnd('?', '&')
                                param.args[1] = clean
                            }
                        }
                    }
                })
            }
        }
    }
}

val TwitterDisableAutoRefresh = patch(
    name = "Disable timeline auto-refresh",
    description = "Stops the home timeline from refreshing and jumping to the top automatically.",
    use = false,
) {
    neutralizeTwitterFlags(
        "timeline_auto_refresh_enabled",
        "home_timeline_auto_refresh_policy_enabled",
        "android_timeline_auto_refresh_enabled",
        "timeline_auto_refresh_interval",
    )
}

val TwitterPatches = arrayOf(
    TwitterRemoveAds,
    TwitterUnlockVideoDownloads,
    TwitterBlockUpdateScreen,
    TwitterBlockTelemetry,
    TwitterRemovePremiumUpsell,
    TwitterControlVideoAutoScroll,
    TwitterDmDebloat,
    TwitterPauseSearchSuggestions,
    TwitterShowSensitiveMedia,
    TwitterRemoveViewCount,
    TwitterHideTimelineBookmarks,
    TwitterDisableImmersivePlayer,
    TwitterSanitizeShareLinks,
    TwitterDisableAutoRefresh,
)

// ---- Remove Ads ---------------------------------------------------------------------------------

/** X 12.38+'s unified presenter funnel descriptor. */
private const val PRESENTER_FUNNEL_DESCRIPTOR_12_38 =
    "Lcom/x/urt/b;->a:(ILcom/x/models/timelines/items/o0;Lcom/x/navigation/no;" +
        "Lcom/x/repositories/urt/w1;Lcom/x/urt/y0;)Lcom/x/presenter/a;"

/** X 12.37.2's unified presenter funnel descriptor. */
private const val PRESENTER_FUNNEL_DESCRIPTOR_12_37 =
    "Lcom/x/urt/b;->a:(ILcom/x/models/timelines/items/o0;Lcom/x/navigation/go;" +
        "Lcom/x/repositories/urt/u1;Lcom/x/urt/t0;)Lcom/x/presenter/a;"

/** X 12.35's unified presenter funnel; every feed resolves item presenters through it. */
private const val PRESENTER_FUNNEL_DESCRIPTOR =
    "Lcom/x/urt/b;->a:(ILcom/x/models/timelines/items/o0;Lcom/x/navigation/in;" +
        "Lcom/x/repositories/urt/r1;Lcom/x/urt/x0;)Lcom/x/presenter/a;"

/** The Quick Promote tray composable the upstream patch drops. */
private const val QUICK_PROMOTE_DESCRIPTOR =
    "Lcom/x/urt/items/post/quickpromote/b;->a:(Lkotlin/jvm/functions/Function0;" +
        "Lkotlin/jvm/functions/Function0;Lcom/x/quickpromote/j0;Landroidx/compose/runtime/Composer;I)V"

/** The Google Mobile Ads initializer coroutine the upstream patch fails early. */
private const val MOBILE_ADS_INIT_DESCRIPTOR =
    "Lcom/x/googlessp/init/b;->a:(Lkotlin/coroutines/jvm/internal/ContinuationImpl;)Ljava/lang/Object;"

private fun IHook.installTwitterAdSuppressor() {
    val filter = TwitterAdFilter(classLoader)
    val missing = filter.missingProbeClasses()
    if (missing.isNotEmpty()) {
        PatcherLog.info { "NexFusion: X promoted-item probes not found in this build: ${missing.joinToString()}" }
    }

    // 1. JSON-layer ad filtering (stable across releases, immune to obfuscated presenter shifts)
    val jsonEntryClasses = listOf(
        "com.twitter.model.json.timeline.urt.JsonTimelineEntry\$\$JsonObjectMapper",
        "com.twitter.model.json.timeline.urt.JsonTimelineModuleItem\$\$JsonObjectMapper"
    )
    for (jsonCls in jsonEntryClasses) {
        hookMethodsIfExists(jsonCls, "parse", "JSON timeline entry filter") {
            after = { param ->
                runCatching {
                    val result = param.result ?: return@runCatching
                    val entryId = runCatching {
                        XposedHelpers.getObjectField(result, "a") as? String
                    }.getOrNull() ?: runCatching {
                        XposedHelpers.callMethod(result, "c") as? String
                    }.getOrNull()
                    if (entryId != null) {
                        val isAd = entryId.contains("promoted", ignoreCase = true) ||
                                entryId.contains("rtb", ignoreCase = true) ||
                                entryId.startsWith("superhero", ignoreCase = true) ||
                                entryId.startsWith("eventsummary", ignoreCase = true) ||
                                entryId.startsWith("main-event-", ignoreCase = true) ||
                                entryId.startsWith("pivot", ignoreCase = true)
                        if (isAd) {
                            param.result = null
                        }
                    }
                }
            }
        }
    }

    // 2. UI-layer presenter funnel filtering
    val adSuppressorBlock: HookDsl<IHookCallback>.() -> Unit = {
        before = { param ->
            runCatching {
                val item = param.args.getOrNull(1)
                if (item != null && filter.isPromoted(item)) {
                    val presenter = filter.emptyPresenter()
                    if (presenter != null) {
                        val method = param.method
                        if (method !is Method || method.returnType.isInstance(presenter)) {
                            param.result = presenter
                        }
                    }
                }
            }
        }
    }
    val hookedFunnel = hookMethodsIfExists(
        "com.x.urt.b", "a", "presenter funnel",
        shape = { it.size == 5 && it[0] == java.lang.Integer.TYPE },
        block = adSuppressorBlock,
    )
    if (hookedFunnel == 0) {
        hookCatching(PRESENTER_FUNNEL_DESCRIPTOR_12_38, adSuppressorBlock)
        hookCatching(PRESENTER_FUNNEL_DESCRIPTOR_12_37, adSuppressorBlock)
        hookCatching(PRESENTER_FUNNEL_DESCRIPTOR, adSuppressorBlock)
    }
    hookCatching(QUICK_PROMOTE_DESCRIPTOR) {
        before = { param -> param.result = null }
    }
    hookMethodsIfExists("com.x.urt.items.post.quickpromote.b", "a", "quick promote tray") {
        before = { param -> param.result = null }
    }
    hookCatching(MOBILE_ADS_INIT_DESCRIPTOR) {
        before = { param -> param.result = java.lang.Boolean.FALSE }
    }
}

// ---- Unlock Video Downloads ---------------------------------------------------------------------

private fun IHook.installVideoDownloadUnlock() {
    // Upstream forces the Premium check to true inside download call sites;
    // the gate marks those methods, so the override only applies while a download surface builds.
    val siteBlock: HookDsl<IHookCallback>.() -> Unit = {
        before = { TwitterDownloadGate.enterSite() }
        after = { TwitterDownloadGate.exitSite() }
    }
    hookMethodsIfExists(
        "com.x.media.n", "b", "media viewer download",
        shape = { it.size == 1 && it[0].name.startsWith("com.x.models.") }, block = siteBlock,
    )
    hookMethodsIfExists(
        "com.x.urt.items.post.k5", "invoke", "post menu download",
        shape = { it.isEmpty() }, block = siteBlock,
    )
    // 12.37+: video tab download method 'f'
    hookMethodsIfExists(
        "com.x.video.tab.d0", "f", "video tab download 12.37+",
        shape = { it.size == 1 && it[0].name.startsWith("com.x.video.tab.") }, block = siteBlock,
    )
    // 12.35: video tab download method 'e'
    hookMethodsIfExists(
        "com.x.video.tab.d0", "e", "video tab download",
        shape = { it.size == 1 && it[0].name.startsWith("com.x.video.tab.") }, block = siteBlock,
    )
    hookMethodsIfExists(
        "com.x.media.download.d", "c", "media download dispatch",
        block = siteBlock,
    )

    // X 12.35 subscription check
    hookCatching("Lcom/x/subscriptions/q;->g:()Z") {
        after = { param ->
            if (param.result == false && TwitterDownloadGate.isInsideSite()) param.result = true
        }
    }
    // X 12.37+ subscription check (overriding boolean accessors while inside download flow)
    for (methodName in listOf("c", "f", "h", "i", "l", "m", "n", "o", "p")) {
        hookMethodsIfExists(
            "com.x.subscriptions.r",
            methodName,
            "subscriptions 12.37+ premium check ($methodName)",
            shape = { it.isEmpty() },
        ) {
            after = { param ->
                if (param.result == false && TwitterDownloadGate.isInsideSite()) param.result = true
            }
        }
    }

    // The media models gate the save action on their own isDownloadable flag; upstream forces it on
    // in every constructor return path.
    // X 12.37+:
    installDownloadableFlag("com.x.models.v4", "j", "video")
    installDownloadableFlag("com.x.models.p4", "g", "GIF")
    installDownloadableFlag("com.x.models.s4", "g", "video meta")
    // X 12.35 - 12.38:
    installDownloadableFlag("com.x.models.w4", "j", "video (legacy)")
    installDownloadableFlag("com.x.models.q4", "g", "GIF (legacy)")
    installDownloadableFlag("com.x.models.t4", "g", "video meta (legacy)")
}

private fun IHook.installDownloadableFlag(className: String, fieldName: String, label: String) {
    var warned = false
    hookAllConstructorsIfExists(className, "$label isDownloadable") {
        after = { param ->
            val media = param.thisObject
            if (media != null) {
                runCatching { XposedHelpers.setBooleanField(media, fieldName, true) }
                    .onFailure { error ->
                        if (!warned) {
                            warned = true
                            PatcherLog.info(error) { "NexFusion: could not force the $label isDownloadable flag" }
                        }
                    }
            }
        }
    }
}

// ---- Block Update Screen ------------------------------------------------------------------------

/** The outdated-screen gate returning Unit when updates are allowed. */
private const val BLOCKING_UPDATE_DESCRIPTOR =
    "Lcom/apollo/n;->invokeSuspend:(Ljava/lang/Object;)Ljava/lang/Object;"

/** The in-app update prompt call the upstream patch turns into a no-op. */
private const val IN_APP_UPDATE_DESCRIPTOR = "Lcom/x/android/appupdate/f;->a:(Lcom/x/android/appupdate/o;)V"

private fun IHook.installUpdateScreenBlock() {
    val unit = appUnit()
    if (unit == null) {
        PatcherLog.info { "NexFusion: kotlin.Unit unavailable; the outdated app screen cannot be stubbed" }
    } else {
        hookCatching(BLOCKING_UPDATE_DESCRIPTOR) {
            before = { param -> param.result = unit }
        }
    }
    hookCatching(IN_APP_UPDATE_DESCRIPTOR) {
        before = { param -> param.result = null }
    }
    hookMethodsIfExists("com.x.android.appupdate.l", "a", "in-app update 12.37+") {
        before = { param -> param.result = null }
    }
}

// ---- Remove Premium Upsell ----------------------------------------------------------------------

private fun IHook.installProfileUpsellDisabler() {
    // Upstream pins the two trailing boolean constructor parameters of the profile upsell holder
    // (com.x.profile.upsell.k, ctor (ZZZ)V); the (IZ)V synthetic funnels into it.
    hookAllConstructorsIfExists("com.x.profile.upsell.k", "profile upsell") {
        before = { param ->
            val types = param.parameterTypes
            if (types.size == 3 && types.all { it == Boolean::class.javaPrimitiveType }) {
                param.args[1] = false
                param.args[2] = false
            }
        }
    }
}

// ---- Control Video Auto Scroll ------------------------------------------------------------------

private fun IHook.installAutoScrollDisabler() {
    // PersistentVideoSettings (com.x.media.playback.settings.persistent.h): the constructor stores
    // its first four boolean parameters into fields a..d; c is isAutoAdvanceEnabled, i.e. args[2].
    hookAllConstructorsIfExists("com.x.media.playback.settings.persistent.h", "video auto scroll") {
        before = { param ->
            val types = param.parameterTypes
            if (types.size == 7 && (0 until 4).all { types[it] == Boolean::class.javaPrimitiveType }) {
                param.args[2] = false
            }
        }
    }
}

// ---- DM Debloat ---------------------------------------------------------------------------------

private fun IHook.installDmCallButtonHider() {
    // Upstream pins the show-calls boolean (2nd parameter) of the DM chat composable to false.
    hookMethodsIfExists(
        "com.x.dm.chat.composables.r0",
        "d",
        "DM call buttons",
        shape = {
            it.size == 7 && it[0].name == "com.x.dms.components.chat.v4" &&
                it[1] == Boolean::class.javaPrimitiveType
        },
    ) {
        before = { param -> param.args[1] = false }
    }
}

// ---- Pause Search Suggestions -------------------------------------------------------------------

/** The recent-search persist suspend body (fingerprinted by "search_recent_v3" upstream). */
private const val SEARCH_PERSIST_DESCRIPTOR =
    "Lcom/x/repositories/search/p;->c:(Lcom/x/models/search/r;" +
        "Lkotlin/coroutines/jvm/internal/SuspendLambda;)Ljava/lang/Object;"

private fun IHook.installSearchSuggestionsPauser() {
    // Upstream returns kotlin.Unit at method entry of the persist call, so search still completes
    // without writing the suggestion store.
    val unit = appUnit()
    if (unit == null) {
        PatcherLog.info { "NexFusion: kotlin.Unit unavailable; search suggestions cannot be paused" }
    } else {
        hookCatching(SEARCH_PERSIST_DESCRIPTOR) {
            before = { param -> param.result = unit }
        }
    }
}

// ---- Show Sensitive Media -----------------------------------------------------------------------

/** X 12.35's media visibility wrapper; its blur field (a) is left null by the upstream patch. */
private const val BLUR_WRAPPER_CLASS = "com.x.models.interstitial.h"

/** The blur interstitial type the wrapper stores in field a. */
private const val BLUR_INTERSTITIAL_CLASS = "com.x.models.interstitial.e"

private fun IHook.installSensitiveMediaDisabler() {
    // Upstream returns from every constructor right after the super call, so the blur field keeps
    // its null default. Runtime equivalent: null the blur constructor argument before it is stored.
    hookAllConstructorsIfExists(BLUR_WRAPPER_CLASS, "sensitive media blur") {
        before = { param ->
            val types = param.parameterTypes
            for (index in param.args.indices) {
                if (types.getOrNull(index)?.name == BLUR_INTERSTITIAL_CLASS) param.args[index] = null
            }
        }
    }

    // Compose sensitive-media presenter: null out blur in MediaVisibilityResults constructors and getter hooks
    hookAllConstructorsIfExists("com.x.models.interstitial.MediaVisibilityResults", "MediaVisibilityResults blur") {
        before = { param ->
            for (i in param.args.indices) {
                param.args[i] = null
            }
        }
    }
    try {
        val visibilityClass = classLoader.loadClass("com.x.models.interstitial.MediaVisibilityResults")
        for (m in visibilityClass.declaredMethods) {
            if (m.name == "getBlurImageInterstitial" || (m.name.startsWith("get") && m.returnType.name.contains("BlurImageInterstitial"))) {
                XposedBridge.hookMethod(m, object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? = null
                })
            }
        }
    } catch (_: Throwable) {}
}

