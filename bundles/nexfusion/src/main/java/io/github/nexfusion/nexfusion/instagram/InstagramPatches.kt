package io.github.nexfusion.nexfusion.instagram

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.nexfusion.instagram.download.DownloadMedia
import io.github.nexfusion.hookMethod
import io.github.nexfusion.morphe.findMethodDirect
import io.github.nexfusion.patch
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap

/**
 * Instagram patches ported from SysAdminDoc/HushGram (GPL-3.0),
 * supporting Instagram 453.0.0.57.79, 453.0.0.55.79, 452.1.0.53.64, 452.0.0.48.64,
 * 451.0.0.53.80, 450.0.0.46.79, 447.0.0.55.81, and compatible versions.
 *
 * Three kinds of mechanism are used, and only these:
 *
 * - InstagramRequestFilter hooks `TigonServiceLayer.startRequest`, the kept Instagram API entry
 *   point, and answers the matched endpoints with an unreachable address. The ad, suggested,
 *   story-seen, live-viewer and analytics rules all ride on it. The endpoints each rule matches
 *   are asserted in InstagramPatchesTest.
 * - The ad injector is resolved with DexKit once per process and made to answer that nothing went
 *   in (see InstagramAds), the one ad mechanism that never reaches a request endpoint.
 * - Framework hooks: Window flag handling, ClipData text sharing and the screen capture
 *   callbacks, none of which depend on Instagram's obfuscation.
 * - The download menu hooks restored from this repository's own Piko bundle (see
 *   instagram/download/ and the NOTICE).
 *
 * Removed in this pass, because they only logged or hooked class names that do not exist in
 * release builds: "View DMs anonymously" and "Disable typing status" (both need the direct
 * message targets upstream resolves structurally, HushGram direct/seen and direct/typing), and
 * the log-only stubs "Ask before like", "Ask before refresh", "Disable video autoplay",
 * "Remove build expired popup" and "Open developer options" (each has a real upstream
 * implementation to port, from HushGram feed/confirm/, misc/buildexpiry/ and
 * misc/developeroptions/). "Download any media" was restored from the Piko bundle instead.
 * See the audit report for the backlog.
 */

private const val FLAG_SECURE = 0x2000
private fun withoutSecure(flags: Int): Int = flags and FLAG_SECURE.inv()

private fun clearSecure(params: Any?) {
    val layoutParams = params as? WindowManager.LayoutParams ?: return
    layoutParams.flags = withoutSecure(layoutParams.flags)
}

/**
 * The hosts that stay in the in-app browser, each with its subdomains: login, account linking,
 * checkout and Meta's own pages need the in-app browser's bridges, and HushGram keeps them.
 */
private val IN_APP_HOSTS = listOf(
    "instagram.com", "instagr.am", "ig.me", "facebook.com", "fb.com", "fb.me",
    "fb.watch", "messenger.com", "meta.com", "threads.net", "threads.com",
)

/** The extra Instagram's browser launchers put the link's context in; an ad's is an IABAdsContext. */
private const val IAB_CONTEXT_EXTRA = "EXTRA_IAB_CONTEXT"
private const val ADS_CONTEXT_CLASS = "com.facebook.browser.iabcontext.IABAdsContext"

/**
 * Instagram's in-app browser, a kept class name HushGram anchors on; the patch fails when it is
 * gone rather than letting links keep opening in an activity nothing redirects.
 */
private const val IN_APP_BROWSER_CLASS = "com.instagram.inappbrowser.fragments.BrowserLiteInMainProcessIGActivity"

val HideAds = patch(
    name = "Hide ads",
    description = "Hides sponsored posts, reels, stories, and feed ad units without leaving gaps.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.ADS)
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.REELS_ADS)
    InstagramRequestFilter.ensureHooks(classLoader)
    guardAdInjector()
    PatcherLog.info { "NexFusion Instagram: ad endpoints are blocked and the ad injector is guarded" }
}

val HideSuggestedPosts = patch(
    name = "Hide suggested posts",
    description = "Removes suggested posts, recommended accounts, and explore chaining suggestions from the feed.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.SUGGESTED)
    InstagramRequestFilter.ensureHooks(classLoader)
    PatcherLog.info { "NexFusion Instagram: suggestion endpoints are blocked through the Tigon filter" }
}

val HideReelsAds = patch(
    name = "Hide reels ads",
    description = "Suppresses sponsored reels in the Reels tab and viewer.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.REELS_ADS)
    InstagramRequestFilter.ensureHooks(classLoader)
    guardAdInjector()
    PatcherLog.info { "NexFusion Instagram: reels ad endpoints are blocked and the ad injector is guarded" }
}

val ViewStoriesAnonymously = patch(
    name = "View stories anonymously",
    description = "Prevents sending story seen receipts so you do not appear in story viewer lists.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.STORY_SEEN)
    InstagramRequestFilter.ensureHooks(classLoader)
    PatcherLog.info { "NexFusion Instagram: story seen receipts are blocked through the Tigon filter" }
}

val ViewLiveAnonymously = patch(
    name = "View live anonymously",
    description = "Suppresses live viewer heartbeat pings so you do not appear on live stream viewer lists.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.LIVE_VIEWER)
    InstagramRequestFilter.ensureHooks(classLoader)
}

val AllowScreenshots = patch(
    name = "Allow screenshots",
    description = "Clears FLAG_SECURE so screenshots and screen recordings work everywhere, including disappearing media.",
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
            .filter { it.name in listOf("setFlags", "addFlags", "setAttributes") }
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

    PatcherLog.info { "NexFusion Instagram: FLAG_SECURE hooks installed" }
}

val BlockScreenshotDetection = patch(
    name = "Block screenshot detection",
    description = "Stops Instagram from notifying people when you take screenshots or screen recordings.",
) {
    var observerHooked = false
    var frameworkHooked = 0

    // The photo library observer every Meta screenshot detector shares keeps its class name in
    // release builds; returning at once in its onChange hides every screenshot from Instagram.
    runCatching {
        val observer = classLoader.loadClass("com.facebook.screenshot.ScreenshotContentObserver")
        val onChange = observer.declaredMethods.single {
            it.name == "onChange" && it.returnType == Void.TYPE &&
                it.parameterTypes.contentEquals(arrayOf(java.lang.Boolean.TYPE, Uri::class.java))
        }
        XposedBridge.hookMethod(onChange, object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: MethodHookParam): Any? = null
        })
        observerHooked = true
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: screenshot observer hook skipped" } }

    // Android 14+ activity screen capture callbacks; Android 15+ screen recording callbacks.
    val noop = object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: MethodHookParam): Any? = null
    }
    if (Build.VERSION.SDK_INT >= 34) {
        for (className in listOf("android.app.Activity", "androidx.activity.ComponentActivity")) {
            val activity = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            activity.declaredMethods
                .filter { it.name == "registerScreenCaptureCallback" && it.parameterTypes.size == 2 }
                .forEach {
                    XposedBridge.hookMethod(it, noop)
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
                XposedBridge.hookMethod(it, noop)
                frameworkHooked++
            }
    }

    if (!observerHooked && frameworkHooked == 0) {
        throw IllegalStateException("Block screenshot detection: no Instagram observer or framework call was found")
    }
    PatcherLog.info { "NexFusion Instagram: screenshot detection hooks installed (observer=$observerHooked, framework=$frameworkHooked)" }
}

val SanitizeShareLinks = patch(
    name = "Sanitize share links",
    description = "Strips tracking parameters (igsh, utm, fbclid) from shared URLs.",
) {
    runCatching {
        // Hook clip data / text sharing
        val clipClass = classLoader.loadClass("android.content.ClipData")
        clipClass.declaredMethods.filter { it.name == "newPlainText" }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val text = param.args.getOrNull(1) as? CharSequence ?: return
                    val sanitized = InstagramLinks.sanitizeUrl(text.toString())
                    param.args[1] = sanitized
                }
            })
        }
    }
}

val OpenLinksExternally = patch(
    name = "Open links in external browser",
    description = "Opens web links directly in your default browser instead of Instagram's in-app browser. Instagram pages and links opened for an ad stay in the app.",
) {
    // Instagram's own in-app browser is the activity HushGram anchors on; it is a kept name the
    // manifest declares, and the link launchers route through it. Facebook's browser classes are
    // hooked too for surfaces that reach them. The redirect runs after each activity's own
    // lifecycle has finished, so the framework never sees a skipped super call (a before-hook
    // that overwrote onCreate used to make the activity crash with SuperNotCalledException).
    var instagramBrowserHooked = false
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
        IN_APP_BROWSER_CLASS,
        "com.facebook.browser.litev2.lite.BrowserLiteDIActivity",
        "com.facebook.browser.lite.BrowserLiteActivity",
    )) {
        val browser = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
        var classHooked = 0
        for (method in browser.declaredMethods) {
            val hookable = when (method.name) {
                "onCreate" -> method.parameterTypes.contentEquals(arrayOf(Bundle::class.java))
                "onNewIntent" -> method.parameterTypes.contentEquals(arrayOf(Intent::class.java))
                else -> false
            }
            if (hookable) {
                XposedBridge.hookMethod(method, redirect)
                hooked++
                classHooked++
            }
        }
        if (className == IN_APP_BROWSER_CLASS && classHooked > 0) instagramBrowserHooked = true
    }
    if (!instagramBrowserHooked) {
        throw IllegalStateException("Open links in external browser: Instagram's in-app browser activity was not found (a new name?)")
    }
    PatcherLog.info { "NexFusion Instagram: $hooked in-app browser lifecycle hooks installed" }
}

/**
 * Opens the intent's web link in the user's browser. The link is resolved through Instagram's
 * click tracker, checked as it is sent, and sent with Meta's click id stripped; Instagram-family
 * pages and links opened for an ad stay in the in-app browser, as HushGram keeps them.
 */
private fun openExternally(activity: Activity, intent: Intent?) {
    val uri = intent?.data ?: return
    if (!isWebLink(uri)) return
    if (isAdLink(activity, intent)) return
    val target = Uri.parse(InstagramLinks.unwrapShims(uri.toString()))
    val host = target.host?.lowercase(Locale.ROOT) ?: return
    if (IN_APP_HOSTS.any { host == it || host.endsWith(".$it") }) return
    // fbclid is Meta's click id; the site the link opens could otherwise tell Meta it was
    // followed. Nothing else in the link changes.
    val cleaned = Uri.parse(InstagramLinks.withoutClickId(target.toString()))
    if (!isWebLink(cleaned)) return
    if (InstagramLinks.openExternally(activity, cleaned.toString())) {
        PatcherLog.info { "NexFusion Instagram: opened $host in the external browser" }
        runCatching { activity.finish() }
    }
}

/** Whether [uri] is a web address that can be sent to a browser, host included. */
private fun isWebLink(uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
    return (scheme == "http" || scheme == "https") && !uri.host.isNullOrEmpty()
}

/**
 * Whether the link was opened for an ad, which stays in the app. A link followed inside a page
 * the browser already shows comes with no context of its own, so the context of the page it was
 * followed from is used; context that cannot be read counts as an ad, so the link stays where
 * Instagram put it.
 */
@Suppress("DEPRECATION")
private fun isAdLink(activity: Activity, intent: Intent): Boolean {
    return try {
        var context: Parcelable? = intent.getParcelableExtra(IAB_CONTEXT_EXTRA)
        if (context == null && activity.intent !== intent) {
            context = activity.intent?.getParcelableExtra(IAB_CONTEXT_EXTRA)
        }
        var type: Class<*>? = context?.javaClass
        while (type != null) {
            if (type.name == ADS_CONTEXT_CLASS) return true
            type = type.superclass
        }
        false
    } catch (e: Exception) {
        PatcherLog.info(e) { "NexFusion Instagram: the link's context could not be read, leaving it in the app" }
        true
    }
}

val DisableAnalytics = patch(
    name = "Disable analytics",
    description = "Stops sending usage reports, crash reports, and logging events to Meta.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.ANALYTICS)
    InstagramRequestFilter.ensureHooks(classLoader)
}

val StopStoryAutoAdvance = patch(
    name = "Stop story auto-advance",
    description = "Keeps each story on screen until you tap or swipe, preventing stories from auto-advancing.",
) {
    val viewerClass = runCatching {
        classLoader.loadClass("instagram.features.stories.fragment.ReelViewerFragment")
    }.getOrNull()
    if (viewerClass != null) {
        var count = 0
        for (method in viewerClass.declaredMethods) {
            if (method.isBridge && method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 && method.parameterTypes[0] == Any::class.java
            ) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = null
                    }
                })
                count++
            }
        }
        PatcherLog.info { "NexFusion Instagram: Stop story auto-advance installed ($count listeners)" }
    } else {
        PatcherLog.info { "NexFusion Instagram: ReelViewerFragment not found for auto-advance hook" }
    }
}

internal val typingStatusMethod = findMethodDirect {
    findMethod {
        matcher {
            usingStrings(listOf("IGDirectTypingStatusService", "is_typing_indicator_enabled"), StringMatchType.Contains)
            returnType = "void"
            paramTypes = listOf("boolean")
        }
    }.first()
}

val HideTypingStatus = patch(
    name = "Hide typing status",
    description = "Stops people you are chatting with from seeing when you are typing in direct messages.",
) {
    runCatching {
        ::typingStatusMethod.hookMethod(object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val isTyping = param.args.getOrNull(0) as? Boolean ?: return
                if (isTyping) {
                    param.result = null
                }
            }
        })
        PatcherLog.info { "NexFusion Instagram: typing status indicator suppressed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: typing status hook skipped" } }
}

internal val feedDoubleTapMethod = findMethodDirect {
    findMethod {
        matcher {
            usingStrings(
                listOf("DefaultMediaHolderGestureDetectorDelegateImpl#onDoubleTapMedia called with null activity"),
                StringMatchType.Contains,
            )
        }
    }.first()
}

internal val reelsDoubleTapMethod = findMethodDirect {
    findMethod {
        matcher {
            usingStrings(listOf("ClipsItemGestureDetector_onDoubleTap"), StringMatchType.Contains)
            returnType = "boolean"
            name = "onDoubleTap"
        }
    }.first()
}

val TurnOffDoubleTapLike = patch(
    name = "Turn off double tap to like",
    description = "Stops double-tapping posts and reels from liking them, preventing accidental likes while scrolling.",
) {
    runCatching {
        ::feedDoubleTapMethod.hookMethod(object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = null
            }
        })
        PatcherLog.info { "NexFusion Instagram: feed double-tap like suppressed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: feed double-tap hook skipped" } }

    runCatching {
        ::reelsDoubleTapMethod.hookMethod(object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                // Return true to consume the double-tap motion event so Reels does not trigger like animation/API.
                param.result = true
            }
        })
        PatcherLog.info { "NexFusion Instagram: reels double-tap like suppressed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: reels double-tap hook skipped" } }
}

internal val storyHeaderBinderMethod = findMethodDirect {
    findMethod {
        matcher {
            usingStrings(listOf("ReelViewerItemBinder.bindToolbarViews"))
            returnType = "void"
        }
    }.first()
}

private const val STORY_EXTRAS_CONTAINER = "reel_header_extras_container"
private const val STORY_FOLLOW_BUTTON = "reel_header_unconnected_follow_button_stub"

private val storyRowKeepers = Collections.synchronizedMap(WeakHashMap<View, ViewTreeObserver.OnPreDrawListener>())

private fun hideStoryFollowButton(holder: Any) {
    var c: Class<*>? = holder.javaClass
    var row: ViewGroup? = null
    while (c != null && c != Any::class.java) {
        for (field in c.declaredFields) {
            if (View::class.java.isAssignableFrom(field.type)) {
                try {
                    field.isAccessible = true
                    val view = field.get(holder) as? View ?: continue
                    val context = view.context
                    val containerId = context.resources.getIdentifier(STORY_EXTRAS_CONTAINER, "id", context.packageName)
                    if (containerId != 0) {
                        row = (if (view.id == containerId) view else view.findViewById(containerId)) as? ViewGroup
                        if (row != null) break
                    }
                } catch (_: Throwable) {}
            }
        }
        if (row != null) break
        c = c.superclass
    }

    val targetRow = row ?: return
    if (storyRowKeepers.containsKey(targetRow)) return

    val context = targetRow.context
    val followId = context.resources.getIdentifier(STORY_FOLLOW_BUTTON, "id", context.packageName)
    if (followId == 0) return

    val listener = object : ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            v.viewTreeObserver.addOnPreDrawListener(this)
        }

        override fun onViewDetachedFromWindow(v: View) {
            if (v.viewTreeObserver.isAlive) {
                v.viewTreeObserver.removeOnPreDrawListener(this)
            }
        }

        override fun onPreDraw(): Boolean {
            val followBtn = targetRow.findViewById<View>(followId)
            if (followBtn != null && followBtn !is ViewStub && followBtn.visibility != View.GONE) {
                followBtn.visibility = View.GONE
                return false
            }
            return true
        }
    }

    storyRowKeepers[targetRow] = listener
    targetRow.addOnAttachStateChangeListener(listener)
    if (targetRow.isAttachedToWindow) {
        targetRow.viewTreeObserver.addOnPreDrawListener(listener)
    }
}

/**
 * Takes the Follow button off the top of stories from accounts you don't follow, keeping the header clean.
 * Ported from SysAdminDoc/HushGram #237.
 */
val HideStoryFollow = patch(
    name = "Hide Follow on stories",
    description = "Takes the Follow button off the top of stories from accounts you don't follow. The name, time and menu stay.",
) {
    runCatching {
        ::storyHeaderBinderMethod.hookMethod(object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val holder = param.args.firstOrNull() ?: return
                hideStoryFollowButton(holder)
            }
        })
        PatcherLog.info { "NexFusion Instagram: Hide Follow on stories hook installed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: Hide Follow on stories hook skipped" } }
}

private const val FEED_HEADER = "row_feed_profile_header"
private const val INLINE_FOLLOW = "inline_follow_button"
private const val HEADER_FOLLOW = "follow_in_header"
private const val SUBSCRIBE = "subscribe_button"
private const val SUBSCRIBE_SHORTCUT = "subscribe_shortcut_button"
private const val SUBSCRIBE_SHORTCUT_STUB = "subscribe_shortcut_stub"

private val feedHiderSessions = Collections.synchronizedMap(WeakHashMap<Activity, ViewTreeObserver.OnPreDrawListener>())

private fun installFeedFollowHider(activity: Activity) {
    if (feedHiderSessions.containsKey(activity)) return
    val decorView = activity.window?.decorView ?: return
    val context = activity

    val headerId = context.resources.getIdentifier(FEED_HEADER, "id", context.packageName)
    val inlineFollowId = context.resources.getIdentifier(INLINE_FOLLOW, "id", context.packageName)
    val headerFollowId = context.resources.getIdentifier(HEADER_FOLLOW, "id", context.packageName)
    val subscribeId = context.resources.getIdentifier(SUBSCRIBE, "id", context.packageName)
    val subscribeShortcutId = context.resources.getIdentifier(SUBSCRIBE_SHORTCUT, "id", context.packageName)
    val subscribeShortcutStubId = context.resources.getIdentifier(SUBSCRIBE_SHORTCUT_STUB, "id", context.packageName)

    val targetButtons = setOf(inlineFollowId, headerFollowId, subscribeId).filter { it != 0 }.toSet()
    val anywhereButtons = setOf(subscribeShortcutId, subscribeShortcutStubId).filter { it != 0 }.toSet()

    if (targetButtons.isEmpty() && anywhereButtons.isEmpty()) return

    fun hideFeedFollowButtons(root: View, insideHeader: Boolean = false, budget: IntArray = intArrayOf(5000)): Boolean {
        if (budget[0]-- <= 0) return false
        var changed = false
        val id = root.id
        val isHeader = insideHeader || (headerId != 0 && id == headerId)
        if (id != View.NO_ID && root !is ViewStub) {
            if ((isHeader && id in targetButtons) || id in anywhereButtons) {
                if (root.visibility != View.GONE) {
                    root.visibility = View.GONE
                    changed = true
                }
            }
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                changed = hideFeedFollowButtons(root.getChildAt(i), isHeader, budget) || changed
            }
        }
        return changed
    }

    val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        hideFeedFollowButtons(decorView)
        true
    }

    feedHiderSessions[activity] = preDrawListener
    decorView.viewTreeObserver.addOnPreDrawListener(preDrawListener)
}

/**
 * Takes the Follow and Subscribe buttons off feed posts, keeping the post header clean.
 * Ported from SysAdminDoc/HushGram #248.
 */
val HideFeedFollow = patch(
    name = "Hide Follow on feed posts",
    description = "Takes the Follow and Subscribe buttons off feed posts, keeping the post header clean.",
    use = false,
) {
    runCatching {
        val activityClass = classLoader.loadClass("android.app.Activity")
        for (method in activityClass.declaredMethods) {
            if (method.name == "onResume" && method.parameterTypes.isEmpty()) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as? Activity ?: return
                        installFeedFollowHider(activity)
                    }
                })
            }
        }
        PatcherLog.info { "NexFusion Instagram: Hide Follow on feed posts hook installed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: Hide Follow on feed posts hook skipped" } }
}

/**
 * Stops Instagram from saving recent search clicks to your search history.
 * Ported from SysAdminDoc/HushGram.
 */
val DontSaveRecentSearches = patch(
    name = "Don't save recent searches",
    description = "Prevents searches from being added to your recent search history.",
    use = false,
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.RECENT_SEARCHES)
    InstagramRequestFilter.ensureHooks(classLoader)
    PatcherLog.info { "NexFusion Instagram: recent searches Tigon filter enabled" }
}

/**
 * Stops Instagram from sending watched reels seen state to its servers.
 * Ported from SysAdminDoc/HushGram.
 */
val DontSendReelWatchHistory = patch(
    name = "Don't send reel watch history",
    description = "Stops Instagram from sending watched reel status to its servers, keeping your watch history private.",
    use = false,
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.REEL_WATCH_HISTORY)
    InstagramRequestFilter.ensureHooks(classLoader)
    PatcherLog.info { "NexFusion Instagram: reel watch history Tigon filter enabled" }
}

private val metaAiHiderSessions = Collections.synchronizedMap(WeakHashMap<View, ViewTreeObserver.OnPreDrawListener>())

private val META_AI_VIEW_IDS = listOf(
    "meta_ai",
    "meta_ai_ring",
    "meta_ai_button",
    "meta_ai_pill",
    "meta_ai_badge",
    "meta_ai_inbox_row",
    "ask_meta_ai",
    "meta_ai_summary",
    "meta_ai_sources",
    "bottomSearchSuggestionPillsHelper",
    "row_thread_composer_meta_ai_discover",
    "row_thread_composer_meta_ai_invocation",
    "row_thread_composer_meta_ai_voice",
    "layout_meta_ai_large_ask_pill",
    "layout_meta_ai_prompt_pill",
    "meta_ai_pill_keyword",
    "meta_ai_hcm_prompt_pills",
    "meta_ai_actionbar_button",
    "biz_ai_agent_pills_on_media_stub",
    "biz_ai_agent_pills_toolbar_stub",
    "direct_composer_meta_ai_composer_upsell",
    "direct_composer_meta_ai_discovery_button",
    "direct_composer_meta_ai_invocation_button",
    "direct_composer_meta_ai_nux_disclaimer",
    "direct_composer_meta_ai_voice_button",
    "direct_composer_meta_ai_voice_button_v2",
    "direct_composer_overflow_button_meta_ai_v2",
    "restyleplus_create_with_ai_pill",
    "reel_netego_meta_ai_stories_overlay",
    "reel_netego_meta_ai_stories_stub",
    "ai_rewrite_bottom_sheet_meta_ai_icon_view",
    "ai_rewrite_nux_meta_ai_icon_view",
    "ai_rewrite_nux_meta_ai_text",
)

private val META_AI_MOBILE_CONFIG_FLAGS = setOf(
    0x81067100111e9fL, // Search tab bar "Search with Meta AI", search results & answers
    0x81040c0006109dL, // Messages inbox "Search or ask Meta AI"
    0x81040a001b1093L, // Messages search bar Meta AI ring
    0x81168700017258L, // Home top bar Meta AI chats / hatch button
    0x8110c200115a92L, // About this reel summary factory
    0x810ba900064043L, // Reels / post ask meta ai upsell
    0x8103A800000EB2L, // Direct tap & hold ask meta ai
    0x81061700032BF3L, // Regenerate ai message
    0x81061700052BF5L, // Direct ai feedback
    0x810f6600074387L, // Sidechat meta ai
    0x82061300050e2dL, // Ask meta ai mustache pill
    0x82061300040e2cL, // Imagine mustache pill
)

private fun isMetaAiText(text: CharSequence?): Boolean {
    if (text == null) return false
    val s = text.toString().lowercase(Locale.ROOT)
    return s.contains("meta ai") || s.contains("ask meta") || s.contains("meta_ai") ||
        s.contains("ask_meta_ai") || s.contains("create with ai") || s.contains("create_with_ai") ||
        s.contains("imagine") || (s.contains("summarize") && s.contains("ai")) ||
        s.contains("biz_ai_agent") || s.contains("prompt meta ai")
}

private fun installMetaAiViewHiderOnDecor(decorView: View) {
    if (metaAiHiderSessions.containsKey(decorView)) return
    val context = decorView.context ?: return

    val targetIds = META_AI_VIEW_IDS.mapNotNull { name ->
        val id = context.resources.getIdentifier(name, "id", context.packageName)
        if (id != 0) id else null
    }.toSet()

    fun hideMetaAiViews(root: View, budget: IntArray = intArrayOf(5000)): Boolean {
        if (budget[0]-- <= 0) return false
        var changed = false
        if (root !is ViewStub) {
            val id = root.id
            val resName = if (id != View.NO_ID) {
                runCatching { context.resources.getResourceEntryName(id) }.getOrNull()?.lowercase(Locale.ROOT)
            } else null
            val contentDesc = root.contentDescription
            val viewText = (root as? TextView)?.text
            val tag = root.tag?.toString()
            val clsName = root.javaClass.simpleName

            if (clsName.contains("ComponentHost") || clsName.contains("LithoView")) {
                val textContent = runCatching {
                    root.javaClass.getMethod("getTextContent").invoke(root)
                }.getOrNull()
                if (textContent != null && isMetaAiText(textContent.toString())) {
                    root.visibility = View.GONE
                    changed = true
                }
            }

            val shouldHide = (targetIds.isNotEmpty() && id != View.NO_ID && id in targetIds) ||
                (resName != null && (resName.contains("meta_ai") || resName.contains("ask_meta_ai") || resName.contains("create_with_ai") || resName.contains("biz_ai_agent") || resName.contains("gen_ai"))) ||
                isMetaAiText(contentDesc) ||
                isMetaAiText(viewText) ||
                isMetaAiText(tag) ||
                (clsName.contains("MetaAi", ignoreCase = true) || clsName.contains("AskMetaAi", ignoreCase = true))

            if (shouldHide && root.visibility != View.GONE) {
                val parent = root.parent as? ViewGroup
                val parentResName = if (parent != null && parent.id != View.NO_ID) {
                    runCatching { context.resources.getResourceEntryName(parent.id) }.getOrNull()?.lowercase(Locale.ROOT)
                } else null
                val isPillContainer = parent != null && parent.childCount in 1..5 &&
                    (parent.isClickable || parentResName?.contains("pill") == true || parentResName?.contains("chip") == true || parentResName?.contains("button") == true || parentResName?.contains("container") == true || parentResName?.contains("prompt") == true)
                if (isPillContainer) {
                    parent!!.visibility = View.GONE
                } else {
                    root.visibility = View.GONE
                }
                changed = true
            }
        }
        if (root is ViewGroup && root.visibility != View.GONE) {
            for (i in 0 until root.childCount) {
                changed = hideMetaAiViews(root.getChildAt(i), budget) || changed
            }
        }
        return changed
    }

    val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        hideMetaAiViews(decorView)
        true
    }

    metaAiHiderSessions[decorView] = preDrawListener
    decorView.viewTreeObserver.addOnPreDrawListener(preDrawListener)
}

/**
 * Removes Meta AI from Instagram: search bar and results, direct messages inbox, home top bar,
 * reel summaries, and AI network requests.
 * Ported and adapted from SysAdminDoc/HushGram (GPL-3.0).
 */
val HideMetaAi = patch(
    name = "Hide Meta AI",
    description = "Removes Meta AI from search, inbox, home top bar, reels menus, and blocks all Meta AI endpoints.",
    use = true,
) {
    // 1. Enable network filter
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.META_AI)
    InstagramRequestFilter.ensureHooks(classLoader)

    // 2. MobileConfig flags interceptor
    runCatching {
        val candidateClasses = listOf(
            "com.facebook.mobileconfig.MobileConfigManagerHolderImpl",
            "com.facebook.mobileconfig.factory.MobileConfigSafeContext",
            "com.facebook.mobileconfig.MobileConfigDependenciesInFBApps",
            "com.facebook.mobileconfig.MobileConfigOverridesTableHolder",
            "com.facebook.mobileconfig.MobileConfigContext",
            "com.facebook.mobileconfig.factory.MobileConfigUnsafeContext",
        )
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val paramId = param.args.firstOrNull { it is Long } as? Long ?: return
                if (paramId in META_AI_MOBILE_CONFIG_FLAGS) {
                    param.result = false
                }
            }
        }
        var hooked = 0
        for (clsName in candidateClasses) {
            val clazz = runCatching { classLoader.loadClass(clsName) }.getOrNull() ?: continue
            for (method in clazz.declaredMethods) {
                if (!Modifier.isAbstract(method.modifiers) &&
                    method.returnType == java.lang.Boolean.TYPE &&
                    method.parameterTypes.any { it == java.lang.Long.TYPE }) {
                    runCatching {
                        XposedBridge.hookMethod(method, hook)
                        hooked++
                    }
                }
            }
        }
        PatcherLog.info { "NexFusion Instagram: MobileConfig Meta AI flags hooked on $hooked methods" }
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: MobileConfig hook skipped" } }

    // 3. View hider in onResume
    runCatching {
        val activityClass = classLoader.loadClass("android.app.Activity")
        for (method in activityClass.declaredMethods) {
            if (method.name == "onResume" && method.parameterTypes.isEmpty()) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as? Activity ?: return
                        val decor = activity.window?.decorView ?: return
                        installMetaAiViewHiderOnDecor(decor)
                    }
                })
            }
        }
        PatcherLog.info { "NexFusion Instagram: Hide Meta AI activity view hider installed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: Hide Meta AI view hider skipped" } }

    // 4. View hider in Dialog.show (reels comments, reels more sheets, bottom sheets)
    runCatching {
        val dialogClass = classLoader.loadClass("android.app.Dialog")
        for (method in dialogClass.declaredMethods) {
            if (method.name == "show" && method.parameterTypes.isEmpty()) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val dialog = param.thisObject as? Dialog ?: return
                        val decor = dialog.window?.decorView ?: return
                        installMetaAiViewHiderOnDecor(decor)
                    }
                })
            }
        }
        PatcherLog.info { "NexFusion Instagram: Hide Meta AI dialog view hider installed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: Hide Meta AI dialog view hider skipped" } }

    // 5. View hider in Fragment.onViewCreated (Reels clips viewer, direct inbox fragments)
    runCatching {
        val fragmentClass = classLoader.loadClass("androidx.fragment.app.Fragment")
        for (method in fragmentClass.declaredMethods) {
            if (method.name == "onViewCreated" && method.parameterTypes.size == 2 && method.parameterTypes[0] == View::class.java) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.args.getOrNull(0) as? View ?: return
                        installMetaAiViewHiderOnDecor(view)
                    }
                })
            }
        }
        PatcherLog.info { "NexFusion Instagram: Hide Meta AI fragment view hider installed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: Hide Meta AI fragment view hider skipped" } }
}

val TurnOffHdrBoosts = patch(
    name = "Turn off HDR brightness boosts",
    description = "Prevents HDR reels and videos from boosting display brightness beyond SDR levels, and disables AV1 synthetic HDR tone mapping.",
    use = false,
) {
    if (Build.VERSION.SDK_INT >= 34) {
        runCatching {
            SurfaceView::class.java.declaredMethods.filter { it.name == "setDesiredHdrHeadroom" }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = 1.0f
                    }
                })
            }
        }
    }
    runCatching {
        Window::class.java.declaredMethods.filter { it.name == "setColorMode" }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.args[0] = 0
                }
            })
        }
    }
    // AV1 synthetic HDR tone mapping hook (Dav1dMediaCodecAdapterSetting)
    runCatching {
        val dav1dClass = classLoader.loadClass("exoplayer2.av1.src.Dav1dMediaCodecAdapterSetting")
        for (constructor in dav1dClass.declaredConstructors) {
            XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val field = dav1dClass.declaredFields.firstOrNull { it.name == "enhancementMode" && it.type == Integer.TYPE }
                    if (field != null) {
                        field.isAccessible = true
                        val mode = field.getInt(param.thisObject)
                        if (mode in 12..14) {
                            field.setInt(param.thisObject, 0)
                        }
                    }
                }
            })
        }
    }
    PatcherLog.info { "NexFusion Instagram: Turn off HDR brightness boosts installed" }
}

val InstagramPatches = arrayOf(
    HideAds,
    HideSuggestedPosts,
    HideReelsAds,
    ViewStoriesAnonymously,
    ViewLiveAnonymously,
    StopStoryAutoAdvance,
    HideTypingStatus,
    HideStoryFollow,
    HideFeedFollow,
    TurnOffDoubleTapLike,
    TurnOffHdrBoosts,
    AllowScreenshots,
    BlockScreenshotDetection,
    SanitizeShareLinks,
    OpenLinksExternally,
    DownloadMedia,
    DisableAnalytics,
    DontSaveRecentSearches,
    DontSendReelWatchHistory,
    HideMetaAi,
)
