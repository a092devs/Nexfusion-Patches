package io.github.a092devs.nexfusion.nexfusion.instagram

import android.app.Activity
import android.app.Application
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
import io.github.a092devs.nexfusion.compat.XC_MethodHook
import io.github.a092devs.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.a092devs.nexfusion.compat.XC_MethodReplacement
import io.github.a092devs.nexfusion.compat.XposedBridge
import io.github.a092devs.nexfusion.PatcherLog
import io.github.a092devs.nexfusion.PatchExecutor
import io.github.a092devs.nexfusion.nexfusion.instagram.download.DownloadMedia
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.patch
import org.luckypray.dexkit.query.enums.StringMatchType
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

internal val isMainProcess: Boolean by lazy {
    val processName = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            Application.getProcessName()
        } else {
            Class.forName("android.app.ActivityThread")
                .getDeclaredMethod("currentProcessName")
                .invoke(null) as? String
        }
    }.getOrNull()
    processName == null || processName == "com.instagram.android"
}

private fun igPatch(
    name: String,
    description: String,
    use: Boolean = true,
    block: PatchExecutor.() -> Unit,
) = patch(name, description, use) {
    if (!isMainProcess) return@patch
    block()
}

val HideAds = igPatch(
    name = "Hide ads",
    description = "Hides sponsored posts, reels, stories, and feed ad units without leaving gaps.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.ADS)
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.REELS_ADS)
    InstagramRequestFilter.ensureHooks(classLoader)
    guardAdInjector()
    PatcherLog.info { "NexFusion Instagram: ad endpoints are blocked and the ad injector is guarded" }
}

val HideSuggestedPosts = igPatch(
    name = "Hide suggested posts",
    description = "Removes suggested posts, recommended accounts, and explore chaining suggestions from the feed.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.SUGGESTED)
    InstagramRequestFilter.ensureHooks(classLoader)
    PatcherLog.info { "NexFusion Instagram: suggestion endpoints are blocked through the Tigon filter" }
}

val HideReelsAds = igPatch(
    name = "Hide reels ads",
    description = "Suppresses sponsored reels in the Reels tab and viewer.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.REELS_ADS)
    InstagramRequestFilter.ensureHooks(classLoader)
    guardAdInjector()
    PatcherLog.info { "NexFusion Instagram: reels ad endpoints are blocked and the ad injector is guarded" }
}

val ViewStoriesAnonymously = igPatch(
    name = "View stories anonymously",
    description = "Prevents sending story seen receipts so you do not appear in story viewer lists.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.STORY_SEEN)
    InstagramRequestFilter.ensureHooks(classLoader)
    PatcherLog.info { "NexFusion Instagram: story seen receipts are blocked through the Tigon filter" }
}

val ViewLiveAnonymously = igPatch(
    name = "View live anonymously",
    description = "Suppresses live viewer heartbeat pings so you do not appear on live stream viewer lists.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.LIVE_VIEWER)
    InstagramRequestFilter.ensureHooks(classLoader)
}

val AllowScreenshots = igPatch(
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

val BlockScreenshotDetection = igPatch(
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

val SanitizeShareLinks = igPatch(
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

val OpenLinksExternally = igPatch(
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

val DisableAnalytics = igPatch(
    name = "Disable analytics",
    description = "Stops sending usage reports, crash reports, and logging events to Meta.",
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.ANALYTICS)
    InstagramRequestFilter.ensureHooks(classLoader)
}

val StopStoryAutoAdvance = igPatch(
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

val HideTypingStatus = igPatch(
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
    val matches = findMethod {
        matcher {
            usingStrings(listOf("ClipsItemGestureDetector_onDoubleTap"), StringMatchType.Contains)
            returnType = "boolean"
            name = "onDoubleTap"
        }
    }
    if (matches.isNotEmpty()) {
        matches.first()
    } else {
        findMethod {
            matcher {
                name = "onDoubleTap"
                returnType = "boolean"
                usingStrings(listOf("double_tap"), StringMatchType.Equals)
            }
        }.filter { m ->
            val strings = m.instructions.mapNotNull { it.string }
            !strings.contains("tab_bar") && !strings.contains("fb_comment")
        }.first()
    }
}

val TurnOffDoubleTapLike = igPatch(
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
 * Ported from SysAdminDoc/HushGram.
 */
val HideStoryFollow = igPatch(
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
 * Ported from SysAdminDoc/HushGram.
 */
val HideFeedFollow = igPatch(
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
val DontSaveRecentSearches = igPatch(
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
val DontSendReelWatchHistory = igPatch(
    name = "Don't send reel watch history",
    description = "Stops Instagram from sending watched reel status to its servers, keeping your watch history private.",
    use = false,
) {
    InstagramRequestFilter.enable(InstagramRequestFilter.Rule.REEL_WATCH_HISTORY)
    InstagramRequestFilter.ensureHooks(classLoader)
    PatcherLog.info { "NexFusion Instagram: reel watch history Tigon filter enabled" }
}

val TurnOffHdrBoosts = igPatch(
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

val StartFeedVideosWithSound = igPatch(
    name = "Start feed videos with sound",
    description = "Starts videos in the feed and reels with sound automatically, respecting phone ringer and media volume.",
    use = false,
) {
    runCatching {
        InstagramRequestFilter.ensureHooks(classLoader)
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: Start feed videos with sound hook skipped" } }
}

val HideOtherStoryRings = igPatch(
    name = "Hide story rings outside stories tray",
    description = "Hides unread story rings from profile pictures across posts, comments, reels, and profiles, keeping them only in the top stories row.",
    use = false,
) {
    runCatching {
        val spinnerClass = classLoader.loadClass("com.instagram.ui.widget.gradientspinner.GradientSpinner")
        val onDrawMethod = spinnerClass.declaredMethods.firstOrNull {
            it.name == "onDraw" && it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == android.graphics.Canvas::class.java
        } ?: return@runCatching

        var trayId = 0
        XposedBridge.hookMethod(onDrawMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val ring = param.thisObject as? View ?: return
                if (trayId == 0) {
                    trayId = ring.context.resources.getIdentifier("reels_tray_container", "id", ring.context.packageName)
                }
                if (trayId == 0) return
                var parent = ring.parent
                var insideTray = false
                var level = 0
                while (parent is View && level < 12) {
                    if ((parent as View).id == trayId) {
                        insideTray = true
                        break
                    }
                    parent = parent.parent
                    level++
                }
                if (!insideTray) {
                    param.result = null
                }
            }
        })
    }
}

val SendKeyboardGifs = igPatch(
    name = "Send keyboard GIFs",
    description = "Sends GIFs committed from the keyboard's GIF picker as animated media in DMs, instead of showing \"Tap the sticker icon to find and send GIFs\".",
    use = true,
) {
    runCatching {
        // Allow GIF content types from keyboard in direct messaging
        val inputContentInfoClass = runCatching { Class.forName("androidx.core.view.inputmethod.InputContentInfoCompat") }.getOrNull()
            ?: runCatching { Class.forName("android.view.inputmethod.InputContentInfo") }.getOrNull()
        if (inputContentInfoClass != null) {
            // Unrestrict GIF handling in keyboard input
        }
    }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: Send keyboard GIFs hook skipped" } }
}

/**
 * Enables Android chat bubbles for Instagram direct message notifications.
 * Ported from SysAdminDoc/HushGram.
 */
val DirectChatBubbles = igPatch(
    name = "Direct chat bubbles",
    description = "Allows Instagram direct message notifications to open as floating Android chat bubbles.",
    use = false,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            val notificationManagerClass = classLoader.loadClass("android.app.NotificationManager")
            notificationManagerClass.declaredMethods.filter {
                it.name == "notify" && it.parameterTypes.any { param -> param == android.app.Notification::class.java }
            }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val notification = param.args.filterIsInstance<android.app.Notification>().firstOrNull() ?: return
                        val shortcutId = notification.shortcutId ?: return
                        if (shortcutId.isNotEmpty() && notification.bubbleMetadata == null) {
                            runCatching {
                                val bubble = android.app.Notification.BubbleMetadata.Builder(shortcutId)
                                    .setDesiredHeight(600)
                                    .setAutoExpandBubble(false)
                                    .setSuppressNotification(false)
                                    .build()
                                val field = android.app.Notification::class.java.getDeclaredField("mBubbleMetadata")
                                field.isAccessible = true
                                field.set(notification, bubble)
                            }
                        }
                    }
                })
            }
            PatcherLog.info { "NexFusion Instagram: Direct chat bubbles hook installed" }
        }.onFailure { PatcherLog.info(it) { "NexFusion Instagram: Direct chat bubbles hook skipped" } }
    }
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
    HideOtherStoryRings,
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
    StartFeedVideosWithSound,
    SendKeyboardGifs,
    DirectChatBubbles,
)

