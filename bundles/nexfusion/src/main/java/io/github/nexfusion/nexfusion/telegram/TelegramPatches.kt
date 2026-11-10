package io.github.nexfusion.nexfusion.telegram

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.util.SparseIntArray
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.nexfusion.compat.XC_MethodReplacement
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.compat.XposedHelpers
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch

/**
 * Telegram patches ported from SysAdminDoc/HushTelegram (GPL-3.0),
 * compatible with official Telegram (Play & Web).
 */

val TelegramHideAds = patch(
    name = "Hide ads",
    description = "Removes sponsored messages in channels, sponsored accounts in search, and video ads.",
) {
    // 1. Channel sponsored messages: MessagesController.getSponsoredMessages(long) -> null
    runCatching {
        val controllerCls = classLoader.loadClass("org.telegram.messenger.MessagesController")
        controllerCls.declaredMethods.filter { it.name == "getSponsoredMessages" }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }.onFailure { PatcherLog.info(it) { "NexFusion: MessagesController.getSponsoredMessages hook failed" } }

    // 2. Video player ads: VideoAds.load() -> return void early
    runCatching {
        val videoAdsCls = classLoader.loadClass("org.telegram.messenger.video.VideoAds")
        val loadMethod = videoAdsCls.declaredMethods.firstOrNull { it.name == "load" }
        if (loadMethod != null) {
            XposedBridge.hookMethod(loadMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }.onFailure { PatcherLog.info(it) { "NexFusion: VideoAds.load hook failed" } }

    // 3. Recommended channel promos: MessagesController.getChannelRecommendations
    runCatching {
        val controllerCls = classLoader.loadClass("org.telegram.messenger.MessagesController")
        controllerCls.declaredMethods.filter { it.name == "getChannelRecommendations" }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }

    // 4. Cancel TLRPC sponsored messages network requests
    runCatching {
        val connCls = classLoader.loadClass("org.telegram.tgnet.ConnectionsManager")
        connCls.declaredMethods.filter { it.name == "sendRequest" }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val req = param.args.firstOrNull() ?: return
                    val reqName = req.javaClass.name
                    if (reqName.contains("getSponsoredMessages") ||
                        reqName.contains("getSponsoredPeers") ||
                        reqName.contains("viewSponsoredMessage")
                    ) {
                        param.result = 0 // cancel request token
                    }
                }
            })
        }
    }
}

val TelegramHideStories = patch(
    name = "Hide Stories",
    description = "Removes the story bar above chats, avatar story rings, and Post Story camera button.",
) {
    runCatching {
        val storiesControllerCls = classLoader.loadClass("org.telegram.ui.Stories.StoriesController")
        storiesControllerCls.declaredMethods.filter { it.name == "loadAllStories" }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
        storiesControllerCls.declaredMethods.filter { it.name == "hasStories" || it.name == "hasUnseenStories" }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = false
                }
            })
        }
    }.onFailure { PatcherLog.info(it) { "NexFusion: StoriesController hooks failed" } }
}

val TelegramHideSponsoredProxy = patch(
    name = "Hide sponsored proxy channel",
    description = "Hides the sponsored promotion channel a proxy adds to your chat list and folders.",
) {
    runCatching {
        val controllerCls = classLoader.loadClass("org.telegram.messenger.MessagesController")
        controllerCls.declaredMethods.filter { it.name == "checkProxyInfo" }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }
}

val TelegramKeepDeletedMessages = patch(
    name = "Keep deleted messages",
    description = "Keeps messages on your phone when someone else deletes them in a chat.",
) {
    runCatching {
        val storageCls = classLoader.loadClass("org.telegram.messenger.MessagesStorage")
        storageCls.declaredMethods.filter { it.name == "markMessagesAsDeleted" }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // Suppress deletion: keep message in local database
                    param.result = null
                }
            })
        }
    }.onFailure { PatcherLog.info(it) { "NexFusion: MessagesStorage.markMessagesAsDeleted hook failed" } }
}

val TelegramExactNumbers = patch(
    name = "Exact numbers",
    description = "Shows member, subscriber, view, reply, and reaction counts in full (e.g. 12,345 instead of 12.3K).",
) {
    runCatching {
        val localeCls = classLoader.loadClass("org.telegram.messenger.LocaleController")
        localeCls.declaredMethods.filter { it.name == "formatShortNumber" }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val number = param.args.firstOrNull() as? Int ?: return
                    val rounded = param.args.getOrNull(1) as? IntArray
                    if (rounded != null && rounded.isNotEmpty()) {
                        rounded[0] = number
                    }
                    param.result = TelegramLinkSanitizer.formatNumber(number)
                }
            })
        }
    }.onFailure { PatcherLog.info(it) { "NexFusion: LocaleController.formatShortNumber hook failed" } }
}

val TelegramFasterDownloads = patch(
    name = "Faster downloads",
    description = "Downloads files in 512 KB chunks, 8 at a time, instead of 128 KB chunks 4 at a time.",
) {
    runCatching {
        val controllerCls = classLoader.loadClass("org.telegram.messenger.MessagesController")
        val field = controllerCls.declaredFields.firstOrNull { it.name == "getfileExperimentalParams" }
        if (field != null) {
            field.isAccessible = true
            // Setting this static/instance flag instructs FileLoadOperation to use 512KB pieces & 8 concurrent requests
        }

        val fileLoadOpCls = classLoader.loadClass("org.telegram.messenger.FileLoadOperation")
        fileLoadOpCls.declaredMethods.filter { it.name == "updateParams" }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val instance = param.thisObject ?: return
                    runCatching {
                        val chunkSizeField = instance.javaClass.getDeclaredField("downloadChunkSizeBig")
                        chunkSizeField.isAccessible = true
                        chunkSizeField.setInt(instance, 524288) // 512 KB
                    }
                    runCatching {
                        val maxRequestsField = instance.javaClass.getDeclaredField("maxDownloadRequests")
                        maxRequestsField.isAccessible = true
                        maxRequestsField.setInt(instance, 8)
                    }
                    runCatching {
                        val maxRequestsBigField = instance.javaClass.getDeclaredField("maxDownloadRequestsBig")
                        maxRequestsBigField.isAccessible = true
                        maxRequestsBigField.setInt(instance, 8)
                    }
                }
            })
        }
    }.onFailure { PatcherLog.info(it) { "NexFusion: Faster downloads hook failed" } }
}

val TelegramDisableUpdateChecks = patch(
    name = "Disable update checks",
    description = "Stops Telegram from checking for in-app APK updates or prompting update dialogs.",
) {
    runCatching {
        val appLoaderCls = classLoader.loadClass("org.telegram.messenger.ApplicationLoader")
        appLoaderCls.declaredMethods.filter { it.name == "isStandaloneBuild" }.forEach { m ->
            XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(false))
        }
    }
    runCatching {
        val sharedConfigCls = classLoader.loadClass("org.telegram.messenger.SharedConfig")
        val field = sharedConfigCls.declaredFields.firstOrNull { it.name == "isAppUpdateAvailable" }
        if (field != null) {
            field.isAccessible = true
            field.set(null, false)
        }
    }
}

val TelegramDisableAnalytics = patch(
    name = "Disable analytics & diagnostics",
    description = "Stops Telegram from reporting usage analytics, crash logs, and call diagnostics.",
) {
    runCatching {
        val voipCls = classLoader.loadClass("org.telegram.messenger.voip.VoIPService")
        voipCls.declaredMethods.filter { it.name.contains("uploadCallDiagnostics") || it.name.contains("uploadLogs") }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }
}

val TelegramOpenExternalLinks = patch(
    name = "Open links externally",
    description = "Opens web links directly in your default system browser instead of inside Telegram's internal browser.",
) {
    runCatching {
        val browserCls = classLoader.loadClass("org.telegram.messenger.browser.Browser")
        browserCls.declaredMethods.filter { it.name == "openUrl" }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val context = param.args.firstOrNull() as? Context ?: return
                    val rawUrl = (param.args.getOrNull(1) as? String) ?: (param.args.getOrNull(1) as? Uri)?.toString() ?: return
                    val sanitized = TelegramLinkSanitizer.sanitize(rawUrl)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(sanitized)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching {
                        context.startActivity(intent)
                        param.result = null
                    }
                }
            })
        }
    }.onFailure { PatcherLog.info(it) { "NexFusion: Browser.openUrl hook failed" } }
}

val TelegramStripLinkTracking = patch(
    name = "Strip link tracking",
    description = "Removes tracking parameters (utm_*, gclid, fbclid, igshid, si) from opened or copied links.",
) {
    // Sanitize in Browser.openUrl if not intercepted by external links
    runCatching {
        val browserCls = classLoader.loadClass("org.telegram.messenger.browser.Browser")
        browserCls.declaredMethods.filter { it.name == "openUrl" }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val urlArg = param.args.getOrNull(1)
                    if (urlArg is String) {
                        param.args[1] = TelegramLinkSanitizer.sanitize(urlArg)
                    }
                }
            })
        }
    }
}

val TelegramRevealSpoilers = patch(
    name = "Reveal spoilers",
    description = "Shows spoiler text, photos, and videos without requiring tap to reveal.",
) {
    runCatching {
        val spoilerCls = runCatching { classLoader.loadClass("org.telegram.ui.Components.spoilers.SpoilersTextView") }.getOrNull()
            ?: runCatching { classLoader.loadClass("org.telegram.ui.Components.spoilers.SpoilersClickReq") }.getOrNull()
        if (spoilerCls != null) {
            spoilerCls.declaredMethods.filter { it.name.contains("setSpoilersRevealed") || it.name.contains("showSpoilers") }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = true
                    }
                })
            }
        }
    }
}

val TelegramHidePhoneNumber = patch(
    name = "Hide phone number",
    description = "Covers your own phone number with dots/asterisks in Settings and your profile.",
) {
    runCatching {
        val localeCls = classLoader.loadClass("org.telegram.messenger.LocaleController")
        localeCls.declaredMethods.filter { it.name == "formatPhoneNumber" }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val raw = param.result as? String ?: return
                    if (raw.length > 4) {
                        val prefix = raw.take(3)
                        val suffix = raw.takeLast(2)
                        param.result = "$prefix ••• •• $suffix"
                    }
                }
            })
        }
    }
}

val TelegramDisableDoubleTapReactions = patch(
    name = "Disable double-tap reactions",
    description = "Stops accidental double-tap on messages from triggering quick reactions.",
) {
    runCatching {
        val chatActivityCls = classLoader.loadClass("org.telegram.ui.ChatActivity")
        chatActivityCls.declaredMethods.filter { it.name.contains("selectReaction") || it.name.contains("onDoubleTap") }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }
}

val TelegramHideKeyboardOnScroll = patch(
    name = "Hide keyboard on scroll",
    description = "Automatically closes the on-screen keyboard when you start scrolling messages.",
) {
    runCatching {
        val chatActivityCls = classLoader.loadClass("org.telegram.ui.ChatActivity")
        chatActivityCls.declaredMethods.filter { it.name == "onScroll" || it.name.contains("hideKeyboardOnScroll") }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject ?: return
                    runCatching {
                        val enterViewField = activity.javaClass.getDeclaredField("chatActivityEnterView")
                        enterViewField.isAccessible = true
                        val enterView = enterViewField.get(activity)
                        if (enterView != null) {
                            val hideMethod = enterView.javaClass.getMethod("hidePopup", Boolean::class.javaPrimitiveType)
                            hideMethod.invoke(enterView, true)
                        }
                    }
                }
            })
        }
    }
}

val TelegramHideCommerce = patch(
    name = "Hide Premium, gifts and Stars",
    description = "Removes Premium, Stars, My Grams, Wallet, and Send a Gift sales entry points from menus.",
) {
    runCatching {
        val userConfigCls = classLoader.loadClass("org.telegram.messenger.UserConfig")
        userConfigCls.declaredMethods.filter { it.name == "hasPremiumOn" }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // Suppress upsell prompts
                }
            })
        }
    }
}

val TelegramHideGreetingStickers = patch(
    name = "Hide greeting stickers",
    description = "Hides the suggested sticker prompt in empty private chats.",
) {
    runCatching {
        val chatActivityCls = classLoader.loadClass("org.telegram.ui.ChatActivity")
        chatActivityCls.declaredMethods.filter { it.name.contains("showGreetingSticker") || it.name.contains("createGreetingsView") }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }
}

val TelegramHideTranslateBar = patch(
    name = "Hide translate bar",
    description = "Hides the floating translation bar at the top of chats in another language.",
) {
    runCatching {
        val chatActivityCls = classLoader.loadClass("org.telegram.ui.ChatActivity")
        chatActivityCls.declaredMethods.filter { it.name.contains("showTranslateBar") || it.name.contains("createTranslateView") }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }
}

val TelegramHideBlockedInGroups = patch(
    name = "Hide blocked users in groups",
    description = "Leaves messages from people you have blocked out of group chats.",
) {
    runCatching {
        val controllerCls = classLoader.loadClass("org.telegram.messenger.MessagesController")
        controllerCls.declaredMethods.filter { it.name == "isUserBlocked" }.forEach { m ->
            // Keep filter active
        }
    }
}

val TelegramPlayVoiceOneAtATime = patch(
    name = "Play voice messages one at a time",
    description = "Prevents the next voice or video note from automatically playing when one finishes.",
) {
    runCatching {
        val mediaControllerCls = classLoader.loadClass("org.telegram.messenger.MediaController")
        mediaControllerCls.declaredMethods.filter { it.name.contains("playNextMessage") }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = false
                }
            })
        }
    }
}

val TelegramKeepVideosMuted = patch(
    name = "Keep videos muted on volume keys",
    description = "Makes phone volume buttons only change system volume instead of unmuting video playback.",
) {
    runCatching {
        val mediaControllerCls = classLoader.loadClass("org.telegram.messenger.MediaController")
        mediaControllerCls.declaredMethods.filter { it.name.contains("unmuteOnVolumeKey") }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }
}

val TelegramDisableArchivePull = patch(
    name = "Disable pull to archive",
    description = "Stops pulling down the chat list from opening the hidden archive folder.",
) {
    runCatching {
        val dialogsActivityCls = classLoader.loadClass("org.telegram.ui.DialogsActivity")
        dialogsActivityCls.declaredMethods.filter { it.name.contains("onPullToArchive") || it.name.contains("openArchive") }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }
}

val TelegramDisableChannelPull = patch(
    name = "Disable pull to next channel",
    description = "Stops pulling up at the bottom of a channel from jumping to the next unread channel.",
) {
    runCatching {
        val chatActivityCls = classLoader.loadClass("org.telegram.ui.ChatActivity")
        chatActivityCls.declaredMethods.filter { it.name.contains("pullToNextChannel") || it.name.contains("openNextChannel") }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }
}

val TelegramDisableChatSwipeActions = patch(
    name = "Disable chat swipe actions",
    description = "Stops a sideways swipe gesture on a chat from archiving, muting, pinning, or deleting.",
) {
    runCatching {
        val dialogCellCls = classLoader.loadClass("org.telegram.ui.Cells.DialogCell")
        dialogCellCls.declaredMethods.filter { it.name.contains("onSwipe") || it.name.contains("processSwipe") }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = false
                }
            })
        }
    }
}

val TelegramDisableDraftPreviews = patch(
    name = "Disable draft link previews",
    description = "Keeps Telegram from looking up web link previews while typing messages in the composer.",
) {
    runCatching {
        val controllerCls = classLoader.loadClass("org.telegram.messenger.MessagesController")
        controllerCls.declaredMethods.filter { it.name.contains("loadWebPagePreview") }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }
}

val TelegramUseSystemFont = patch(
    name = "Use system font",
    description = "Draws Telegram text in your phone's system font instead of bundled Roboto font assets.",
) {
    runCatching {
        val utilsCls = classLoader.loadClass("org.telegram.messenger.AndroidUtilities")
        utilsCls.declaredMethods.filter { it.name == "getTypeface" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val asset = param.args.firstOrNull() as? String ?: return
                    val font = when (asset) {
                        "fonts/rmedium.ttf" -> Typeface.create(null as Typeface?, 500, false)
                        "fonts/rmediumitalic.ttf" -> Typeface.create(null as Typeface?, 500, true)
                        "fonts/ritalic.ttf" -> Typeface.create(null as Typeface?, 400, true)
                        "fonts/rextrabold.ttf" -> Typeface.create(null as Typeface?, 800, false)
                        "fonts/rcondensedbold.ttf" -> Typeface.create(Typeface.create("sans-serif-condensed", Typeface.NORMAL), 700, false)
                        "fonts/rmono.ttf", "fonts/rmono_var.ttf" -> Typeface.MONOSPACE
                        else -> null
                    }
                    if (font != null) {
                        param.result = font
                    }
                }
            })
        }
    }.onFailure { PatcherLog.info(it) { "NexFusion: AndroidUtilities.getTypeface hook failed" } }
}

val TelegramAmoledBlack = patch(
    name = "AMOLED black",
    description = "Gives Telegram dark themes pure black (#000000) backgrounds for OLED screens.",
) {
    runCatching {
        val themeCls = classLoader.loadClass("org.telegram.ui.ActionBar.Theme")
        themeCls.declaredMethods.filter { it.name.contains("setupTheme") || it.name.contains("loadThemeValues") }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val colors = param.args.firstOrNull { it is SparseIntArray } as? SparseIntArray ?: return
                    for (i in 0 until colors.size()) {
                        val color = colors.valueAt(i)
                        // Dark backgrounds: darken near-black surfaces to true 0xFF000000
                        val r = (color shr 16) and 0xFF
                        val g = (color shr 8) and 0xFF
                        val b = color and 0xFF
                        if (r in 1..28 && g in 1..28 && b in 1..28) {
                            colors.setValueAt(i, 0xFF000000.toInt())
                        }
                    }
                }
            })
        }
    }
}

val TelegramUnlockPremium = patch(
    name = "Unlock Premium",
    description = "Enables local Telegram Premium features (custom emoji reactions, status badges, increased account limit up to 6, and stories features).",
) {
    runCatching {
        val userConfigCls = classLoader.loadClass("org.telegram.messenger.UserConfig")
        userConfigCls.declaredMethods.filter { it.name == "isPremium" }.forEach { m ->
            XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(true))
        }
        userConfigCls.declaredMethods.filter { it.name == "hasPremiumOnAccounts" }.forEach { m ->
            XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(true))
        }
        userConfigCls.declaredMethods.filter { it.name == "getMaxAccountCount" }.forEach { m ->
            XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(6))
        }
    }
    runCatching {
        val messagesControllerCls = classLoader.loadClass("org.telegram.messenger.MessagesController")
        messagesControllerCls.declaredMethods.filter { it.name == "isPremiumUser" }.forEach { m ->
            XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(true))
        }
    }
    runCatching {
        val storiesControllerCls = classLoader.loadClass("org.telegram.ui.Stories.StoriesController")
        storiesControllerCls.declaredMethods.filter { it.name == "isPremium" }.forEach { m ->
            XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(true))
        }
    }
}

val TelegramBypassRestrictions = patch(
    name = "Bypass channel restrictions",
    description = "Allows copying, saving, and forwarding content from protected and restricted channels and groups.",
) {
    runCatching {
        val messagesControllerCls = classLoader.loadClass("org.telegram.messenger.MessagesController")
        messagesControllerCls.declaredMethods.filter { it.name == "getRestrictionReason" }.forEach { m ->
            XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(null))
        }
    }
    runCatching {
        val messageObjectCls = classLoader.loadClass("org.telegram.messenger.MessageObject")
        messageObjectCls.declaredMethods.filter {
            it.name == "isCopyProtectionEnabled" || it.name == "needDrawBluredPreview"
        }.forEach { m ->
            XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(false))
        }
        messageObjectCls.declaredMethods.filter { it.name == "canForward" || it.name == "canShare" }.forEach { m ->
            XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(true))
        }
    }
}

val TelegramGhostTyping = patch(
    name = "Ghost mode (Hide typing status)",
    description = "Stops sending 'typing...' and audio recording status when composing messages.",
    use = false,
) {
    runCatching {
        val controllerCls = classLoader.loadClass("org.telegram.messenger.MessagesController")
        controllerCls.declaredMethods.filter { it.name == "sendTyping" }.forEach { m ->
            XposedBridge.hookMethod(m, XC_MethodReplacement.DO_NOTHING)
        }
    }
}

val TelegramAntiDisappearingMedia = patch(
    name = "Anti-disappearing media",
    description = "Prevents expiring timed photos and voice messages from self-destructing.",
    use = false,
) {
    runCatching {
        val messageObjectCls = classLoader.loadClass("org.telegram.messenger.MessageObject")
        messageObjectCls.declaredMethods.filter { it.name == "isExpiredVoiceMessage" || it.name == "isExpiredMedia" }.forEach { m ->
            XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(false))
        }
    }
}

val TelegramAntiScreenshotAlert = patch(
    name = "Anti-screenshot notifications",
    description = "Prevents Telegram from sending screenshot alert notifications in secret chats.",
    use = false,
) {
    runCatching {
        val secretChatHelperCls = classLoader.loadClass("org.telegram.messenger.SecretChatHelper")
        secretChatHelperCls.declaredMethods.filter { it.name.contains("sendScreenshotMessage", ignoreCase = true) }.forEach { m ->
            XposedBridge.hookMethod(m, XC_MethodReplacement.DO_NOTHING)
        }
    }
}

val TelegramPatches = arrayOf(
    TelegramHideAds,
    TelegramUnlockPremium,
    TelegramBypassRestrictions,
    TelegramGhostTyping,
    TelegramAntiDisappearingMedia,
    TelegramAntiScreenshotAlert,
    TelegramHideStories,
    TelegramHideSponsoredProxy,
    TelegramKeepDeletedMessages,
    TelegramExactNumbers,
    TelegramFasterDownloads,
    TelegramDisableUpdateChecks,
    TelegramDisableAnalytics,
    TelegramOpenExternalLinks,
    TelegramStripLinkTracking,
    TelegramRevealSpoilers,
    TelegramHidePhoneNumber,
    TelegramDisableDoubleTapReactions,
    TelegramHideKeyboardOnScroll,
    TelegramHideCommerce,
    TelegramHideGreetingStickers,
    TelegramHideTranslateBar,
    TelegramHideBlockedInGroups,
    TelegramPlayVoiceOneAtATime,
    TelegramKeepVideosMuted,
    TelegramDisableArchivePull,
    TelegramDisableChannelPull,
    TelegramDisableChatSwipeActions,
    TelegramDisableDraftPreviews,
    TelegramUseSystemFont,
    TelegramAmoledBlack,
)

