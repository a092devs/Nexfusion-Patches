package io.github.nexfusion.nexfusion.telegram

import android.app.Activity
import android.content.Context
import android.view.View
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.nexfusion.compat.XC_MethodReplacement
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.compat.XposedHelpers
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import java.util.ArrayList

private fun isOfficialTelegram(classLoader: ClassLoader?): Boolean {
    val pkg = runCatching {
        val at = Class.forName("android.app.ActivityThread")
        at.getMethod("currentPackageName").invoke(null) as? String
    }.getOrNull()
    if (pkg != null) {
        if (pkg == "fork.risin42.nagramx" || pkg.contains("nagramx")) return false
        return true
    }
    return true
}

val TelegramAmoledBlack = patch(
    name = "AMOLED black",
    description = "Gives Telegram's dark themes pure black screens, bars, chats and dialogs for AMOLED displays.",
) {
    runCatching {
        val themeClasses = listOf("org.telegram.ui.ActionBar.u6", "org.telegram.ui.ActionBar.Theme")
        val themeClass = themeClasses.mapNotNull { runCatching { classLoader.loadClass(it) }.getOrNull() }.firstOrNull()

        val keyLookupClasses = listOf("org.telegram.ui.ActionBar.d5", "org.telegram.ui.ActionBar.Theme")
        val keyLookupClass = keyLookupClasses.mapNotNull { runCatching { classLoader.loadClass(it) }.getOrNull() }.firstOrNull()
        val keyLookupMethod = keyLookupClass?.declaredMethods?.firstOrNull {
            java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java &&
                it.returnType == java.lang.Integer.TYPE
        }

        fun getKeyId(name: String): Int {
            return runCatching {
                if (keyLookupMethod != null) {
                    (keyLookupMethod.invoke(null, name) as? Number)?.toInt() ?: -1
                } else -1
            }.getOrDefault(-1)
        }

        val surfaces = arrayOf(
            "windowBackgroundWhite", "windowBackgroundGray", "graySection", "windowBackgroundUnchecked",
            "actionBarDefault", "actionBarDefaultArchived", "actionBarDefaultSelector", "actionBarDefaultSubmenuBackground",
            "avatar_backgroundActionBarBlue", "chats_menuBackground", "chats_menuTopBackgroundCats", "chats_menuItemIcon",
            "chat_wallpaper", "chat_messagePanelBackground", "chat_emojiPanelBackground", "chat_topPanelBackground",
            "inappPlayerBackground", "glass_targetMainTabs", "glass_targetMainTopPanel",
            "dialogBackground", "dialogBackgroundGray", "sheet_bg", "sheet_scrollable_header",
            "player_background", "chat_selectedBackground", "switchTrack", "switchTrackChecked", "divider",
        )

        val amoledKeyIds = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

        fun populateKeyIds() {
            if (amoledKeyIds.isEmpty()) {
                for (s in surfaces) {
                    val id = getKeyId(s)
                    if (id >= 0) amoledKeyIds.add(id)
                }
            }
        }

        fun isColorDark(color: Int): Boolean {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            return (0.2126 * r + 0.7152 * g + 0.0722 * b) < 130
        }

        fun blackenArrays() {
            if (themeClass == null) return
            populateKeyIds()
            for (f in themeClass.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers) && android.util.SparseIntArray::class.java.isAssignableFrom(f.type)) {
                    runCatching {
                        f.isAccessible = true
                        val arr = f.get(null) as? android.util.SparseIntArray ?: return@runCatching
                        val winId = getKeyId("windowBackgroundWhite")
                        val isDark = if (winId >= 0 && arr.indexOfKey(winId) >= 0) {
                            isColorDark(arr.get(winId))
                        } else true
                        if (isDark) {
                            for (id in amoledKeyIds) {
                                if (arr.indexOfKey(id) >= 0) {
                                    arr.put(id, 0xFF000000.toInt())
                                }
                            }
                        }
                    }
                }
            }
        }

        if (themeClass != null) {
            // 1. Hook Theme file reader returning SparseIntArray
            themeClass.declaredMethods.filter {
                android.util.SparseIntArray::class.java.isAssignableFrom(it.returnType)
            }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val colors = param.result as? android.util.SparseIntArray ?: return
                        populateKeyIds()
                        val winId = getKeyId("windowBackgroundWhite")
                        val isDark = if (winId >= 0 && colors.indexOfKey(winId) >= 0) {
                            isColorDark(colors.get(winId))
                        } else true

                        if (isDark) {
                            for (id in amoledKeyIds) {
                                colors.put(id, 0xFF000000.toInt())
                            }
                            blackenArrays()
                        }
                    }
                })
            }

            // 2. Hook getColor methods: any static method returning int/Integer with key as first param
            themeClass.declaredMethods.filter {
                java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    (it.returnType == java.lang.Integer.TYPE || it.returnType == java.lang.Integer::class.java) &&
                    it.parameterTypes.isNotEmpty() && it.parameterTypes[0] == java.lang.Integer.TYPE
            }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = (param.args[0] as? Number)?.toInt() ?: return
                        populateKeyIds()
                        if (!amoledKeyIds.contains(key)) return
                        val currentColor = (param.result as? Number)?.toInt() ?: return
                        if (isColorDark(currentColor)) {
                            param.result = 0xFF000000.toInt()
                        }
                    }
                })
            }

            // 3. Hook wallpaper creation
            themeClass.declaredMethods.filter {
                it.parameterCount >= 10 &&
                    it.parameterTypes.any { pt -> android.util.SparseIntArray::class.java.isAssignableFrom(pt) }
            }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        populateKeyIds()
                        for (arg in param.args) {
                            if (arg is android.util.SparseIntArray) {
                                val winId = getKeyId("windowBackgroundWhite")
                                val isDark = if (winId >= 0 && arg.indexOfKey(winId) >= 0) {
                                    isColorDark(arg.get(winId))
                                } else true
                                if (isDark) {
                                    for (id in amoledKeyIds) {
                                        arg.put(id, 0xFF000000.toInt())
                                    }
                                }
                            }
                        }
                    }
                })
            }
        }

        // 4. Hook LaunchActivity window background
        val launchActivity = runCatching { classLoader.loadClass("org.telegram.ui.LaunchActivity") }.getOrNull()
        if (launchActivity != null) {
            XposedBridge.hookAllMethods(launchActivity, "onCreate", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    activity.window?.decorView?.setBackgroundColor(0xFF000000.toInt())
                    blackenArrays()
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        activity.splashScreen?.setSplashScreenTheme(android.R.style.Theme_Black_NoTitleBar)
                    }
                }
            })
            XposedBridge.hookAllMethods(launchActivity, "onResume", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    blackenArrays()
                }
            })
        }
    }.onFailure { PatcherLog.info(it) { "Telegram: AMOLED black hook failed" } }
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
    description = "Removes sponsored messages and video ads in channels.",
) {
    runCatching {
        val mo = classLoader.loadClass("org.telegram.messenger.MessageObject")
        XposedBridge.hookAllMethods(mo, "isSponsored", XC_MethodReplacement.returnConstant(false))
    }

    runCatching {
        val mc = classLoader.loadClass("org.telegram.messenger.MessagesController")
        XposedBridge.hookAllMethods(mc, "isChannelAdHidden", XC_MethodReplacement.returnConstant(true))
        XposedBridge.hookAllMethods(mc, "getSponsoredMessages", XC_MethodReplacement.returnConstant(null))
    }

    runCatching {
        val videoAds = classLoader.loadClass("org.telegram.messenger.video.VideoAds")
        XposedBridge.hookAllMethods(videoAds, "hasAds", XC_MethodReplacement.returnConstant(false))
    }
}

val TelegramAntiDelete = patch(
    name = "Anti-delete messages",
    description = "Prevents messages deleted by other users from being removed locally while allowing immediate self-deletion.",
) {
    runCatching {
        val mc = classLoader.loadClass("org.telegram.messenger.MessagesController")
        XposedBridge.hookAllMethods(mc, "deleteMessagesByPush", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = null // suppress remote push deletion
            }
        })
    }

    runCatching {
        val nc = classLoader.loadClass("org.telegram.messenger.NotificationsController")
        XposedBridge.hookAllMethods(nc, "removeDeletedMessagesFromNotifications", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = null
            }
        })
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
        XposedBridge.hookAllMethods(mo, "isRestricted", XC_MethodReplacement.returnConstant(false))
        XposedBridge.hookAllMethods(mo, "getRestrictionReason", XC_MethodReplacement.returnConstant(null))
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
    description = "Prevents the client from sending Stories read receipts.",
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
        XposedBridge.hookAllMethods(mc, "sendTyping", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = true // Telegram considers it handled/sent and drops transmission
            }
        })
    }

    runCatching {
        val cm = classLoader.loadClass("org.telegram.tgnet.ConnectionsManager")
        cm.declaredMethods.filter { it.name == "sendRequest" }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val req = param.args.firstOrNull() ?: return
                    val name = req.javaClass.name
                    if (name.contains("TL_messages_setTyping") || name.contains("setTyping")) {
                        param.result = 0 // Drop typing request token
                    }
                }
            })
        }
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
        XposedBridge.hookAllMethods(mc, "checkAppUpdate", XC_MethodReplacement.DO_NOTHING)
    }
}

val TelegramDisableAnalytics = patch(
    name = "Disable analytics",
    description = "Prevents sending telemetry, crashlytics and diagnostic events to Telegram.",
) {
    runCatching {
        val appLoader = classLoader.loadClass("org.telegram.messenger.ApplicationLoader")
        XposedBridge.hookAllMethods(appLoader, "initAppCenter", XC_MethodReplacement.DO_NOTHING)
        XposedBridge.hookAllMethods(appLoader, "startPushLogService", XC_MethodReplacement.DO_NOTHING)
    }
}

val TelegramDisableDoubleTapReaction = patch(
    name = "Disable double-tap reaction",
    description = "Prevents sending accidental message reactions on double tap in chat view.",
) {
    runCatching {
        val cellClass = classLoader.loadClass("org.telegram.ui.Cells.ChatMessageCell")
        cellClass.declaredMethods.filter {
            it.name.contains("Reaction", ignoreCase = true) &&
                it.parameterTypes.any { pt -> pt.name.contains("Reaction") || pt.name.contains("View") }
        }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }
}

val TelegramRevealSpoilers = patch(
    name = "Auto-reveal spoilers",
    description = "Automatically reveals hidden spoiler text without tapping.",
) {
    runCatching {
        val spoilerClass = classLoader.loadClass("org.telegram.ui.Components.spoilers.SpoilerEffect")
        XposedBridge.hookAllMethods(spoilerClass, "isRippling", XC_MethodReplacement.returnConstant(false))
    }
}

val TelegramDisableVibration = patch(
    name = "Disable vibration",
    description = "Turns off haptic vibration feedback for in-app gestures.",
) {
    if (!isOfficialTelegram(classLoader)) return@patch
    runCatching {
        val au = classLoader.loadClass("org.telegram.messenger.AndroidUtilities")
        au.declaredMethods.filter {
            it.name.contains("vibrat", ignoreCase = true) || it.name.contains("haptic", ignoreCase = true)
        }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        }
    }
}

val TelegramShowSeconds = patch(
    name = "Show message seconds",
    description = "Displays seconds in message time stamps.",
) {
    runCatching {
        val formatter = classLoader.loadClass("org.telegram.messenger.LocaleController")
        formatter.declaredMethods.filter {
            it.name == "formatDateChat" && it.parameterCount >= 1 && it.returnType == String::class.java
        }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val timestamp = (param.args[0] as? Number)?.toLong() ?: return
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp * 1000L }
                    param.result = String.format("%02d:%02d:%02d",
                        cal.get(java.util.Calendar.HOUR_OF_DAY),
                        cal.get(java.util.Calendar.MINUTE),
                        cal.get(java.util.Calendar.SECOND)
                    )
                }
            })
        }
    }
}

val TelegramExactNumbers = patch(
    name = "Exact numbers",
    description = "Displays exact subscriber and member counts without rounding (e.g. 4812 instead of 4.8K).",
) {
    runCatching {
        val lc = classLoader.loadClass("org.telegram.messenger.LocaleController")
        lc.declaredMethods.filter {
            it.name == "formatShortNumber" && it.returnType == String::class.java
        }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val num = (param.args.firstOrNull() as? Number)?.toInt() ?: return
                    param.result = String.format(java.util.Locale.US, "%,d", num)
                }
            })
        }
    }
}

val TelegramAntiScreenshotNotification = patch(
    name = "Anti-screenshot notification",
    description = "Prevents sending screenshot notifications in secret chats.",
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
    name = "Voice to music player",
    description = "Plays voice and audio notes in the Telegram background music player.",
) {
    runCatching {
        val mo = classLoader.loadClass("org.telegram.messenger.MessageObject")
        XposedBridge.hookAllMethods(mo, "isMusic", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val obj = param.thisObject ?: return
                val isVoice = runCatching {
                    val m = obj.javaClass.getMethod("isVoice")
                    m.invoke(obj) as? Boolean ?: false
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
    description = "Spoofs certificate fingerprint so login works on patched APK.",
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
    TelegramAmoledBlack,
    TelegramUnlockPremium,
    TelegramRemoveAds,
    TelegramAntiDelete,
    TelegramAntiDisappearingMedia,
    TelegramBypassContentRestrictions,
    TelegramBypassChannelRestrictions,
    TelegramDownloadBoost,
    TelegramHideStoriesRead,
    TelegramHideTyping,
    TelegramDisableDoubleTapReaction,
    TelegramRevealSpoilers,
    TelegramDisableVibration,
    TelegramShowSeconds,
    TelegramExactNumbers,
    TelegramDisableAutoUpdate,
    TelegramDisableAnalytics,
    TelegramAntiScreenshotNotification,
    TelegramVoiceToMusic,
    TelegramBypassIntegrity,
)
