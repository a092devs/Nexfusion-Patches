package io.github.a092devs.nexfusion.nexfusion.reddit

import android.app.Activity
import android.os.Bundle
import app.morphe.extension.reddit.settings.RedditActivityHook
import app.morphe.extension.reddit.settings.preference.RedditSettingsStyle
import io.github.a092devs.nexfusion.compat.XC_MethodHook
import io.github.a092devs.nexfusion.compat.XC_MethodReplacement
import io.github.a092devs.nexfusion.compat.XposedBridge
import io.github.a092devs.nexfusion.compat.XposedHelpers
import io.github.a092devs.nexfusion.PatcherLog
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.patch

private val currentRowKey = ThreadLocal<String?>()

private fun findActivity(obj: Any?): Activity? {
    if (obj == null) return null
    return runCatching {
        var ctx: Any? = obj
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return ctx
            val base = ctx.baseContext
            if (base === ctx) break
            ctx = base
        }
        for (field in obj.javaClass.declaredFields) {
            if (field.type.isPrimitive) continue
            field.isAccessible = true
            val value = field.get(obj) ?: continue
            var fCtx: Any? = value
            while (fCtx is android.content.ContextWrapper) {
                if (fCtx is Activity) return fCtx
                val base = fCtx.baseContext
                if (base === fCtx) break
                fCtx = base
            }
        }
        null
    }.getOrNull()
}

val RedditSettings = patch(
    name = "Reddit Settings",
    description = "Adds Morphe settings into Reddit's settings menu.",
    use = false,
) {
    // 0. Enable modern Compose settings style for Reddit 2026.25+
    runCatching {
        XposedBridge.hookMethod(
            RedditSettingsStyle::class.java.getMethod("isModernStyle"),
            XC_MethodReplacement.returnConstant(true)
        )
    }

    // 0.1 Enable all Morphe extension settings categories by hooking isPatchIncluded
    val redditExtensionPatches = listOf(
        "app.morphe.extension.reddit.patches.AlwaysShowHidePatch",
        "app.morphe.extension.reddit.patches.AppDowngradeNoticePatch",
        "app.morphe.extension.reddit.patches.AppIconPatch",
        "app.morphe.extension.reddit.patches.AutoModeratorCommentsPatch",
        "app.morphe.extension.reddit.patches.ColorizeCommentIndentLinesPatch",
        "app.morphe.extension.reddit.patches.CopyTextPatch",
        "app.morphe.extension.reddit.patches.CustomFontPatch",
        "app.morphe.extension.reddit.patches.DisableBottomBarAutoHidePatch",
        "app.morphe.extension.reddit.patches.DisableHomeFeedAutoRefreshPatch",
        "app.morphe.extension.reddit.patches.DisableModernHomePatch",
        "app.morphe.extension.reddit.patches.DisableRefreshOnBackPatch",
        "app.morphe.extension.reddit.patches.DisableScreenshotPopupPatch",
        "app.morphe.extension.reddit.patches.DownloadAllFromGalleryPatch",
        "app.morphe.extension.reddit.patches.DownloadLocationPatch",
        "app.morphe.extension.reddit.patches.FeedAutoScrollPatch",
        "app.morphe.extension.reddit.patches.FeedMediaMaxHeightPatch",
        "app.morphe.extension.reddit.patches.FlairFiltersPatch",
        "app.morphe.extension.reddit.patches.FlipPostActionBarPatch",
        "app.morphe.extension.reddit.patches.ForceSystemFontPatch",
        "app.morphe.extension.reddit.patches.FullScreenReplyPatch",
        "app.morphe.extension.reddit.patches.FullWidthFeedMediaPatch",
        "app.morphe.extension.reddit.patches.GlassNavigationBarPatch",
        "app.morphe.extension.reddit.patches.HideAdsPatch",
        "app.morphe.extension.reddit.patches.HideAskButtonPatch",
        "app.morphe.extension.reddit.patches.HideCommunitiesShelfPatch",
        "app.morphe.extension.reddit.patches.HideHomeTabsPatch",
        "app.morphe.extension.reddit.patches.HideInteractivePostsPatch",
        "app.morphe.extension.reddit.patches.HideMediaViewerComponentsPatch",
        "app.morphe.extension.reddit.patches.HideNavigationButtonsPatch",
        "app.morphe.extension.reddit.patches.HidePostButtonsPatch",
        "app.morphe.extension.reddit.patches.HideRelatedPostsSectionPatch",
        "app.morphe.extension.reddit.patches.HideSidebarComponentsPatch",
        "app.morphe.extension.reddit.patches.HideTrendingShelvesPatch",
        "app.morphe.extension.reddit.patches.HideVideosForYouShelfPatch",
        "app.morphe.extension.reddit.patches.HighQualityAvatarsPatch",
        "app.morphe.extension.reddit.patches.HomeFeedTabsPatch",
        "app.morphe.extension.reddit.patches.KeepFeedPositionPatch",
        "app.morphe.extension.reddit.patches.KeepMediaViewerInSubredditPatch",
        "app.morphe.extension.reddit.patches.KeywordFiltersPatch",
        "app.morphe.extension.reddit.patches.MediaViewerBlackBackgroundPatch",
        "app.morphe.extension.reddit.patches.MediaViewerFadePatch",
        "app.morphe.extension.reddit.patches.OpenLinksDirectlyPatch",
        "app.morphe.extension.reddit.patches.OpenLinksExternallyPatch",
        "app.morphe.extension.reddit.patches.OpenSystemShareSheetPatch",
        "app.morphe.extension.reddit.patches.PostSidePanelPatch",
        "app.morphe.extension.reddit.patches.PrivateBrowsingModePatch",
        "app.morphe.extension.reddit.patches.RedgifsAudioPatch",
        "app.morphe.extension.reddit.patches.RememberCollapsedCommentsPatch",
        "app.morphe.extension.reddit.patches.RemoveSubRedditDialogPatch",
        "app.morphe.extension.reddit.patches.SanitizeSharingLinksPatch",
        "app.morphe.extension.reddit.patches.SearchHiddenProfilesPatch",
        "app.morphe.extension.reddit.patches.SharePostContentPatch",
        "app.morphe.extension.reddit.patches.ShowFlairsInHomeFeedPatch",
        "app.morphe.extension.reddit.patches.ShowViewCountPatch",
        "app.morphe.extension.reddit.patches.SwipeUpForCommentsPatch",
        "app.morphe.extension.reddit.patches.TabletGridFeedPatch",
        "app.morphe.extension.reddit.patches.VersionCheckPatch",
    )
    val bundleLoader = RedditActivityHook::class.java.classLoader
    for (className in redditExtensionPatches) {
        runCatching {
            val cls = runCatching { Class.forName(className, false, bundleLoader) }.getOrNull()
                ?: runCatching { Class.forName(className, false, XposedBridge::class.java.classLoader) }.getOrNull()
                ?: runCatching { classLoader.loadClass(className) }.getOrNull()
            if (cls != null) {
                val method = cls.getDeclaredMethod("isPatchIncluded")
                XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(true))
            }
        }
    }

    // 1. Hook WebBrowserActivity to host Morphe settings
    runCatching {
        val webBrowserClass = classLoader.loadClass("com.reddit.webembed.browser.WebBrowserActivity")

        XposedHelpers.findAndHookMethod(
            webBrowserClass,
            "onCreate",
            Bundle::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    if (RedditActivityHook.hook(activity)) {
                        PatcherLog.info { "NexFusion: Initialized Morphe settings inside WebBrowserActivity" }
                    }
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            webBrowserClass,
            "finish",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    if (RedditActivityHook.handleFinish(activity)) {
                        param.result = null
                    }
                }
            }
        )
    }.onFailure {
        PatcherLog.info(it) { "NexFusion Reddit: WebBrowserActivity hook skipped" }
    }

    // 2. Hook UserSettingsRegistry to register dedicated Morphe row on 2026.30+
    runCatching {
        UserSettingsRegistryConstructorFingerprint.hookMethod {
            before { param ->
                val rows = param.args[0] as? Set<*> ?: return@before
                var privacyPolicyRow: Any? = null
                for (row in rows) {
                    if (row == null) continue
                    val isPrivacyPolicy = runCatching {
                        row.javaClass.declaredFields.any { f ->
                            f.isAccessible = true
                            f.get(row) == "key_pref_privacy_policy"
                        }
                    }.getOrDefault(false)
                    if (isPrivacyPolicy) {
                        privacyPolicyRow = row
                        break
                    }
                }

                if (privacyPolicyRow == null) return@before

                val rowClass = privacyPolicyRow.javaClass
                val morpheRow = runCatching {
                    val ctor = rowClass.declaredConstructors.first()
                    ctor.isAccessible = true
                    val dummyArgs = arrayOfNulls<Any?>(ctor.parameterTypes.size)
                    for (i in ctor.parameterTypes.indices) {
                        dummyArgs[i] = when (ctor.parameterTypes[i]) {
                            Int::class.javaPrimitiveType -> 0
                            Boolean::class.javaPrimitiveType -> false
                            else -> null
                        }
                    }
                    val f1 = runCatching { XposedHelpers.getObjectField(privacyPolicyRow, "b") }.getOrNull()
                    if (f1 != null && dummyArgs.isNotEmpty()) dummyArgs[0] = f1
                    ctor.newInstance(*dummyArgs)
                }.getOrNull() ?: runCatching {
                    val unsafeClass = Class.forName("sun.misc.Unsafe")
                    val theUnsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
                    val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
                    allocateInstance.invoke(theUnsafe, rowClass)
                }.getOrNull()

                if (morpheRow != null) {
                    for (f in rowClass.declaredFields) {
                        f.isAccessible = true
                        runCatching { f.set(morpheRow, f.get(privacyPolicyRow)) }
                    }
                    for (f in rowClass.declaredFields) {
                        f.isAccessible = true
                        if (f.get(morpheRow) == "key_pref_privacy_policy") {
                            f.set(morpheRow, "key_pref_morphe")
                        }
                        if (f.type == Int::class.javaPrimitiveType && f.getInt(morpheRow) == 10) {
                            f.setInt(morpheRow, -1000)
                        }
                        if (f.type.name.contains("UserSettingsSection")) {
                            val generalSection = runCatching {
                                f.type.getField("General").get(null)
                            }.getOrNull()
                            if (generalSection != null) {
                                f.set(morpheRow, generalSection)
                            }
                        }
                    }

                    val newRows = LinkedHashSet(rows)
                    newRows.add(morpheRow)
                    param.args[0] = newRows
                    PatcherLog.info { "NexFusion Reddit: Added dedicated Morphe setting row" }
                }
            }
        }
    }.onFailure {
        PatcherLog.info(it) { "NexFusion Reddit: UserSettingsRegistry hook skipped" }
    }

    // 3. Hook PrivacyPolicySettingRowContentFingerprint to render the Morphe row
    runCatching {
        PrivacyPolicySettingRowContentFingerprint.hookMethod {
            before { param ->
                val row = param.thisObject ?: return@before
                val isMorpheRow = runCatching {
                    row.javaClass.declaredFields.any { f ->
                        f.isAccessible = true
                        f.get(row) == "key_pref_morphe"
                    } || row.javaClass.declaredMethods.any { m ->
                        m.returnType == String::class.java && m.parameterTypes.isEmpty() &&
                            runCatching { m.isAccessible = true; m.invoke(row) == "key_pref_morphe" }.getOrDefault(false)
                    }
                }.getOrDefault(false)
                if (isMorpheRow) {
                    currentRowKey.set("key_pref_morphe")
                }
            }
            after { _ ->
                currentRowKey.remove()
            }
        }

        ::SettingsRowComposableMethod.hookMethod {
            before { param ->
                if (currentRowKey.get() == "key_pref_morphe") {
                    param.args[0] = RedditActivityHook.getSettingLabel()
                    val origOnClick = param.args[1]
                    val fn0Class = runCatching { classLoader.loadClass("kotlin.jvm.functions.Function0") }.getOrNull()
                    if (fn0Class != null) {
                        val rowActivity = findActivity(origOnClick)
                        val proxyOnClick = java.lang.reflect.Proxy.newProxyInstance(
                            classLoader,
                            arrayOf(fn0Class)
                        ) { _, method, _ ->
                            if (method.name == "invoke") {
                                var activity = rowActivity
                                if (activity == null || activity.isFinishing || activity.isDestroyed) {
                                    activity = app.morphe.extension.shared.Utils.getActivity()
                                }
                                RedditActivityHook.showSettingsDialog(activity)
                                null
                            } else {
                                null
                            }
                        }
                        param.args[1] = proxyOnClick
                    }
                }
            }
        }
    }.onFailure {
        PatcherLog.info(it) { "NexFusion Reddit: Compose setting row rendering hook skipped" }
    }
}
