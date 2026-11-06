package io.github.nexfusion.nexfusion.reddit

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.Window
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.nexfusion.IHook
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

// Ported from jkennethcarino/adobo (GPL-3.0),
// patches/src/main/kotlin/dev/jkcarino/adobo/patches/reddit/ (Reddit 2026.43.1 - 2026.14.0).
//
// Adobo rewrites Reddit's bytecode at build time (returnEarly stubs and one-instruction edits);
// this file installs runtime hooks that reach the same results. Each patch keeps its upstream
// default for its `use` flag so the manager's toggles match Adobo's picker.

/**
 * Upstream rewrites the [android.app.Activity.ScreenCaptureCallback] implementation and both
 * banner listeners so they never fire. The runtime port reaches the same effect one step earlier,
 * replacing the callback Reddit registers, so a screenshot never reaches Reddit's detection code.
 */
val DisableScreenshotBanner = patch(
    name = "Disable Reddit screenshot banner",
    description = "Disables the banner that shows up after taking a screenshot.",
) {
    val callbackClass = runCatching {
        Class.forName("android.app.Activity\$ScreenCaptureCallback", false, classLoader)
    }.getOrNull()
    if (callbackClass == null) {
        PatcherLog.info { "NexFusion: ScreenCaptureCallback not present, screenshot banner patch skipped" }
        return@patch
    }
    val register = XposedHelpers.findMethodExactIfExists(
        Activity::class.java,
        "registerScreenCaptureCallback",
        Executor::class.java,
        callbackClass,
    )
    if (register != null) {
        XposedBridge.hookMethod(register, XC_MethodReplacement.DO_NOTHING)
    }
    val unregister = XposedHelpers.findMethodExactIfExists(
        Activity::class.java,
        "unregisterScreenCaptureCallback",
        callbackClass,
    )
    if (unregister != null) {
        XposedBridge.hookMethod(unregister, XC_MethodReplacement.DO_NOTHING)
    }
}

/** Upstream replaces the click tracking getters with early returns. */
val OpenLinksDirectly = patch(
    name = "Open Reddit links directly",
    description = "Opens external links without going through the out.reddit.com redirector.",
) {
    GetAllowClickTrackingFingerprint.memberOrNull
        ?.let { XposedBridge.hookMethod(it, XC_MethodReplacement.returnConstant(false)) }
    GetOutboundClickTrackingFingerprint.memberOrNull
        ?.let { XposedBridge.hookMethod(it, XC_MethodReplacement.returnConstant(false)) }
}

/**
 * Upstream deletes the `OnAutoRefreshFeed` dispatch instructions; at runtime the dispatcher
 * completes without running its body, which is the same effect for this single-purpose method.
 */
val DisableAutoRefresh = patch(
    name = "Disable Reddit home feed auto-refresh",
    description = "Stops the home feed from refreshing automatically after viewing a post or being away.",
    use = false,
) {
    OnAutoRefreshFeedFingerprint.memberOrNull?.let { member ->
        XposedBridge.hookMethod(member, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                // Keep the original return shape: void dispatchers skip, value dispatchers see Unit.
                val method = param.method
                param.result = if (method is Method && method.returnType == Void.TYPE) null else Unit
            }
        })
    }
}

/** Upstream returns early from the `AppResumeAction` dispatcher when redirecting to home. */
val DisableHomeRedirect = patch(
    name = "Disable Reddit home screen redirect",
    description = "Stops Reddit from redirecting to the home screen when the app resumes from the background.",
) {
    AppResumeRedirectFingerprint.memberOrNull?.let { member ->
        XposedBridge.hookMethod(member, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val arg = param.args.firstOrNull()?.toString().orEmpty()
                if (arg.contains("REDIRECT_TO_HOME")) {
                    param.result = null
                }
            }
        })
    }
}

/**
 * Makes the system navigation bar fully transparent across all Reddit screens by hooking Reddit's
 * edge-to-edge setup, activity window focus changes, and platform Window color APIs.
 */
val TransparentSystemNavBar = patch(
    name = "Transparent Reddit navigation bar",
    description = "Makes the system navigation bar fully transparent.",
) {
    // 1. Hook Reddit's internal EdgeToEdge setup method
    ::EdgeToEdgeSetUpMethod.memberOrNull?.let { member ->
        XposedBridge.hookMethod(member, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val window = param.args.firstOrNull { it is Window } as? Window
                if (window != null) {
                    window.navigationBarColor = Color.TRANSPARENT
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                }
            }
        })
    }

    // 2. Hook Activity.onWindowFocusChanged for Reddit Activities to enforce transparency
    val onWindowFocusChanged = XposedHelpers.findMethodExactIfExists(
        Activity::class.java,
        "onWindowFocusChanged",
        Boolean::class.javaPrimitiveType,
    )
    if (onWindowFocusChanged != null) {
        XposedBridge.hookMethod(onWindowFocusChanged, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                val window = activity.window ?: return
                window.navigationBarColor = Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
            }
        })
    }

    // 3. Intercept direct Window setNavigationBarColor calls
    runCatching {
        XposedHelpers.findAndHookMethod(
            Window::class.java,
            "setNavigationBarColor",
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.args[0] = Color.TRANSPARENT
                }
            },
        )
    }

    // 4. Intercept direct Window setNavigationBarContrastEnforced calls
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            XposedHelpers.findAndHookMethod(
                Window::class.java,
                "setNavigationBarContrastEnforced",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = false
                    }
                },
            )
        }
    }
}

/**
 * Replaces default gray comment indent guidelines with distinct color-coded lines per nesting depth.
 * Colors cycle through 8 distinct theme hues with full opacity in Jetpack Compose drawScope.
 */
private val COMMENT_INDENT_COLORS = longArrayOf(
    0xFF2A9D8FL shl 32, // Teal Cascade
    0xFFC96B2CL shl 32, // Burnt Apricot
    0xFF7A8F3AL shl 32, // Olive Moss
    0xFF8C4A5BL shl 32, // Muted Mulberry
    0xFF4A6FA5L shl 32, // Slate Blue
    0xFFB85C38L shl 32, // Rust Clay
    0xFF6B7A2BL shl 32, // Juniper Berry
    0xFF7B5E8EL shl 32, // Heather Purple
)

private val isDrawingCommentIndent = ThreadLocal<Boolean>()
private val commentIndentLineIndex = ThreadLocal<Int>()

val ColorizeCommentIndentLines = patch(
    name = "Colorize comment indent lines",
    description = "Replaces the default gray comment indent lines with color-coded lines.",
) {
    val indentMember = ::CommentIndentMethod.memberOrNull
    val drawLineMember = ::CommentIndentDrawLineMethod.memberOrNull

    if (indentMember != null && drawLineMember != null) {
        // Track when Reddit's Compose comment indent drawing lambda is active
        XposedBridge.hookMethod(indentMember, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                isDrawingCommentIndent.set(true)
                commentIndentLineIndex.set(0)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                isDrawingCommentIndent.remove()
                commentIndentLineIndex.remove()
            }
        })

        // Intercept Compose drawLine calls made while drawing comment indent lines
        XposedBridge.hookMethod(drawLineMember, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isDrawingCommentIndent.get() == true) {
                    val index = commentIndentLineIndex.get() ?: 0
                    commentIndentLineIndex.set(index + 1)
                    val color = COMMENT_INDENT_COLORS[index % COMMENT_INDENT_COLORS.size]
                    param.args[1] = color
                    if (param.args.size > 7) {
                        param.args[7] = 1.0f // full opacity
                    }
                }
            }
        })
    } else {
        PatcherLog.info { "NexFusion: Comment indent line methods not found for ColorizeCommentIndentLines" }
    }
}

/**
 * Partial port: upstream additionally rewrites fields inside several `toString()` methods, which
 * cannot be expressed as runtime hooks; the score getters below are the portable part. Opt-in.
 */
val HideUpvoteScores = patch(
    name = "Hide Reddit upvote scores",
    description = "Stubs the score getters so upvote counts report zero or hidden.",
    use = false,
) {
    GetScoreFingerprint.memberOrNull
        ?.let { XposedBridge.hookMethod(it, XC_MethodReplacement.returnConstant(0)) }
    GetHideScoreFingerprint.memberOrNull
        ?.let { XposedBridge.hookMethod(it, XC_MethodReplacement.returnConstant(true)) }
}

/**
 * Partial port: upstream also rewrites the `toString()` view-count fields, which cannot be
 * expressed as runtime hooks; the view-count getter below is the portable part. Opt-in.
 */
val HideViewCounts = patch(
    name = "Hide Reddit post view counts",
    description = "Makes the post view-count getter report nothing so the views indicator is empty.",
    use = false,
) {
    GetViewCountFingerprint.memberOrNull
        ?.let { XposedBridge.hookMethod(it, XC_MethodReplacement.returnConstant(null)) }
}

val OpenLinksExternally = patch(
    name = "Open Reddit links externally",
    description = "Always opens links in your default external web browser instead of Reddit's in-app Custom Tabs browser.",
    use = false,
) {
    runCatching {
        XposedBridge.hookAllMethods(
            Activity::class.java,
            "startActivity",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val intent = param.args.firstOrNull() as? android.content.Intent ?: return
                    if (intent.action == android.content.Intent.ACTION_VIEW && intent.data?.scheme?.startsWith("http") == true) {
                        intent.removeExtra("android.support.customtabs.extra.SESSION")
                        intent.removeExtra("androidx.browser.customtabs.extra.SESSION")
                        intent.removeExtra("android.support.customtabs.extra.TOOLBAR_COLOR")
                        intent.removeExtra("androidx.browser.customtabs.extra.TOOLBAR_COLOR")
                        intent.setPackage(null)
                        intent.component = null
                    }
                }
            }
        )
    }
}

val RedditUnlockPremium = patch(
    name = "Unlock Premium",
    description = "Enables local Reddit Premium UI perks (custom app icons, premium styling, avatar styling). Server-side perks cannot be unlocked.",
) {
    runCatching {
        val accountClass = classLoader.loadClass("com.reddit.domain.model.Account")
        for (m in accountClass.declaredMethods) {
            val name = m.name.lowercase()
            if (m.returnType == java.lang.Boolean.TYPE || m.returnType == java.lang.Boolean::class.java) {
                if (name == "isgold" || name == "haspremium" || name == "ispremiumsubscriber" ||
                    name == "haspremiumavatartreatment" || name.contains("premium")
                ) {
                    XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(true))
                }
            }
        }
    }
}

val RedditBlockAutoModComments = patch(
    name = "Block AutoMod comments",
    description = "Hides stickied and automated comments from u/AutoModerator at the top of comment threads.",
    use = false,
) {
    runCatching {
        val adapterClass = runCatching { classLoader.loadClass("androidx.recyclerview.widget.RecyclerView\$Adapter") }.getOrNull()
        if (adapterClass != null) {
            XposedBridge.hookAllMethods(
                adapterClass,
                "onBindViewHolder",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val holder = param.args.firstOrNull() ?: return
                        val itemView = runCatching {
                            XposedHelpers.getObjectField(holder, "itemView") as? View
                        }.getOrNull() ?: return

                        val holderStr = holder.toString().lowercase()
                        val text = itemView.findCommentText()
                        if (text.contains("AutoModerator", ignoreCase = true) && (holderStr.contains("comment") || text.contains("I am a bot", ignoreCase = true))) {
                            itemView.visibility = View.GONE
                            itemView.layoutParams?.let { lp ->
                                lp.height = 0
                                lp.width = 0
                                itemView.layoutParams = lp
                            }
                        }
                    }
                }
            )
        }
    }
}

private fun View.findCommentText(): String {
    val sb = StringBuilder()
    fun walk(v: View) {
        if (v is TextView) {
            sb.append(v.text).append(" ")
        } else if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                walk(v.getChildAt(i))
            }
        }
    }
    walk(this)
    return sb.toString()
}

val RedditPatches = arrayOf(
    DisableScreenshotBanner,
    OpenLinksDirectly,
    OpenLinksExternally,
    RedditUnlockPremium,
    RedditBlockAutoModComments,
    RedgifsAudio,
    RedgifsHd,
    DisableAutoRefresh,
    DisableHomeRedirect,
    TransparentSystemNavBar,
    ColorizeCommentIndentLines,
    HideUpvoteScores,
    HideViewCounts,
    HideAds,
    SanitizeSharingLinks,
    StartAsGuest,
)

