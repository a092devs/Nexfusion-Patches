package io.github.nexfusion.nexfusion.linkedin

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import io.github.nexfusion.IHook
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import org.luckypray.dexkit.wrap.DexMethod

// Ported from heyymichii/michii-patches (GPL-3.0),
// extensions/extension/src/main/java/app/linkedin/extension and patches/src/main/kotlin/app/linkedin/patches.
//
// Upstream rewrites every ClipboardManager.setPrimaryClip / Intent.putExtra(String, String) call site
// in LinkedIn's dex to ShareLinkPatch methods. Hooking the framework methods inside the LinkedIn
// process reaches the same call sites, including the copy and share paths of bundled libraries.

val SanitizeShareLinks = patch(
    name = "Sanitize share links",
    description = "Removes tracking parameters from LinkedIn links when they are copied or shared.",
) {
    hookBefore("Landroid/content/ClipboardManager;->setPrimaryClip(Landroid/content/ClipData;)V") { param ->
        val clip = param.args.getOrNull(0) as? ClipData ?: return@hookBefore
        param.args[0] = cleanClip(clip)
    }

    hookBefore("Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;") { param ->
        if (param.args.getOrNull(0) != Intent.EXTRA_TEXT) return@hookBefore
        val value = param.args.getOrNull(1) as? String ?: return@hookBefore
        param.args[1] = LinkCleaner.clean(value)
    }
}

val OpenLinksDirectly = patch(
    name = "Open links directly",
    description = "Opens external links without LinkedIn's safety/go warning page.",
) {
    // Upstream injects unwrap at the start of the NavigateToUrlActionViewData constructor (feed,
    // profiles, ...), before the Uri is stored. The middle parameter types are wildcarded upstream,
    // so every constructor that takes the Uri first is hooked.
    try {
        val viewData = classLoader.loadClass("com.linkedin.sdui.viewdata.action.NavigateToUrlActionViewData")
        for (constructor in viewData.declaredConstructors) {
            if (constructor.parameterTypes.firstOrNull() != Uri::class.java) continue
            XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val uri = param.args.getOrNull(0) as? Uri ?: return
                    param.args[0] = unwrapSafetyUrl(uri)
                }
            })
        }
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn NavigateToUrlActionViewData not found" }
    }

    // Upstream makes getLinkShimmingLink return the link unchanged, so chat links skip the shim.
    try {
        val richText = classLoader.loadClass("com.linkedin.android.messaging.util.RichTextUtils")
        val shim = richText.declaredMethods.firstOrNull {
            it.name == "getLinkShimmingLink" && it.returnType == String::class.java &&
                it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
        } ?: throw NoSuchMethodException("RichTextUtils.getLinkShimmingLink(String)")
        XposedBridge.hookMethod(shim, object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: MethodHookParam) = param.args[0]
        })
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn RichTextUtils.getLinkShimmingLink not found" }
    }
}

val BlockTracking = patch(
    name = "Block tracking",
    description = "Stops most LinkedIn analytics events from being sent. Off by default, like upstream.",
    use = false,
) {
    // MetricQueue.queueMetric(IMetricAdapter, Tracker, CustomTrackingEventBuilder, CopyOnWriteArraySet)
    // returns early when the upstream setting is on; here the manager toggle plays that part.
    try {
        val metricQueue = classLoader.loadClass("com.linkedin.android.litrackinglib.network.MetricQueue")
        val methods = metricQueue.methods.filter {
            it.name == "queueMetric" && it.parameterTypes.size == 4 &&
                Set::class.java.isAssignableFrom(it.parameterTypes[3])
        }
        if (methods.isEmpty()) throw NoSuchMethodException("MetricQueue.queueMetric")
        for (method in methods) {
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.setResult(null)
                }
            })
        }
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn MetricQueue.queueMetric not found" }
    }
}

val AnonymousProfileViewing = patch(
    name = "Anonymous profile viewing",
    description = "Visits user profiles without appearing in 'Who viewed your profile'.",
    use = false,
) {
    runCatching {
        val trackingCls = classLoader.loadClass("com.linkedin.android.profile.components.tracking.ProfileTrackingFeatureImpl")
        for (m in trackingCls.declaredMethods) {
            if (m.name.contains("sendCustomProfileViewEvent")) {
                XposedBridge.hookMethod(m, XC_MethodReplacement.DO_NOTHING)
            }
        }
    }

    runCatching {
        val trackerCls = classLoader.loadClass("com.linkedin.android.litrackinglib.metric.Tracker")
        for (m in trackerCls.declaredMethods) {
            if (m.name == "send") {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val event = param.args.firstOrNull()?.toString() ?: ""
                        if (event.contains("ProfileViewEvent") || event.contains("profile_view")) {
                            param.result = null
                        }
                    }
                })
            } else if (m.name == "sendViaLiTracker") {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val eventName = param.args.firstOrNull()?.toString() ?: ""
                        val event = param.args.getOrNull(1)?.toString() ?: ""
                        if (eventName.contains("ProfileView") || event.contains("profile")) {
                            param.result = null
                        }
                    }
                })
            }
        }
    }

    runCatching {
        val queueCls = classLoader.loadClass("com.linkedin.android.litrackinglib.network.MetricQueue")
        for (m in queueCls.declaredMethods) {
            if (m.name == "sendViaTfe") {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val eventName = param.args.firstOrNull()?.toString() ?: ""
                        val event = param.args.getOrNull(2)?.toString() ?: ""
                        if (eventName.contains("ProfileView") || event.contains("profile")) {
                            param.result = null
                        }
                    }
                })
            }
        }
    }
}

val LinkedInPatches = arrayOf(
    DownloadMedia,
    DocumentPdfDownload,
    SanitizeShareLinks,
    OpenLinksDirectly,
    BlockTracking,
    AnonymousProfileViewing,
    HideSponsoredMessages,
    GhostNoTypingIndicator,
    GhostNoReadReceipts,
    DisableDoubleTapLike,
    HideFeedAds,
    HideSuggestedPosts,
    HidePromotedJobs,
    HidePremiumUpsells,
    FeedFilters,
    ExpandPosts,
    PostMenuItems,
    KeepFeed,
    CustomizeNavigationBar,
    HideTabLabels,
    HideTabBadges,
    SwipeFeeds,
    CopyText,
    ViewerTapToHide,
    MaterialYouTheme,
    PitchBlackTheme,
)

/** Hooks [descriptor] with a before callback, skipping targets this LinkedIn version does not have. */
private fun IHook.hookBefore(descriptor: String, body: (MethodHookParam) -> Unit) {
    try {
        DexMethod(descriptor).hookMethod(object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) = body(param)
        })
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn target not found: $descriptor" }
    }
}

/** Replaces the primary clip's text with the cleaned text. Returns the clip when nothing changes. */
private fun cleanClip(clip: ClipData): ClipData {
    if (clip.itemCount != 1) return clip
    val text = clip.getItemAt(0).text?.toString() ?: return clip
    val cleaned = LinkCleaner.clean(text) ?: return clip
    if (cleaned == text) return clip
    val label = clip.description?.label
    return ClipData.newPlainText(label, cleaned)
}

/** `www.linkedin.com/safety/go/?url=target` -> `target`. Other links stay unchanged. */
private fun unwrapSafetyUrl(uri: Uri): Uri {
    val target = SafetyLink.unwrapTarget(uri.host, uri.path, uri.getQueryParameter("url")) ?: return uri
    val targetUri = runCatching { Uri.parse(target) }.getOrNull() ?: return uri
    if (!SafetyLink.isWebLink(targetUri.scheme)) return uri
    return targetUri
}
