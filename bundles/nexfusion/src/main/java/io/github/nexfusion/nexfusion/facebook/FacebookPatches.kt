package io.github.nexfusion.nexfusion.facebook

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import org.luckypray.dexkit.wrap.DexMethod
import java.io.IOException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Locale

// Ported and adapted from SysAdminDoc/Hushfacebook (GPL-3.0), supporting Facebook 583.0.0.39.55,
// 582.0.0.50.54 and 581.0.0.45.54.
//
// Upstream patches Facebook's dex at build time. This bundle installs runtime hooks instead: the
// classes Facebook keeps their names for are loaded directly, and the classes Redex renames are
// resolved with the DexKit finders in FacebookFingerprints.kt. A patch throws when none of its
// anchors is present, so the patcher reports it as failed instead of silently doing nothing.

/** android.view.WindowManager.LayoutParams.FLAG_SECURE. */
private const val FLAG_SECURE = 0x2000

private fun withoutSecure(flags: Int): Int = flags and FLAG_SECURE.inv()

private fun clearSecure(params: Any?) {
    val layoutParams = params as? WindowManager.LayoutParams ?: return
    layoutParams.flags = withoutSecure(layoutParams.flags)
}

/**
 * Replaces a hooked method's body with `return null`, right for both void and object returns.
 * Lazy: the Xposed callback classes only exist on the device, not in the unit test JVM.
 */
private val NOOP by lazy {
    object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam): Any? = null
    }
}

/**
 * Neuters every void method of the class, upstream's treatment for its ad telemetry classes and
 * prefetch schedulers: each one is a job, controller or service whose only methods enqueue or
 * report, so returning at once stops the work outright. Throws when the class is not in this build.
 */
private fun neuterVoidMethods(classLoader: ClassLoader, className: String): Int {
    val clazz = classLoader.loadClass(className)
    var hooked = 0
    for (method in clazz.declaredMethods) {
        if (method.returnType != Void.TYPE || Modifier.isAbstract(method.modifiers)) continue
        XposedBridge.hookMethod(method, NOOP)
        hooked++
    }
    return hooked
}

val AllowScreenshots = patch(
    name = "Allow screenshots",
    description = "Clears FLAG_SECURE so screenshots and screen recording work everywhere.",
) {
    val hook = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            when (param.method.name) {
                "setFlags", "addFlags" -> {
                    val flags = param.args.getOrNull(0) as? Int ?: return
                    param.args[0] = withoutSecure(flags)
                }
                "setAttributes" -> clearSecure(param.args.getOrNull(0))
                "addView", "updateViewLayout" -> clearSecure(param.args.getOrNull(1))
            }
        }
    }

    // Window and its PhoneWindow implementation; PhoneWindow overrides setAttributes.
    for (className in listOf("android.view.Window", "com.android.internal.policy.PhoneWindow")) {
        val window = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
        window.declaredMethods
            .filter { it.matchesWindowFlagsMethod() }
            .forEach { XposedBridge.hookMethod(it, hook) }
    }

    // WindowManagerImpl receives the LayoutParams the window finally gets.
    runCatching { classLoader.loadClass("android.view.WindowManagerImpl") }
        .getOrNull()
        ?.declaredMethods
        ?.filter { method ->
            method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == View::class.java &&
                method.parameterTypes[1] == ViewGroup.LayoutParams::class.java &&
                (method.name == "addView" || method.name == "updateViewLayout")
        }
        ?.forEach { XposedBridge.hookMethod(it, hook) }

    PatcherLog.info { "NexFusion: Facebook FLAG_SECURE hooks installed" }
}

val BlockScreenshotDetection = patch(
    name = "Block screenshot detection",
    description = "Stops Facebook from noticing screenshots and screen recordings.",
) {
    var observerHooked = false
    var frameworkHooked = 0

    // Upstream's ScreenshotDetectionAnchors.kt: the photo library observer every screenshot
    // detector shares keeps its class name in release builds. Its onChange looks at each new
    // picture; returning at once there hides every screenshot from Facebook.
    runCatching {
        val observer = classLoader.loadClass("com.facebook.screenshot.ScreenshotContentObserver")
        val onChange = observer.declaredMethods.single {
            it.name == "onChange" && it.returnType == Void.TYPE &&
                it.parameterTypes.contentEquals(arrayOf(java.lang.Boolean.TYPE, Uri::class.java))
        }
        XposedBridge.hookMethod(onChange, NOOP)
        observerHooked = true
    }.onFailure { PatcherLog.info(it) { "NexFusion: Facebook screenshot observer hook skipped" } }

    // Android 14+ activity screen capture callbacks; Android 15+ screen recording callbacks.
    if (Build.VERSION.SDK_INT >= 34) {
        for (className in listOf("android.app.Activity", "androidx.activity.ComponentActivity")) {
            val activity = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            activity.declaredMethods
                .filter { it.name == "registerScreenCaptureCallback" && it.parameterTypes.size == 2 }
                .forEach {
                    XposedBridge.hookMethod(it, NOOP)
                    frameworkHooked++
                }
        }
    }
    if (Build.VERSION.SDK_INT >= 35) {
        runCatching { classLoader.loadClass("android.view.WindowManagerImpl") }
            .getOrNull()
            ?.declaredMethods
            ?.filter { it.name == "addScreenRecordingCallback" || it.name == "removeScreenRecordingCallback" }
            ?.forEach {
                XposedBridge.hookMethod(it, NOOP)
                frameworkHooked++
            }
    }

    if (!observerHooked && frameworkHooked == 0) {
        throw IllegalStateException("Block screenshot detection: no Facebook observer or framework call was found")
    }
    PatcherLog.info { "NexFusion: Facebook screenshot detection hooks installed (observer=$observerHooked, framework=$frameworkHooked)" }
}

val HideSponsoredPosts = patch(
    name = "Hide sponsored posts",
    description = "Hides sponsored posts and other promoted content in the feed.",
) {
    // Upstream's feed chokepoint (FeedEdge.kt / FeedFilterHookPatch.kt): every news feed edge
    // passes the funnel, and the edge's story category is the kept GraphQLFeedStoryCategory
    // enum. On 583 the found method is the static body the single-add wrapper delegates to, so
    // the edge does not sit at the wrapper's argument position; the first argument that is an
    // edge is the one to read. Upstream hands both to its extension, which drops the SPONSORED
    // and PROMOTION constants; this port reads the category's server value through its
    // toString(), which is what the enum stores as `serverValue` (the constants themselves are
    // Redex-renamed, so Enum.name would be an unstable A00-style name).
    val edgeClass = classLoader.loadClass("com.facebook.graphql.model.GraphQLFeedUnitEdge")
    val categoryClass = classLoader.loadClass("com.crossapp.graphql.facebook.enums.GraphQLFeedStoryCategory")
    val categoryGetter = edgeClass.declaredMethods.singleOrNull {
        it.parameterTypes.isEmpty() && it.returnType == categoryClass
    } ?: throw IllegalStateException("Hide sponsored posts: the edge's story category getter was not found")
    categoryGetter.isAccessible = true

    var announced = false
    val hookFunnel: (XC_MethodHook.MethodHookParam) -> Unit = { param ->
        if (!announced) {
            announced = true
            PatcherLog.info { "NexFusion: Facebook sponsored posts filter installed on ${param.method.declaringClass.name}" }
        }
        val edge = param.args.firstOrNull { edgeClass.isInstance(it) }
        if (edge != null) {
            val categoryName = runCatching {
                categoryGetter.invoke(edge)?.toString()
            }.getOrNull()
            if (categoryName != null && (
                categoryName.contains("SPONSORED") ||
                categoryName.contains("PROMOTION") ||
                categoryName.contains("INJECTED") ||
                categoryName.contains("SHOWCASE") ||
                categoryName.contains("META_AI") ||
                categoryName.contains("GEN_AI") ||
                categoryName.contains("GENAI")
            )) {
                // A refused edge is an outcome the feed already handles: it logs "Edge not added
                // to FUC" and carries on.
                param.result = false
            }
        }
    }

    ::feedFunnel.hookMethod { before(hookFunnel) }
    runCatching {
        ::feedFunnelMethods.dexMethodList.forEach { dex ->
            dex.hookMethod { before(hookFunnel) }
        }
    }
}

val HideSponsoredReels = patch(
    name = "Hide sponsored reels",
    description = "Holds the Reels and Watch ad pool, cancels the reels and ad-break ad fetches, and blocks the client-side ad splices.",
) {
    var installed = 0
    // The anchors the patch stands on: the pool vend hold and the ad fetch cancellations. When
    // none of them is present the patch throws below instead of reporting success; the blocks
    // after them are refused individually and named in the log.
    var core = 0

    // Upstream's ReelsAdPool.kt: the pool's vends answer null when they cannot hand out an ad, so
    // holding them back makes the loader take the next organic reel. A vend is an instance method
    // that hands back an object; arrays and the like are other holders of the literal and are
    // left alone. No other class logs the literal. The hold is logged for each vend so a build
    // that moved one is diagnosable.
    runCatching {
        for (dex in ::reelsAdPoolVends.dexMethodList) {
            val method = runCatching { dex.toMethod() }.getOrNull() ?: continue
            if (Modifier.isStatic(method.modifiers)) continue
            val returnType = method.returnType
            if (returnType == Void.TYPE || returnType.isPrimitive || returnType.isArray) continue
            dex.hookMethod { before { param -> param.result = null } }
            installed++
            core++
            PatcherLog.info { "NexFusion: holding back Reels ad pool vend ${method.declaringClass.name}->${method.name}" }
        }
    }.onFailure { PatcherLog.info(it) { "NexFusion: the Reels ad pool was not found" } }

    // The ad fetches: the Reels tab's idle video query, the extended break's query and a profile's
    // reels query. Upstream returns the profile query early and answers the two GraphQL-executor
    // calls with a failed future; a runtime hook cannot replace one call inside a method, so each
    // void runner returns early instead. A holder that returns a value builds the query rather
    // than fetching it and is left running, named in the log.
    for ((marker, fetch) in listOf(
        "FBFetchReelsVideoAdsQuery" to ::reelsVideoAdsFetch,
        "InstreamExtendedBreakQuery" to ::instreamBreakFetch,
        "ProfileReelsAsyncAdsQuery" to ::profileReelsAdsFetch,
    )) {
        var perMarker = 0
        var builders = 0
        runCatching {
            for (dex in fetch.dexMethodList) {
                val method = runCatching { dex.toMethod() }.getOrNull() ?: continue
                if (method.returnType != Void.TYPE) {
                    builders++
                    continue
                }
                dex.hookMethod { before { param -> param.result = null } }
                installed++
                core++
                perMarker++
                PatcherLog.info { "NexFusion: cancelling the reels ad fetch in ${method.declaringClass.name}->${method.name}" }
            }
        }.onFailure { PatcherLog.info(it) { "NexFusion: reels ad fetch \"$marker\" was not found" } }
        if (perMarker == 0) {
            PatcherLog.info {
                "NexFusion: reels ad fetch \"$marker\" was not found" +
                    if (builders > 0) " (its $builders value-returning holder(s) build the query instead)" else ""
            }
        }
    }

    // Banners over a reel and mid-rolls are fetched while the reel plays. Upstream answers the
    // banner helper and every future-returning method of the ad-break server API with a failed
    // future, and each caller handles it as a network error: it stores no ad, and no query goes
    // out. The same failed future is handed back here.
    runCatching {
        val settableFuture = classLoader.loadClass("com.google.common.util.concurrent.SettableFuture")
        val create = settableFuture.getMethod("create")
        val setException = settableFuture.getMethod("setException", Throwable::class.java)
        val hold = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val future = create.invoke(null)
                setException.invoke(future, IOException("Reels ad fetch blocked"))
                param.result = future
            }
        }
        XposedBridge.hookMethod(::adBreakBannerFetch.method, hold)
        for (dex in ::adBreakVideoFetches.dexMethodList) {
            XposedBridge.hookMethod(dex.toMethod(), hold)
        }
        installed++
        PatcherLog.info { "NexFusion: the banner query helper and the video ad queries answer a failed future" }
    }.onFailure { PatcherLog.info(it) { "NexFusion: the ad-break queries were not found" } }

    // The client-side splices of ads into the live item collection. Upstream blocks four; each is
    // a void method that inserts an ad and tells the controller its data changed, so returning at
    // once is safe. Its POE render task holds no string literal and cannot be anchored at runtime,
    // so three are resolved here, and a miss is named in the log.
    for ((marker, splice) in listOf(
        "VideoHomeDataControllerImpl.maybeInsertAds" to ::insertAdsSplice,
        "VideoHomeDataControllerAdsUtil.maybeInsertFbShortsRealtimeIntentItem" to ::realtimeIntentAdInsert,
        "VideoHomeDataControllerSfdAdsUtil" to ::sfdAdInsert,
    )) {
        var perMarker = 0
        runCatching {
            for (dex in splice.dexMethodList) {
                val method = runCatching { dex.toMethod() }.getOrNull() ?: continue
                if (method.returnType != Void.TYPE) continue
                dex.hookMethod { before { param -> param.result = null } }
                installed++
                perMarker++
                PatcherLog.info { "NexFusion: blocking the ad splice in ${method.declaringClass.name}->${method.name}" }
            }
        }.onFailure { PatcherLog.info(it) { "NexFusion: the ad splice \"$marker\" was not found" } }
        if (perMarker == 0) PatcherLog.info { "NexFusion: the ad splice \"$marker\" was not found" }
    }

    // The ad-break lookup retries a failed fetch each second while the reel plays. Upstream makes
    // its tick answer -1, the value for a reel with no media, which stops the poller; the tick is
    // shared with sibling states, so only the deferred-card state is answered, and the others keep
    // their own. An ambiguity leaves the poller running, named in the log.
    runCatching {
        val state = ::deferredCardState.clazz
        var owner: Class<*>? = state
        var tick: Method? = null
        while (owner != null && tick == null) {
            val ticks = owner.declaredMethods.filter { method ->
                method.returnType == java.lang.Long.TYPE && method.parameterCount == 2 &&
                    method.parameterTypes[1] == Integer.TYPE &&
                    !Modifier.isAbstract(method.modifiers) && !Modifier.isNative(method.modifiers)
            }
            check(ticks.size <= 1) { "${owner.name} declares ${ticks.size} (state, int) ticks" }
            tick = ticks.singleOrNull()
            owner = owner.superclass
        }
        val found = tick ?: throw IllegalStateException("no ad-break tick in ${state.name}'s hierarchy")
        XposedBridge.hookMethod(found, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.args[0]?.let(state::isInstance) == true) param.result = -1L
            }
        })
        installed++
    }.onFailure { PatcherLog.info(it) { "NexFusion: the deferred-card ad-break poller was left running" } }

    if (core == 0) {
        throw IllegalStateException("Hide sponsored reels: no Reels ad pool vend and no ad fetch anchor was found")
    }
    PatcherLog.info { "NexFusion: Reels ad defenses installed ($installed hooks)" }
}

/**
 * Upstream's AD_TELEMETRY and AD_ID_JOBS: ad measurement that runs whether or not an ad is shown.
 * Every class keeps its real name, which upstream mapped on 582 and the 581 audit confirms.
 */
private val AD_TELEMETRY_CLASSES = listOf(
    "com.facebook.ads.screenshot.AdsScreenshotController",
    "com.facebook.ads.AdsScreenshotDetector",
    "com.facebook.feed.platformads.AppInstallTrackerScheduler",
    "com.facebook.feed.platformads.AppInstallService",
    "com.facebook.attribution.LatStatusJob",
    "com.facebook.privacysandbox.PrivacySandboxCapabilitiesChecker",
    "com.facebook.privacysandbox.protectedaudience.TestPACustomAudienceAppJob",
)

/** Upstream's AD_PREFETCH_SCHEDULERS: the background jobs that download ads before they show. */
private val AD_PREFETCH_CLASSES = listOf(
    "com.facebook.feed.push.adschannelbackgroundprefetch.FeedAdsChannelBackgroundPrefetchInitializerAppJob",
    "com.facebook.feed.push.adschannelemergingsurfaceprefetch.FeedAdsChannelEmergingSurfacePrefetchInitializerAppJob",
    "com.facebook.video.videohome.prefetching.ads.background.ReelsAdsBackgroundPrefetchAppJob",
    "com.facebook.stories.features.ads.prefetch.StoryViewerAdsPrefetchAppInitializationController",
    "com.facebook.stories.features.ads.prefetch.StoryViewerAdsPrefetchController",
    "com.facebook.addelivery.deliveryvalidation.cachedadsvalidator.NewsFeedAdCacheSyncInitializerAppJob",
    "com.facebook.feed.ads.mlranker.MlRankerAppJob",
)

val BlockTelemetry = patch(
    name = "Block telemetry and ad tracking",
    description = "Blocks ad telemetry, background ad prefetching, and advertising identifier reporting.",
) {
    var installed = 0

    for (className in AD_TELEMETRY_CLASSES + AD_PREFETCH_CLASSES) {
        val count = runCatching { neuterVoidMethods(classLoader, className) }
            .onFailure { PatcherLog.info(it) { "NexFusion: ad telemetry class not in this build: $className" } }
            .getOrDefault(0)
        installed += count
    }

    // Upstream's AD_ID_SENDERS: one void method loading exactly the marker reports an identifier.
    // When the build moved it, the send is left running and named in the log, like upstream does.
    fun cancelSender(marker: String, methods: List<DexMethod>) {
        val senders = methods.mapNotNull { dex ->
            runCatching { dex.toMethod() }.getOrNull()?.takeIf { it.returnType == Void.TYPE }
        }
        if (senders.size != 1) {
            PatcherLog.info { "NexFusion: \"$marker\" has ${senders.size} void senders (expected 1), leaving that send running" }
            return
        }
        XposedBridge.hookMethod(senders.single(), NOOP)
        installed++
    }
    cancelSender("ReportAdvertiserIDMutation", ::advertiserIdReportSender.dexMethodList)
    cancelSender("fb4a_launch_custom_data", ::launchCustomDataSender.dexMethodList)
    cancelSender(
        "https://www.facebook.com/privacy_sandbox/mobile/register/source?tracking_data=",
        ::privacySandboxRegisterSender.dexMethodList,
    )
    cancelSender("app_new_install", ::newInstallSender.dexMethodList)

    // The attribution ID provider other apps on the phone ask for the advertising ID. Its class
    // name is kept; upstream answers its query with one row of nulls instead of the ID.
    runCatching {
        val provider = classLoader.loadClass("com.facebook.katana.provider.AttributionIdProvider\$Impl")
        val queries = provider.declaredMethods.filter { method ->
            method.name == "query" && method.returnType == Cursor::class.java &&
                method.parameterTypes.contentEquals(
                    arrayOf(
                        Uri::class.java, Array<String>::class.java, String::class.java,
                        Array<String>::class.java, String::class.java,
                    ),
                )
        }
        check(queries.size == 1) { "expected one query(...), found ${queries.size}" }
        XposedBridge.hookMethod(queries.single(), NO_AD_ID_QUERY)
        installed++
    }.onFailure { PatcherLog.info(it) { "NexFusion: the attribution ID provider hook was skipped" } }

    if (installed == 0) {
        throw IllegalStateException("Block telemetry and ad tracking: no telemetry anchor was found")
    }
    PatcherLog.info { "NexFusion: ad telemetry, prefetch and advertising ID hooks installed ($installed hooks)" }
}

/** Answers the attribution provider's query the way it answers when Google Play has no ID. */
private val NO_AD_ID_QUERY by lazy {
    object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam): Any {
            val cursor = MatrixCursor(arrayOf("aid", "androidid", "limit_tracking"))
            cursor.addRow(listOf(null, null, null))
            return cursor
        }
    }
}

/** Facebook hosts that stay in the app, as upstream keeps Facebook pages in it. */
private val IN_APP_HOSTS = listOf("facebook.com", "fb.com", "messenger.com")

val OpenLinksExternally = patch(
    name = "Open links in external browser",
    description = "Bypasses Facebook's in-app browser to open web links directly in your default browser.",
) {
    // Upstream's OpenLinksExternallyPatch.kt hooks Facebook's two in-app browsers. Both class
    // names are kept and declared in the manifest. The redirect runs after each activity's own
    // lifecycle has finished, so the framework never sees a skipped super call.
    var hooked = 0
    val redirect = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val activity = param.thisObject as? Activity ?: return
            val intent = if (param.method.name == "onNewIntent") {
                param.args.getOrNull(0) as? Intent
            } else {
                activity.intent
            }
            openExternally(activity, intent)
        }
    }
    for (className in listOf(
        "com.facebook.browser.litev2.lite.BrowserLiteDIActivity",
        "com.facebook.browser.lite.BrowserLiteActivity",
    )) {
        val browser = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
        for (method in browser.declaredMethods) {
            val hookable = when (method.name) {
                "onCreate" -> method.parameterTypes.contentEquals(arrayOf(Bundle::class.java))
                "onNewIntent" -> method.parameterTypes.contentEquals(arrayOf(Intent::class.java))
                else -> false
            }
            if (hookable) {
                XposedBridge.hookMethod(method, redirect)
                hooked++
            }
        }
    }
    if (hooked == 0) {
        throw IllegalStateException("Open links in external browser: no in-app browser activity was found")
    }
    PatcherLog.info { "NexFusion: $hooked in-app browser lifecycle hooks installed" }
}

/** Opens the intent's web link in the user's browser, unless it is a Facebook page. */
private fun openExternally(activity: Activity, intent: Intent?) {
    val uri = intent?.data?.let(::resolveRedirect) ?: return
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return
    if (scheme != "http" && scheme != "https") return
    val host = uri.host?.lowercase(Locale.ROOT) ?: return
    if (IN_APP_HOSTS.any { host == it || host.endsWith(".$it") }) return
    val external = Intent(Intent.ACTION_VIEW, uri)
    if (runCatching { activity.startActivity(external) }.isSuccess) {
        PatcherLog.info { "NexFusion: opened $host in the external browser" }
        runCatching { activity.finish() }
    }
}

/** Unwraps l.facebook.com's outbound redirector, whose `u` parameter holds the real link. */
private fun resolveRedirect(uri: Uri): Uri {
    val host = uri.host?.lowercase(Locale.ROOT) ?: return uri
    if (host != "l.facebook.com" && host != "lm.facebook.com") return uri
    val target = uri.getQueryParameter("u") ?: return uri
    val parsed = runCatching { Uri.parse(target) }.getOrNull() ?: return uri
    return if (parsed.scheme == "http" || parsed.scheme == "https") parsed else uri
}

/**
 * Removes the story viewer's ad cards. Upstream (Hushfacebook ads/sponsoredstories) returns each
 * bucket source's own input list unchanged, which is what Facebook does when a source has nothing
 * to contribute. The four sources are independent, so each is installed on its own and the ones a
 * build still ships keep working when another moved.
 */
val HideSponsoredStories = patch(
    name = "Hide sponsored stories",
    description = "Removes ad cards from the story viewer, so swiping through stories shows only what people posted.",
) {
    val immutableList = "com.google.common.collect.ImmutableList"
    var installed = 0
    fun install(source: String, methods: List<DexMethod>) {
        for (dex in methods) {
            val method = runCatching { dex.toMethod() }.getOrNull() ?: continue
            if (method.returnType.name != immutableList) continue
            val listIndex = method.parameterTypes.indexOfLast { it.name == immutableList }
            if (listIndex < 0) continue
            dex.hookMethod {
                before { param -> param.result = param.args[listIndex] }
            }
            installed++
            PatcherLog.info {
                "NexFusion: holding back Facebook story ads in $source " +
                    "${method.declaringClass.name}->${method.name}"
            }
        }
    }
    install("clash resolver", ::inorganicClashStoryBuckets.dexMethodList)
    install("ad bucket source", ::adBucketDataSourceStoryBuckets.dexMethodList)
    install("paginating source", ::adPaginatingStoryBuckets.dexMethodList)
    install("mid-card source", ::storyViewerMidCardBuckets.dexMethodList)
    check(installed > 0) { "Hide sponsored stories: none of the four story bucket sources was found" }
}

/**
 * Removes the feed's rows of Reels. Upstream files them under the GraphQLFeedStoryCategory names
 * below and also hooks the pre-EOF injector, which builds a Reels row without passing the feed
 * funnel. Both paths are covered here; a Reel a friend posts is an ordinary post and stays.
 */
val HideFeedReels = patch(
    name = "Hide Reels in the feed",
    description = "Removes the rows of Reels between posts and the Reels added where the feed ends. " +
        "A Reel a friend posts stays.",
    use = false,
) {
    val edgeClass = classLoader.loadClass("com.facebook.graphql.model.GraphQLFeedUnitEdge")
    val categoryClass = classLoader.loadClass("com.crossapp.graphql.facebook.enums.GraphQLFeedStoryCategory")
    val categoryGetter = edgeClass.declaredMethods.singleOrNull {
        it.parameterTypes.isEmpty() && it.returnType == categoryClass
    } ?: throw IllegalStateException("Hide Reels in the feed: the edge's story category getter was not found")
    categoryGetter.isAccessible = true
    val reelsCategories = setOf("FB_SHORTS", "FB_SHORTS_FALLBACK", "END_OF_FEED_REELS")
    var installed = 0

    val reelsHook: (XC_MethodHook.MethodHookParam) -> Unit = { param ->
        val edge = param.args.firstOrNull { edgeClass.isInstance(it) }
        if (edge != null) {
            val categoryName = runCatching {
                categoryGetter.invoke(edge)?.toString()
            }.getOrNull()
            if (categoryName in reelsCategories) {
                param.result = false
            }
        }
    }

    ::feedFunnel.hookMethod { before(reelsHook) }
    runCatching {
        ::feedFunnelMethods.dexMethodList.forEach { dex ->
            dex.hookMethod { before(reelsHook) }
        }
    }
    installed++

    for (dex in ::preEofReelsInjectors.dexMethodList) {
        val method = runCatching { dex.toMethod() }.getOrNull() ?: continue
        if (!method.name.startsWith("injectPreEofIfuEdge\$")) continue
        if (method.parameterTypes.isNotEmpty() || method.returnType != Void.TYPE) continue
        dex.hookMethod { before { param -> param.result = null } }
        installed++
        PatcherLog.info {
            "NexFusion: skipping the pre-EOF Reels row in ${method.declaringClass.name}->${method.name}"
        }
    }

    check(installed > 1) { "Hide Reels in the feed: the pre-EOF Reels injector was not found" }
    PatcherLog.info { "NexFusion: Facebook feed Reels filter installed" }
}

/**
 * Stops Facebook from adding what you search for or pages you open from search to your search history.
 * Ported and adapted from SysAdminDoc/Hushfacebook #196, #221.
 */
val DontSaveSearchHistory = patch(
    name = "Don't save search history",
    description = "Stops Facebook from adding what you search for or pages you open from search to your search history.",
    use = false,
) {
    var count = 0
    runCatching {
        ::searchTypeaheadAddRecentSearchMethod.hookMethod {
            before { param ->
                val args = param.args
                val boolIndices = args.indices.filter { args[it] is Boolean }
                if (boolIndices.isNotEmpty()) {
                    param.args[boolIndices.first()] = true
                }
            }
        }
        count++
    }
    runCatching {
        ::analyticsEventFactoryMethod.hookMethod {
            before { param ->
                if (param.args.getOrNull(1) == "profile_engagement") {
                    val field = param.thisObject.javaClass.declaredFields.firstOrNull {
                        Modifier.isStatic(it.modifiers) &&
                            (param.method as? Method)?.returnType?.isAssignableFrom(it.type) == true
                    }
                    field?.isAccessible = true
                    param.result = field?.get(null)
                }
            }
        }
        count++
    }
    check(count > 0) { "Don't save search history: failed to install hooks" }
    PatcherLog.info { "NexFusion: Facebook Don't save search history hooks installed ($count)" }
}

/**
 * Stops Facebook from sending watched reels batches to GraphQL.
 * Ported and adapted from SysAdminDoc/Hushfacebook (FbShortsSeenStateMutation).
 */
val DontSendReelWatchHistory = patch(
    name = "Don't send reel watch history",
    description = "Stops Facebook from sending the reels you watched to its servers, keeping your watch history private.",
    use = false,
) {
    ::reelsSeenStateMethod.hookMethod {
        before { param ->
            param.result = null
        }
    }
    PatcherLog.info { "NexFusion: Facebook Don't send reel watch history hook installed" }
}

/**
 * Allows a song on a photo story to play for up to 30 seconds instead of 15.
 * Ported and adapted from SysAdminDoc/Hushfacebook #156.
 */
val LongerStoryMusic = patch(
    name = "Longer music on photo stories",
    description = "Allows songs added to photo stories to play for up to 30 seconds instead of 15.",
    use = false,
) {
    ::storyMusicClipLengthMethod.hookMethod {
        after { param ->
            val length = param.result as? Int ?: return@after
            if (length in 1..29999) {
                param.result = 30000
            }
        }
    }
    PatcherLog.info { "NexFusion: Facebook Longer music on photo stories hook installed" }
}

private val bloksAttributesFields = java.util.concurrent.ConcurrentHashMap<Class<*>, java.lang.reflect.Field?>()
private val bloksNoteCache = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Any, Boolean>())
private val NOTE_MARKERS = listOf(
    "com.bloks.www.bloks.profile.note.",
    "com.bloks.www.bloks.profile.async.fetch.note_bubble",
)

private fun isBloksNoteNode(node: Any?): Boolean {
    if (node == null) return false
    bloksNoteCache[node]?.let { return it }
    val field = bloksAttributesFields.computeIfAbsent(node.javaClass) { clazz ->
        var curr: Class<*>? = clazz
        while (curr != null && curr != Any::class.java) {
            for (f in curr.declaredFields) {
                if (android.util.SparseArray::class.java.isAssignableFrom(f.type) && !Modifier.isStatic(f.modifiers)) {
                    f.isAccessible = true
                    return@computeIfAbsent f
                }
            }
            curr = curr.superclass
        }
        null
    } ?: return false

    val sparseArray = runCatching { field.get(node) as? android.util.SparseArray<*> }.getOrNull() ?: return false
    var isNote = false
    for (i in 0 until sparseArray.size()) {
        val value = sparseArray.valueAt(i)
        if (value is String) {
            if (NOTE_MARKERS.any { value.contains(it) }) {
                isNote = true
                break
            }
        }
    }
    bloksNoteCache[node] = isNote
    return isNote
}

/**
 * Hides the note bubble on profile pictures.
 * Ported and adapted from SysAdminDoc/Hushfacebook #195.
 */
val HideProfileNotes = patch(
    name = "Hide profile notes",
    description = "Hides the note bubble on profile pictures, including the prompt on your own profile.",
    use = false,
) {
    var count = 0
    for (dexMethod in ::bloksChildGetters.dexMethodList) {
        dexMethod.hookMethod {
            after { param ->
                val list = param.result as? List<*> ?: return@after
                if (list.isEmpty()) return@after
                if (list.any { isBloksNoteNode(it) }) {
                    param.result = list.filterNot { isBloksNoteNode(it) }
                }
            }
        }
        count++
    }
    check(count > 0) { "Hide profile notes: no Bloks child getters found" }
    PatcherLog.info { "NexFusion: Facebook Hide profile notes installed on $count getters" }
}

/**
 * Removes Meta AI from Facebook: search Meta AI answer cards, Ask Meta AI question
 * chips under posts, GenAI deep dive CTAs, and GenAI comment summaries.
 * Ported and adapted from SysAdminDoc/Hushfacebook (GPL-3.0).
 */
val HideMetaAi = patch(
    name = "Hide Meta AI",
    description = "Removes Meta AI answer cards from search, Ask Meta AI question chips and pills under feed posts, and GenAI comment summaries.",
    use = true,
) {
    // 1. Search Meta AI answer builder
    runCatching {
        ::searchMetaAiAnswerBuilder.hookMethod {
            before { param -> param.result = null }
        }
        PatcherLog.info { "NexFusion: Facebook search Meta AI answer builder hooked" }
    }.onFailure { PatcherLog.info(it) { "NexFusion: Facebook search Meta AI answer builder skipped" } }

    // 2. GenAiDeepDivePillPlugin (Ask Meta AI question pills under feed posts)
    runCatching {
        var count = 0
        for (dexMethod in ::genAiDeepDivePillPluginMethods.dexMethodList) {
            val method = runCatching { dexMethod.toMethod() }.getOrNull() ?: continue
            if (method.name == "<clinit>" || method.name == "<init>") continue
            dexMethod.hookMethod {
                before { param ->
                    if (method.returnType == java.lang.Boolean.TYPE) {
                        param.result = false
                    } else if (method.returnType != java.lang.Void.TYPE && !method.returnType.isPrimitive) {
                        param.result = null
                    }
                }
            }
            count++
        }
        PatcherLog.info { "NexFusion: Facebook GenAiDeepDivePillPlugin hooked on $count methods" }
    }.onFailure { PatcherLog.info(it) { "NexFusion: Facebook GenAiDeepDivePillPlugin skipped" } }

    // 3. GenAiDeepDiveCtaPlugin (GenAI CTA chips under photo posts)
    runCatching {
        var count = 0
        for (dexMethod in ::genAiDeepDiveCtaPluginMethods.dexMethodList) {
            val method = runCatching { dexMethod.toMethod() }.getOrNull() ?: continue
            if (method.name == "<clinit>" || method.name == "<init>") continue
            dexMethod.hookMethod {
                before { param ->
                    if (method.returnType == java.lang.Boolean.TYPE) {
                        param.result = false
                    } else if (method.returnType != java.lang.Void.TYPE && !method.returnType.isPrimitive) {
                        param.result = null
                    }
                }
            }
            count++
        }
        PatcherLog.info { "NexFusion: Facebook GenAiDeepDiveCtaPlugin hooked on $count methods" }
    }.onFailure { PatcherLog.info(it) { "NexFusion: Facebook GenAiDeepDiveCtaPlugin skipped" } }

    // 4. Inline comment summary plugin
    runCatching {
        var count = 0
        for (dexMethod in ::inlineCommentSummaryPluginMethods.dexMethodList) {
            val method = runCatching { dexMethod.toMethod() }.getOrNull() ?: continue
            if (method.name == "<clinit>" || method.name == "<init>") continue
            dexMethod.hookMethod {
                before { param ->
                    if (method.returnType == java.lang.Boolean.TYPE) {
                        param.result = false
                    } else if (method.returnType != java.lang.Void.TYPE && !method.returnType.isPrimitive) {
                        param.result = null
                    }
                }
            }
            count++
        }
        PatcherLog.info { "NexFusion: Facebook inline comment summary plugin hooked on $count methods" }
    }.onFailure { PatcherLog.info(it) { "NexFusion: Facebook inline comment summary plugin skipped" } }
}

val HideSuggestedGroups = patch(
    name = "Hide suggested groups",
    description = "Removes Suggested for you group cards and recommendation units from the Groups tab and feeds.",
    use = false,
) {
    runCatching {
        val edgeClass = classLoader.loadClass("com.facebook.graphql.model.GraphQLFeedUnitEdge")
        val categoryClass = classLoader.loadClass("com.crossapp.graphql.facebook.enums.GraphQLFeedStoryCategory")
        val categoryGetter = edgeClass.declaredMethods.singleOrNull {
            it.parameterTypes.isEmpty() && it.returnType == categoryClass
        } ?: throw NoSuchMethodException("GraphQLFeedUnitEdge -> GraphQLFeedStoryCategory")
        categoryGetter.isAccessible = true

        val suggestedCategories = setOf(
            "GROUPS_YOU_SHOULD_JOIN",
            "DISCOVER_GROUPS",
            "SUGGESTED_GROUPS",
            "GROUP_SUGGESTIONS",
            "GROUP_RECOMMENDATIONS",
            "GROUPS_DISCOVERY_CHAINING",
        )

        ::feedFunnel.hookMethod {
            before { param ->
                val edge = param.args.firstOrNull { edgeClass.isInstance(it) } ?: return@before
                val categoryName = runCatching {
                    categoryGetter.invoke(edge)?.toString()
                }.getOrNull() ?: return@before
                if (categoryName in suggestedCategories || categoryName.contains("GROUP_SUGGESTION")) {
                    param.result = false
                }
            }
        }
    }.onFailure { PatcherLog.info(it) { "NexFusion Facebook: Hide suggested groups hook skipped" } }
}

val FacebookPatches = arrayOf(
    AllowScreenshots,
    BlockScreenshotDetection,
    HideSponsoredPosts,
    HideSuggestedGroups,
    HideSponsoredStories,
    HideSponsoredReels,
    HideFeedReels,
    BlockTelemetry,
    OpenLinksExternally,
    DownloadFacebookMedia,
    DontSaveSearchHistory,
    DontSendReelWatchHistory,
    LongerStoryMusic,
    HideProfileNotes,
    HideMetaAi,
)
// "Hide marketplace ads" is not registered: its upstream implementation resolves tens of anchors
// and is not portable yet. It was previously a log-only stub, which reported success without
// hooking anything; see the audit report for the porting backlog.

/** Matches the Window flag setters this port clears the secure bit from. */
private fun Method.matchesWindowFlagsMethod(): Boolean = when (name) {
    "setFlags" -> parameterTypes.contentEquals(arrayOf(Integer.TYPE, Integer.TYPE))
    "addFlags" -> parameterTypes.contentEquals(arrayOf(Integer.TYPE))
    "setAttributes" -> parameterTypes.size == 1 && parameterTypes[0].name == "android.view.WindowManager\$LayoutParams"
    else -> false
}
