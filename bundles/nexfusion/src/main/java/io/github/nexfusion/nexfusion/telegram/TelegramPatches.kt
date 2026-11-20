package io.github.nexfusion.nexfusion.telegram

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import app.template.extension.telegram.MorpheForwardingRuntime
import app.template.extension.telegram.MorpheSettingsActivity
import app.template.extension.telegram.MorpheSettingsEntry
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.nexfusion.compat.XC_MethodReplacement
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.compat.XposedHelpers
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import java.lang.reflect.Method
import java.util.ArrayList

val TelegramMorpheSettings = patch(
    name = "Morphe Settings",
    description = "Experimental: Adds unified Morphe settings to Telegram and initializes message forwarding at startup.",
) {
    // 1. Initialize forwarding runtime on ApplicationLoader.postInitApplication
    runCatching {
        val appLoader = classLoader.loadClass("org.telegram.messenger.ApplicationLoader")
        XposedBridge.hookAllMethods(appLoader, "postInitApplication", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                runCatching {
                    MorpheForwardingRuntime.install()
                    PatcherLog.info { "Telegram: MorpheForwardingRuntime initialized successfully" }
                }
            }
        })
    }.onFailure { PatcherLog.info(it) { "Telegram: failed to hook ApplicationLoader.postInitApplication" } }

    // 2. Add Morphe Settings item to Telegram drawer
    runCatching {
        val drawerAdapter = classLoader.loadClass("org.telegram.ui.Adapters.DrawerLayoutAdapter")
        XposedBridge.hookAllMethods(drawerAdapter, "resetItems", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                runCatching {
                    val adapter = param.thisObject ?: return
                    val itemsField = adapter.javaClass.declaredFields.firstOrNull {
                        java.util.List::class.java.isAssignableFrom(it.type)
                    } ?: return
                    itemsField.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val items = itemsField.get(adapter) as? ArrayList<Any?> ?: return

                    val itemClass = runCatching {
                        classLoader.loadClass("org.telegram.ui.Adapters.DrawerLayoutAdapter\$Item")
                    }.getOrNull() ?: return

                    val constructor = itemClass.declaredConstructors.firstOrNull { it.parameterCount >= 2 } ?: return
                    constructor.isAccessible = true
                    val args = arrayOfNulls<Any>(constructor.parameterCount)
                    args[0] = 9991 // Unique drawer item ID
                    args[1] = "Morphe Settings"
                    val newItem = constructor.newInstance(*args)
                    items.add(newItem)
                }
            }
        })

        // Hook drawer item click
        val launchActivity = classLoader.loadClass("org.telegram.ui.LaunchActivity")
        launchActivity.declaredMethods.filter {
            it.parameterCount >= 2 && it.parameterTypes[0] == View::class.java &&
                (it.parameterTypes[1] == java.lang.Integer.TYPE || it.parameterTypes[1] == java.lang.Long.TYPE)
        }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val id = (param.args[1] as? Number)?.toInt() ?: return
                    if (id == 9991) {
                        param.result = null
                        val activity = param.thisObject as? Activity ?: return
                        MorpheSettingsActivity.showInDialog(activity)
                    }
                }
            })
        }
    }.onFailure { PatcherLog.info(it) { "Telegram: drawer hook failed" } }

    // 3. Hook Settings screen to inject Morphe Settings Help card
    runCatching {
        val uiClasses = listOf("org.telegram.ui.p91", "org.telegram.ui.t91", "org.telegram.ui.ProfileActivity")
        for (name in uiClasses) {
            val clazz = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            clazz.declaredMethods.filter {
                it.parameterCount == 2 && ArrayList::class.java.isAssignableFrom(it.parameterTypes[1])
            }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        runCatching {
                            @Suppress("UNCHECKED_CAST")
                            val list = param.args[1] as? ArrayList<Any?> ?: return
                            val context = (param.args[0] as? View)?.context
                                ?: (param.thisObject as? View)?.context
                                ?: return
                            val row = MorpheSettingsEntry.createSettingsRow(context)
                            val uItemClass = runCatching { classLoader.loadClass("org.telegram.ui.Components.UItem") }.getOrNull()
                            val asCustom = uItemClass?.declaredMethods?.firstOrNull {
                                it.parameterCount == 1 && it.parameterTypes[0] == View::class.java
                            }
                            if (asCustom != null) {
                                val item = asCustom.invoke(null, row)
                                list.add(item)
                            }
                        }
                    }
                })
            }
        }
    }
}

val TelegramUnlockPremium = patch(
    name = "Unlock Premium",
    description = "Unlocks Telegram Premium features for the current account.",
) {
    runCatching {
        val userConfig = classLoader.loadClass("org.telegram.messenger.UserConfig")
        XposedBridge.hookAllMethods(userConfig, "isPremium", XC_MethodReplacement.returnConstant(true))
        XposedBridge.hookAllMethods(userConfig, "hasPremiumOnAccounts", XC_MethodReplacement.returnConstant(true))
        XposedBridge.hookAllMethods(userConfig, "getMaxAccountCount", XC_MethodReplacement.returnConstant(999))
    }

    runCatching {
        val mc = classLoader.loadClass("org.telegram.messenger.MessagesController")
        XposedBridge.hookAllMethods(mc, "isPremiumUser", XC_MethodReplacement.returnConstant(true))
        XposedBridge.hookAllMethods(mc, "isTranslationsManualEnabled", XC_MethodReplacement.returnConstant(true))
        XposedBridge.hookAllMethods(mc, "isTranslationsAutoEnabled", XC_MethodReplacement.returnConstant(true))
    }

    runCatching {
        val sharedConfig = classLoader.loadClass("org.telegram.messenger.SharedConfig")
        XposedBridge.hookAllMethods(sharedConfig, "getDevicePerformanceClass", XC_MethodReplacement.returnConstant(2))
    }
}

val TelegramRemoveAds = patch(
    name = "Remove ads",
    description = "Removes sponsored messages and video ads.",
) {
    runCatching {
        val mo = classLoader.loadClass("org.telegram.messenger.MessageObject")
        XposedBridge.hookAllMethods(mo, "isSponsored", XC_MethodReplacement.returnConstant(false))
    }

    runCatching {
        val mc = classLoader.loadClass("org.telegram.messenger.MessagesController")
        XposedBridge.hookAllMethods(mc, "isSponsoredDisabled", XC_MethodReplacement.returnConstant(true))
        XposedBridge.hookAllMethods(mc, "getSponsoredMessages", XC_MethodReplacement.returnConstant(null))
    }

    runCatching {
        val videoAds = classLoader.loadClass("org.telegram.messenger.video.VideoAds")
        XposedBridge.hookAllMethods(videoAds, "load", XC_MethodReplacement.DO_NOTHING)
    }
}

val TelegramAntiDelete = patch(
    name = "Anti-delete messages",
    description = "Prevents messages deleted by other users from being removed locally.",
) {
    runCatching {
        val mc = classLoader.loadClass("org.telegram.messenger.MessagesController")
        XposedBridge.hookAllMethods(mc, "deleteMessagesByPush", XC_MethodReplacement.DO_NOTHING)
    }

    runCatching {
        val nc = classLoader.loadClass("org.telegram.messenger.NotificationsController")
        XposedBridge.hookAllMethods(nc, "removeDeletedMessagesFromNotifications", XC_MethodReplacement.DO_NOTHING)
    }

    runCatching {
        val storage = classLoader.loadClass("org.telegram.messenger.MessagesStorage")
        storage.declaredMethods.filter { it.name == "markMessagesAsDeleted" }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val isLocal = param.args.filterIsInstance<Boolean>().lastOrNull() ?: false
                    if (!isLocal) {
                        if (method.returnType == ArrayList::class.java || method.returnType == java.util.List::class.java) {
                            param.result = ArrayList<Any?>()
                        }
                    }
                }
            })
        }
    }
}

val TelegramAntiDisappearingMedia = patch(
    name = "Anti-disappearing media",
    description = "Keeps view-once photos, videos and voice messages viewable indefinitely.",
) {
    runCatching {
        val mo = classLoader.loadClass("org.telegram.messenger.MessageObject")
        XposedBridge.hookAllMethods(mo, "isSecretMedia", XC_MethodReplacement.returnConstant(false))
        XposedBridge.hookAllMethods(mo, "isSecretPhotoOrVideo", XC_MethodReplacement.returnConstant(false))
        XposedBridge.hookAllMethods(mo, "shouldEncryptPhotoOrVideo", XC_MethodReplacement.returnConstant(false))
        XposedBridge.hookAllMethods(mo, "isVoiceOnce", XC_MethodReplacement.returnConstant(false))
        XposedBridge.hookAllMethods(mo, "isRoundOnce", XC_MethodReplacement.returnConstant(false))
        XposedBridge.hookAllMethods(mo, "needDrawBluredPreview", XC_MethodReplacement.returnConstant(false))
    }
}

val TelegramBypassContentRestrictions = patch(
    name = "Bypass content restrictions",
    description = "Allows saving and forwarding content from restricted channels, chats, and users.",
) {
    runCatching {
        val mc = classLoader.loadClass("org.telegram.messenger.MessagesController")
        XposedBridge.hookAllMethods(mc, "isChatNoForwards", XC_MethodReplacement.returnConstant(false))
        XposedBridge.hookAllMethods(mc, "isUserNoForwards", XC_MethodReplacement.returnConstant(false))
        XposedBridge.hookAllMethods(mc, "isPeerNoForwards", XC_MethodReplacement.returnConstant(false))
    }

    runCatching {
        val mo = classLoader.loadClass("org.telegram.messenger.MessageObject")
        XposedBridge.hookAllMethods(mo, "canForwardMessage", XC_MethodReplacement.returnConstant(true))
    }
}

val TelegramBypassChannelRestrictions = patch(
    name = "Bypass channel restrictions",
    description = "Allows viewing restricted and sensitive channels and content.",
) {
    runCatching {
        val mc = classLoader.loadClass("org.telegram.messenger.MessagesController")
        XposedBridge.hookAllMethods(mc, "getRestrictionReason", XC_MethodReplacement.returnConstant(null))
        XposedBridge.hookAllMethods(mc, "isSensitive", XC_MethodReplacement.returnConstant(false))
        XposedBridge.hookAllMethods(mc, "showSensitiveContent", XC_MethodReplacement.returnConstant(true))
        XposedBridge.hookAllMethods(mc, "checkChannelError", XC_MethodReplacement.DO_NOTHING)
        XposedBridge.hookAllMethods(mc, "showCantOpenAlert", XC_MethodReplacement.DO_NOTHING)
    }

    runCatching {
        val mo = classLoader.loadClass("org.telegram.messenger.MessageObject")
        XposedBridge.hookAllMethods(mo, "isSensitive", XC_MethodReplacement.returnConstant(false))
        XposedBridge.hookAllMethods(mo, "isHiddenSensitive", XC_MethodReplacement.returnConstant(false))
    }
}

val TelegramDownloadBoost = patch(
    name = "Download speed boost",
    description = "Increases download chunk size to 1 MB and max concurrent requests to 12.",
) {
    runCatching {
        val flo = classLoader.loadClass("org.telegram.messenger.FileLoadOperation")
        XposedBridge.hookAllMethods(flo, "updateParams", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                runCatching {
                    val obj = param.thisObject ?: return
                    val cls = obj.javaClass
                    cls.getDeclaredField("downloadChunkSizeBig").apply { isAccessible = true; set(obj, 1048576) }
                    cls.getDeclaredField("maxDownloadRequests").apply { isAccessible = true; set(obj, 12) }
                    cls.getDeclaredField("maxDownloadRequestsBig").apply { isAccessible = true; set(obj, 12) }
                    cls.getDeclaredField("maxCdnParts").apply { isAccessible = true; set(obj, 2000) }
                }
            }
        })
    }
}

val TelegramHideStoriesRead = patch(
    name = "Hide Stories read status",
    description = "Prevents the client from processing and sending Stories read receipts.",
) {
    runCatching {
        val storiesController = classLoader.loadClass("org.telegram.ui.Stories.StoriesController")
        storiesController.declaredMethods.filter {
            it.name.contains("read", ignoreCase = true) && it.returnType == java.lang.Boolean.TYPE
        }.forEach { method ->
            XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(true))
        }
    }
}

val TelegramHideTyping = patch(
    name = "Hide typing indicator",
    description = "Blocks sending typing status and indicators to other users.",
) {
    runCatching {
        val mc = classLoader.loadClass("org.telegram.messenger.MessagesController")
        XposedBridge.hookAllMethods(mc, "sendTyping", XC_MethodReplacement.returnConstant(false))
    }
}

val TelegramDisableAutoUpdate = patch(
    name = "Disable auto-update",
    description = "Disables Telegram update availability and in-app update prompts.",
) {
    runCatching {
        val sc = classLoader.loadClass("org.telegram.messenger.SharedConfig")
        XposedBridge.hookAllMethods(sc, "isAppUpdateAvailable", XC_MethodReplacement.returnConstant(false))
        XposedBridge.hookAllMethods(sc, "setNewAppVersionAvailable", XC_MethodReplacement.returnConstant(false))
    }

    runCatching {
        val mc = classLoader.loadClass("org.telegram.messenger.MessagesController")
        XposedBridge.hookAllMethods(mc, "checkPromoInfoInternal", XC_MethodReplacement.DO_NOTHING)
    }
}

val TelegramDisableAnalytics = patch(
    name = "Disable telemetry and analytics",
    description = "Blocks AppCenter and Firebase analytics telemetry logging entry points.",
    use = false,
) {
    runCatching {
        val appLoader = classLoader.loadClass("org.telegram.messenger.ApplicationLoader")
        XposedBridge.hookAllMethods(appLoader, "startAppCenter", XC_MethodReplacement.DO_NOTHING)
        XposedBridge.hookAllMethods(appLoader, "logAppCenter", XC_MethodReplacement.DO_NOTHING)
    }
}

val TelegramDisableRichHtmlPaste = patch(
    name = "Use normal paste for Rich HTML clipboard content",
    description = "Bypasses Telegram's custom Rich HTML clipboard handling and delegates paste to normal text paste.",
) {
    runCatching {
        val enterView = classLoader.loadClass("org.telegram.ui.Components.ChatActivityEnterView")
        enterView.declaredMethods.filter {
            it.name.contains("RichHtml", ignoreCase = true)
        }.forEach { method ->
            XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(false))
        }
    }
}

val TelegramAntiScreenshotNotification = patch(
    name = "Anti-screenshot notification",
    description = "Blocks screenshot notifications from being sent to the other user.",
) {
    runCatching {
        val smh = classLoader.loadClass("org.telegram.messenger.SendMessagesHelper")
        XposedBridge.hookAllMethods(smh, "sendScreenshotMessage", XC_MethodReplacement.DO_NOTHING)
    }
    runCatching {
        val sch = classLoader.loadClass("org.telegram.messenger.SecretChatHelper")
        XposedBridge.hookAllMethods(sch, "sendScreenshotMessage", XC_MethodReplacement.DO_NOTHING)
    }
}

val TelegramVoiceToMusic = patch(
    name = "Voice to music",
    description = "Plays voice notes through the music-player path while preserving Raise-to-Listen.",
) {
    runCatching {
        val mo = classLoader.loadClass("org.telegram.messenger.MessageObject")
        XposedBridge.hookAllMethods(mo, "isMusic", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.result == true) return
                val obj = param.thisObject ?: return
                val isVoice = runCatching {
                    obj.javaClass.getMethod("isVoice").invoke(obj) as? Boolean
                }.getOrNull() ?: false
                if (isVoice) {
                    param.result = true
                }
            }
        })
    }
}

val TelegramBypassIntegrity = patch(
    name = "Bypass integrity check",
    description = "Spoofs certificate fingerprint and SafetyNet results so login works on patched APK.",
) {
    runCatching {
        val au = classLoader.loadClass("org.telegram.messenger.AndroidUtilities")
        XposedBridge.hookAllMethods(
            au,
            "getCertificateSHA256Fingerprint",
            XC_MethodReplacement.returnConstant("49C1522548EBACD46CE322B6FD47F6092BB745D0F88082145CAF35E14DCC38E1"),
        )
    }
}

val TelegramPatches = arrayOf(
    TelegramMorpheSettings,
    TelegramUnlockPremium,
    TelegramRemoveAds,
    TelegramAntiDelete,
    TelegramAntiDisappearingMedia,
    TelegramBypassContentRestrictions,
    TelegramBypassChannelRestrictions,
    TelegramDownloadBoost,
    TelegramHideStoriesRead,
    TelegramHideTyping,
    TelegramDisableAutoUpdate,
    TelegramDisableAnalytics,
    TelegramDisableRichHtmlPaste,
    TelegramAntiScreenshotNotification,
    TelegramVoiceToMusic,
    TelegramBypassIntegrity,
)
