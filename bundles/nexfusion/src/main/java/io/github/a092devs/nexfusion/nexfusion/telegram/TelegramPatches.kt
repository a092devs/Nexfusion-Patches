package io.github.a092devs.nexfusion.nexfusion.telegram

import android.app.Activity
import android.content.Context
import android.view.View
import io.github.a092devs.nexfusion.compat.XC_MethodHook
import io.github.a092devs.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.a092devs.nexfusion.compat.XC_MethodReplacement
import io.github.a092devs.nexfusion.compat.XposedBridge
import io.github.a092devs.nexfusion.compat.XposedHelpers
import io.github.a092devs.nexfusion.PatcherLog
import io.github.a092devs.nexfusion.patch
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

// Dynamic reflection helpers to resolve Telegram internal classes across all versions (unobfuscated, FOSS, 13.0, 13.3+)
internal object TelegramResolver {
    fun findUserConfig(classLoader: ClassLoader): Class<*>? {
        for (name in listOf("org.telegram.messenger.y10", "org.telegram.messenger.UserConfig")) {
            val cls = runCatching { classLoader.loadClass(name) }.getOrNull()
            if (cls != null) return cls
        }
        for (name in listOf("org.telegram.messenger.c1", "org.telegram.messenger.d1", "org.telegram.messenger.BaseController", "yg.v2")) {
            val cls = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            val m = cls.declaredMethods.firstOrNull { it.name == "getUserConfig" && it.parameterCount == 0 }
            if (m != null && m.returnType != java.lang.Void.TYPE && m.returnType != Any::class.java) {
                return m.returnType
            }
        }
        return null
    }

    fun findMessagesController(classLoader: ClassLoader): Class<*>? {
        for (name in listOf("org.telegram.messenger.tp", "org.telegram.messenger.sp", "org.telegram.messenger.MessagesController")) {
            val cls = runCatching { classLoader.loadClass(name) }.getOrNull()
            if (cls != null) return cls
        }
        for (name in listOf("org.telegram.messenger.c1", "org.telegram.messenger.d1", "org.telegram.messenger.BaseController", "yg.v2")) {
            val cls = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            val m = cls.declaredMethods.firstOrNull { it.name == "getMessagesController" && it.parameterCount == 0 }
            if (m != null && m.returnType != java.lang.Void.TYPE && m.returnType != Any::class.java) {
                return m.returnType
            }
        }
        return null
    }

    fun findMessagesStorage(classLoader: ClassLoader): Class<*>? {
        for (name in listOf("org.telegram.messenger.es", "org.telegram.messenger.is", "org.telegram.messenger.MessagesStorage")) {
            val cls = runCatching { classLoader.loadClass(name) }.getOrNull()
            if (cls != null) return cls
        }
        for (name in listOf("org.telegram.messenger.c1", "org.telegram.messenger.d1", "org.telegram.messenger.BaseController", "yg.v2")) {
            val cls = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            val m = cls.declaredMethods.firstOrNull { it.name == "getMessagesStorage" && it.parameterCount == 0 }
            if (m != null && m.returnType != java.lang.Void.TYPE && m.returnType != Any::class.java) {
                return m.returnType
            }
        }
        return null
    }

    fun findStoriesController(classLoader: ClassLoader): Class<*>? {
        for (name in listOf("yg.e5", "wg.f5", "zg.d5", "org.telegram.ui.Stories.StoriesController")) {
            val cls = runCatching { classLoader.loadClass(name) }.getOrNull()
            if (cls != null) return cls
        }
        return null
    }

    fun findMessageObject(classLoader: ClassLoader): Class<*>? {
        for (name in listOf("org.telegram.messenger.jj", "org.telegram.messenger.hj", "org.telegram.messenger.MessageObject")) {
            val cls = runCatching { classLoader.loadClass(name) }.getOrNull()
            if (cls != null) return cls
        }
        return null
    }

    fun findNotificationsController(classLoader: ClassLoader): Class<*>? {
        for (name in listOf("org.telegram.messenger.nz", "org.telegram.messenger.nt", "org.telegram.messenger.NotificationsController")) {
            val cls = runCatching { classLoader.loadClass(name) }.getOrNull()
            if (cls != null) return cls
        }
        return null
    }

    fun findThemeClass(classLoader: ClassLoader): Class<*>? {
        for (name in listOf("org.telegram.ui.ActionBar.u6", "org.telegram.ui.ActionBar.Theme", "org.telegram.ui.ActionBar.t6")) {
            val cls = runCatching { classLoader.loadClass(name) }.getOrNull()
            if (cls != null) return cls
        }
        return null
    }

    fun findThemeKeyLookup(classLoader: ClassLoader): Class<*>? {
        for (name in listOf("org.telegram.ui.ActionBar.d5", "org.telegram.ui.ActionBar.g5", "org.telegram.ui.ActionBar.Theme")) {
            val cls = runCatching { classLoader.loadClass(name) }.getOrNull()
            if (cls != null) return cls
        }
        return null
    }

    fun findTimeStringMethod(classLoader: ClassLoader): java.lang.reflect.Method? {
        val moClass = findMessageObject(classLoader)
        for (name in listOf("org.telegram.ui.Cells.q1", "org.telegram.ui.Cells.ChatMessageCell", "org.telegram.ui.Cells.u0")) {
            val cls = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            val m = cls.declaredMethods.firstOrNull {
                java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    it.returnType == String::class.java &&
                    it.parameterCount == 4 &&
                    (moClass == null || it.parameterTypes[0] == moClass) &&
                    it.parameterTypes[2] == java.lang.Boolean.TYPE &&
                    it.parameterTypes[3] == java.lang.Integer.TYPE
            }
            if (m != null) return m
        }
        return null
    }
}

val TelegramAmoledBlack = patch(
    name = "AMOLED black",
    description = "Gives Telegram's dark themes pure black screens, bars, chats and dialogs for AMOLED displays.",
) {
    runCatching {
        val themeClass = TelegramResolver.findThemeClass(classLoader)
        val keyLookupClass = TelegramResolver.findThemeKeyLookup(classLoader)
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
                                tapestry@for (id in amoledKeyIds) {
                                    arg.put(id, 0xFF000000.toInt())
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
    fun unlockUser(user: Any?) {
        if (user == null) return
        runCatching { XposedHelpers.setBooleanField(user, "premium", true) }
        runCatching {
            val flags = XposedHelpers.getIntField(user, "flags")
            XposedHelpers.setIntField(user, "flags", flags or 2048)
        }
    }

    // 1. Hook UserConfig (org.telegram.messenger.UserConfig / y10 / d20)
    runCatching {
        val userConfig = TelegramResolver.findUserConfig(classLoader)
        if (userConfig != null) {
            // ONLY hook isPremium: q() in 13.3.0, or isPremium in unobfuscated.
            // ONLY return true when currentUser != null so empty account slots are never treated as active/premium!
            userConfig.declaredMethods.filter {
                (it.returnType == java.lang.Boolean.TYPE || it.returnType == java.lang.Boolean::class.java) &&
                    it.parameterCount == 0 &&
                    (it.name == "isPremium" || it.name == "q")
            }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val obj = param.thisObject ?: return
                        val user = runCatching { XposedHelpers.getObjectField(obj, "c") }.getOrNull()
                            ?: runCatching { XposedHelpers.callMethod(obj, "getCurrentUser") }.getOrNull()
                        if (user != null) {
                            param.result = true
                        }
                    }
                })
            }

            // getCurrentUser() -> f() in 13.3.0
            userConfig.declaredMethods.filter {
                it.parameterCount == 0 && (it.name == "getCurrentUser" || it.name == "f")
            }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        unlockUser(param.result)
                    }
                })
            }

            // setCurrentUser(User) -> w(User) in 13.3.0
            userConfig.declaredMethods.filter {
                it.parameterCount == 1 && (it.name == "setCurrentUser" || it.name == "w")
            }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        unlockUser(param.args.firstOrNull())
                    }
                })
            }

            // loadConfig() -> b() in 13.3.0
            userConfig.declaredMethods.filter {
                it.parameterCount == 0 && (it.name == "loadConfig" || it.name == "b")
            }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val obj = param.thisObject ?: return
                        val u = runCatching { XposedHelpers.getObjectField(obj, "c") }.getOrNull()
                            ?: runCatching { XposedHelpers.callMethod(obj, "getCurrentUser") }.getOrNull()
                        unlockUser(u)
                    }
                })
            }
            PatcherLog.info { "Telegram: hooked UserConfig (${userConfig.name}) for Premium unlock" }
        }
    }.onFailure { PatcherLog.info(it) { "Telegram: failed hooking UserConfig for Premium" } }

    // 2. Hook MessagesController.isPremiumUser (B2, Z4, H2 in 13.3.0) safely for self/premium only
    runCatching {
        val mc = TelegramResolver.findMessagesController(classLoader)
        if (mc != null) {
            mc.declaredMethods.filter {
                (it.name == "isPremiumUser" || it.name == "l5" || it.name == "B2" || it.name == "Z4" || it.name == "H2") &&
                    it.parameterCount == 1 &&
                    (it.returnType == java.lang.Boolean.TYPE || it.returnType == java.lang.Boolean::class.java)
            }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val user = param.args.firstOrNull() ?: return
                        val isSelf = runCatching { XposedHelpers.getBooleanField(user, "self") }.getOrDefault(false)
                        val isPrem = runCatching { XposedHelpers.getBooleanField(user, "premium") }.getOrDefault(false)
                        if (isSelf || isPrem) {
                            param.result = true
                        }
                    }
                })
            }
            PatcherLog.info { "Telegram: hooked MessagesController (${mc.name}) for Premium unlock" }
        }
    }.onFailure { PatcherLog.info(it) { "Telegram: failed hooking MessagesController for Premium" } }

    // 3. SharedConfig device performance class
    runCatching {
        for (name in listOf("org.telegram.messenger.SharedConfig", "org.telegram.messenger.tz")) {
            val sc = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            sc.declaredMethods.filter {
                (it.returnType == java.lang.Integer.TYPE || it.returnType == java.lang.Integer::class.java) &&
                    it.parameterCount == 0 &&
                    (it.name == "getDevicePerformanceClass" || (java.lang.reflect.Modifier.isStatic(it.modifiers) && it.name.length <= 2))
            }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(2))
            }
        }
    }
}

val TelegramRemoveAds = patch(
    name = "Remove ads",
    description = "Removes sponsored messages and video ads in channels.",
) {
    // 1. Intercept sponsored network requests in ConnectionsManager
    runCatching {
        val cm = runCatching { classLoader.loadClass("org.telegram.tgnet.ConnectionsManager") }.getOrNull()
        if (cm != null) {
            XposedBridge.hookAllMethods(cm, "sendRequest", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val req = param.args.firstOrNull() ?: return
                    val name = req.javaClass.name
                    if (name.contains("Sponsored", ignoreCase = true)) {
                        val delegate = param.args.getOrNull(1)
                        if (delegate != null) {
                            runCatching {
                                val method = delegate.javaClass.methods.firstOrNull { it.name == "run" && it.parameterCount == 2 }
                                method?.invoke(delegate, null, null)
                            }
                        }
                        param.result = 0
                    }
                }
            })
        }
    }

    // 2. Hook MessageObject sponsored checks
    runCatching {
        val mo = TelegramResolver.findMessageObject(classLoader)
        if (mo != null) {
            mo.declaredMethods.filter {
                (it.returnType == java.lang.Boolean.TYPE || it.returnType == java.lang.Boolean::class.java) &&
                    it.parameterCount == 0 &&
                    (it.name == "isSponsored" || it.name == "r4")
            }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(false))
            }
        }
    }

    // 3. Hook MessagesController isChannelAdHidden / getSponsoredMessages
    runCatching {
        val mc = TelegramResolver.findMessagesController(classLoader)
        if (mc != null) {
            mc.declaredMethods.filter {
                it.name == "isChannelAdHidden" || it.name == "getSponsoredMessages"
            }.forEach { m ->
                if (m.returnType == java.lang.Boolean.TYPE) {
                    XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(true))
                } else {
                    XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(null))
                }
            }
        }
    }

    // 4. Hook VideoAds
    runCatching {
        val videoAds = runCatching { classLoader.loadClass("org.telegram.messenger.video.VideoAds") }.getOrNull()
        if (videoAds != null) {
            XposedBridge.hookAllMethods(videoAds, "hasAds", XC_MethodReplacement.returnConstant(false))
            XposedBridge.hookAllMethods(videoAds, "load", XC_MethodReplacement.DO_NOTHING)
        }
    }
}

val TelegramAntiDelete = patch(
    name = "Anti-delete messages",
    description = "Prevents messages deleted by other users from being removed locally and marks them with an indicator icon in the chat bubble.",
) {
    val deletedMessageIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    var prefsLoaded = false

    fun getPrefs(): android.content.SharedPreferences? {
        return runCatching {
            val appLoader = classLoader.loadClass("org.telegram.messenger.ApplicationLoader")
            val ctx = appLoader.getField("applicationContext").get(null) as? Context
            ctx?.getSharedPreferences("nexfusion_tg_deleted_msgs", Context.MODE_PRIVATE)
        }.getOrNull() ?: runCatching {
            val at = Class.forName("android.app.ActivityThread")
            val currentApp = at.getMethod("currentApplication").invoke(null) as? android.app.Application
            currentApp?.applicationContext?.getSharedPreferences("nexfusion_tg_deleted_msgs", Context.MODE_PRIVATE)
        }.getOrNull()
    }

    fun ensureLoaded() {
        if (!prefsLoaded) {
            val sp = getPrefs()
            if (sp != null) {
                prefsLoaded = true
                for (k in sp.all.keys) {
                    if (k.startsWith("d_")) {
                        val id = k.removePrefix("d_").toIntOrNull()
                        if (id != null) deletedMessageIds.add(id)
                    }
                }
            }
        }
    }

    fun requestUIRefresh() {
        runCatching {
            val auCls = runCatching { classLoader.loadClass("org.telegram.messenger.AndroidUtilities") }.getOrNull()
            val runOnUIThread = auCls?.methods?.firstOrNull { it.name == "runOnUIThread" && it.parameterTypes.size == 1 }
            val ncCls = runCatching { classLoader.loadClass("org.telegram.messenger.NotificationCenter") }.getOrNull()
            val mcCls = TelegramResolver.findMessagesController(classLoader)

            val updateMask = runCatching { mcCls?.getField("UPDATE_MASK_ALL")?.getInt(null) }.getOrNull() ?: 255
            val updateInterfaces = runCatching { ncCls?.getField("updateInterfaces")?.getInt(null) }.getOrNull()
            val updateAllMessages = runCatching { ncCls?.getField("updateAllMessages")?.getInt(null) }.getOrNull()

            if (runOnUIThread != null && ncCls != null) {
                runOnUIThread.invoke(null, java.lang.Runnable {
                    for (acc in 0..3) {
                        runCatching {
                            val nc = ncCls.getMethod("getInstance", Int::class.javaPrimitiveType).invoke(null, acc)
                            val postMethod = ncCls.methods.firstOrNull {
                                it.name == "postNotificationName" && it.parameterTypes.size == 2 && it.parameterTypes[0] == Int::class.javaPrimitiveType
                            }
                            if (updateAllMessages != null) {
                                postMethod?.invoke(nc, updateAllMessages, emptyArray<Any?>())
                            }
                            if (updateInterfaces != null) {
                                postMethod?.invoke(nc, updateInterfaces, arrayOf(updateMask))
                            }
                        }
                    }
                })
            }
        }
    }

    fun markDeleted(ids: Collection<*>) {
        ensureLoaded()
        val sp = getPrefs()
        val editor = sp?.edit()
        var changed = false
        for (item in ids) {
            val mid = (item as? Number)?.toInt() ?: continue
            if (deletedMessageIds.add(mid)) {
                changed = true
                editor?.putBoolean("d_$mid", true)
            }
        }
        editor?.apply()
        if (changed) {
            requestUIRefresh()
        }
    }

    fun isDeleted(id: Int): Boolean {
        if (id <= 0) return false
        ensureLoaded()
        if (deletedMessageIds.contains(id)) return true
        val sp = getPrefs()
        return sp?.getBoolean("d_$id", false) == true
    }

    fun getMessageId(msgObj: Any?): Int {
        if (msgObj == null) return 0
        runCatching {
            val m = msgObj.javaClass.methods.firstOrNull { it.name == "getId" && it.parameterCount == 0 }
            if (m != null) {
                val v = (m.invoke(msgObj) as? Number)?.toInt() ?: 0
                if (v > 0) return v
            }
        }
        for (f in msgObj.javaClass.declaredFields) {
            if (f.type.name.contains("TLRPC\$Message")) {
                f.isAccessible = true
                val owner = runCatching { f.get(msgObj) }.getOrNull()
                if (owner != null) {
                    val v = runCatching { XposedHelpers.getIntField(owner, "id") }.getOrDefault(0)
                    if (v > 0) return v
                }
            }
        }
        val owner = runCatching { XposedHelpers.getObjectField(msgObj, "messageOwner") }.getOrNull()
            ?: runCatching { XposedHelpers.getObjectField(msgObj, "k") }.getOrNull()
        if (owner != null) {
            return runCatching { XposedHelpers.getIntField(owner, "id") }.getOrDefault(0)
        }
        return 0
    }

    // 1. Clear deleted message IDs from incoming delete updates and track them
    runCatching {
        val tlrpc = runCatching { classLoader.loadClass("org.telegram.tgnet.TLRPC") }.getOrNull()
        if (tlrpc != null) {
            tlrpc.declaredMethods.filter {
                java.lang.reflect.Modifier.isStatic(it.modifiers) && it.name == "deserialize"
            }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val obj = param.result ?: return
                        val name = obj.javaClass.name
                        if (name.contains("updateDeleteMessages") || name.contains("updateDeleteChannelMessages")) {
                            val messages = runCatching { XposedHelpers.getObjectField(obj, "messages") as? java.util.ArrayList<*> }.getOrNull()
                            if (messages != null && messages.isNotEmpty()) {
                                markDeleted(messages)
                                messages.clear()
                            }
                        }
                    }
                })
            }
        }
        for (name in listOf(
            "org.telegram.tgnet.tl.TL_update\$TL_updateDeleteMessages",
            "org.telegram.tgnet.tl.TL_update\$TL_updateDeleteChannelMessages",
        )) {
            val cls = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            cls.declaredMethods.filter { it.name == "readParams" }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val messages = runCatching { XposedHelpers.getObjectField(param.thisObject, "messages") as? java.util.ArrayList<*> }.getOrNull()
                        if (messages != null && messages.isNotEmpty()) {
                            markDeleted(messages)
                            messages.clear()
                        }
                    }
                })
            }
        }
    }

    // 2. Track messages deleted via MessagesController and suppress remote push deletion
    runCatching {
        val mc = TelegramResolver.findMessagesController(classLoader)
        if (mc != null) {
            mc.declaredMethods.filter {
                it.name == "deleteMessagesByPush"
            }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = null
                    }
                })
            }
            mc.declaredMethods.filter {
                it.name.startsWith("deleteMessages") && !it.name.contains("Push") &&
                    it.parameterTypes.any { pt -> java.util.List::class.java.isAssignableFrom(pt) }
            }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        for (arg in param.args) {
                            if (arg is java.util.List<*>) {
                                markDeleted(arg)
                            }
                        }
                    }
                })
            }
        }
    }

    // 3. Mark deleted messages with cute trash icon indicator in chat bubbles
    runCatching {
        val timeStringMethod = TelegramResolver.findTimeStringMethod(classLoader)
        if (timeStringMethod != null) {
            XposedBridge.hookMethod(timeStringMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val current = param.args.firstOrNull() ?: return
                    val id = getMessageId(current)
                    if (id > 0 && isDeleted(id)) {
                        val orig = (param.result as? CharSequence)?.toString() ?: ""
                        if (!orig.contains("🗑")) {
                            param.result = if (orig.isEmpty()) "🗑️" else "🗑️ $orig"
                        }
                    }
                }
            })
            PatcherLog.info { "Telegram: hooked timeString (${timeStringMethod.declaringClass.name}.${timeStringMethod.name}) for deleted message mark" }
        }
    }.onFailure { PatcherLog.info(it) { "Telegram: failed to hook timeString for deleted mark" } }

    // 4. Slightly grey-out / dim deleted chat bubbles (alpha = 0.78f) matching NagramXF
    runCatching {
        val moClass = TelegramResolver.findMessageObject(classLoader)
        for (name in listOf("org.telegram.ui.Cells.ChatMessageCell", "org.telegram.ui.Cells.q1", "org.telegram.ui.Cells.u0")) {
            val cellClass = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            cellClass.declaredMethods.filter {
                (it.name == "setMessageObject" || (it.parameterTypes.isNotEmpty() && moClass != null && it.parameterTypes[0] == moClass)) &&
                    !java.lang.reflect.Modifier.isStatic(it.modifiers)
            }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? View ?: return
                        val msgObj = param.args.firstOrNull()
                        val id = getMessageId(msgObj)
                        if (id > 0 && isDeleted(id)) {
                            view.alpha = 0.78f
                        } else {
                            view.alpha = 1.0f
                        }
                    }
                })
            }
        }
    }
}

val TelegramAntiDisappearingMedia = patch(
    name = "Anti-disappearing media",
    description = "Keeps view-once photos, videos and voice messages viewable indefinitely across foreground and unopened chats.",
) {
    fun neutralizeMessage(msg: Any?) {
        if (msg == null) return
        runCatching { XposedHelpers.setIntField(msg, "ttl_period", 0) }
        runCatching { XposedHelpers.setIntField(msg, "ttl", 0) }
        runCatching { XposedHelpers.setIntField(msg, "destroyTime", 0) }
        runCatching { XposedHelpers.setLongField(msg, "destroyTimeMillis", 0L) }
        runCatching { XposedHelpers.setIntField(msg, "expire_date", 0) }
        runCatching { XposedHelpers.setBooleanField(msg, "noforwards", false) }
        val media = runCatching { XposedHelpers.getObjectField(msg, "media") }.getOrNull()
        if (media != null) {
            runCatching { XposedHelpers.setIntField(media, "ttl_seconds", 0) }
        }
    }

    fun neutralizeMedia(media: Any?) {
        if (media == null) return
        runCatching { XposedHelpers.setIntField(media, "ttl_seconds", 0) }
    }

    fun neutralizeObject(obj: Any?) {
        if (obj == null) return
        if (obj is java.util.Collection<*>) {
            for (item in obj) neutralizeObject(item)
        } else {
            neutralizeMessage(obj)
            neutralizeMedia(obj)
            val owner = runCatching { XposedHelpers.getObjectField(obj, "messageOwner") }.getOrNull()
            if (owner != null) neutralizeMessage(owner)
        }
    }

    // 1. Hook TLRPC.deserialize (catches ALL incoming objects across all threads/sync routines)
    runCatching {
        val tlrpc = runCatching { classLoader.loadClass("org.telegram.tgnet.TLRPC") }.getOrNull()
        if (tlrpc != null) {
            tlrpc.declaredMethods.filter {
                java.lang.reflect.Modifier.isStatic(it.modifiers) && it.name == "deserialize"
            }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        neutralizeObject(param.result)
                    }
                })
            }
        }
    }

    // 2. Hook TLRPC$Message and TL_message readParams & TLdeserialize
    runCatching {
        for (name in listOf(
            "org.telegram.tgnet.TLRPC\$Message",
            "org.telegram.tgnet.TLRPC\$TL_message",
            "org.telegram.tgnet.TLRPC\$TL_message_layer104",
            "org.telegram.tgnet.TLRPC\$TL_message_layer117",
            "org.telegram.tgnet.TLRPC\$TL_message_layer72",
            "org.telegram.tgnet.TLRPC\$TL_message_secret",
            "org.telegram.tgnet.TLRPC\$TL_message_secret_layer72",
            "org.telegram.tgnet.TLRPC\$TL_message_secret_old",
        )) {
            val cls = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            cls.declaredMethods.filter { it.name == "readParams" || it.name == "TLdeserialize" }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        neutralizeMessage(param.result ?: param.thisObject)
                    }
                })
            }
        }
    }

    // 3. Hook TLRPC$MessageMedia and all subclasses readParams & TLdeserialize
    runCatching {
        for (name in listOf(
            "org.telegram.tgnet.TLRPC\$MessageMedia",
            "org.telegram.tgnet.TLRPC\$TL_messageMediaPhoto",
            "org.telegram.tgnet.TLRPC\$TL_messageMediaPhoto_layer223",
            "org.telegram.tgnet.TLRPC\$TL_messageMediaPhoto_layer74",
            "org.telegram.tgnet.TLRPC\$TL_messageMediaDocument",
            "org.telegram.tgnet.TLRPC\$TL_messageMediaDocument_layer197",
            "org.telegram.tgnet.TLRPC\$TL_messageMediaDocument_layer187",
            "org.telegram.tgnet.TLRPC\$TL_messageMediaDocument_layer159",
            "org.telegram.tgnet.TLRPC\$TL_messageMediaDocument_layer74",
        )) {
            val cls = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            cls.declaredMethods.filter { it.name == "readParams" || it.name == "TLdeserialize" }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        neutralizeMedia(param.result ?: param.thisObject)
                    }
                })
            }
        }
    }

    // 4. Hook MessageObject constructors
    runCatching {
        val mo = TelegramResolver.findMessageObject(classLoader)
        if (mo != null) {
            XposedBridge.hookAllConstructors(mo, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val obj = param.thisObject ?: return
                    val owner = runCatching { XposedHelpers.getObjectField(obj, "messageOwner") }.getOrNull()
                        ?: runCatching { XposedHelpers.getObjectField(obj, "k") }.getOrNull()
                    neutralizeMessage(owner)
                    runCatching { XposedHelpers.setBooleanField(obj, "forceExpired", false) }
                    for (f in obj.javaClass.declaredFields) {
                        if (f.type == java.lang.Boolean.TYPE && f.name == "forceExpired") {
                            f.isAccessible = true
                            runCatching { f.setBoolean(obj, false) }
                        }
                    }
                }
            })
        }
    }

    // 5. Hook MessagesStorage (es / MessagesStorage) to neutralize before persisting and cancel TTL tasks
    runCatching {
        val ms = TelegramResolver.findMessagesStorage(classLoader)
        if (ms != null) {
            for (m in ms.declaredMethods) {
                val hasMsgParam = m.parameterTypes.any { pt ->
                    pt.name.contains("Message") || java.util.Collection::class.java.isAssignableFrom(pt)
                }
                if (hasMsgParam) {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            for (arg in param.args) {
                                neutralizeObject(arg)
                            }
                        }
                    })
                }

                // Prevent createTaskForMid (6 params: long, int, int, int, int, boolean)
                if (m.parameterCount == 6 && m.parameterTypes[0] == java.lang.Long.TYPE && m.parameterTypes[5] == java.lang.Boolean.TYPE) {
                    XposedBridge.hookMethod(m, XC_MethodReplacement.DO_NOTHING)
                }

                // Prevent emptyMessagesMedia, checkMessagesTTL, getNewTask
                if (m.name == "emptyMessagesMedia" || m.name == "checkMessagesTTL" || m.name == "getNewTask" ||
                    (m.parameterCount == 2 && m.parameterTypes[0] == java.lang.Long.TYPE && java.util.List::class.java.isAssignableFrom(m.parameterTypes[1]))
                ) {
                    XposedBridge.hookMethod(m, XC_MethodReplacement.DO_NOTHING)
                }
            }
        }

        // Emptying runners
        for (qName in listOf("org.telegram.messenger.qr", "org.telegram.messenger.ur")) {
            val qCls = runCatching { classLoader.loadClass(qName) }.getOrNull()
            if (qCls != null) {
                qCls.declaredMethods.filter { it.name == "run" && it.parameterCount == 0 }.forEach { m ->
                    XposedBridge.hookMethod(m, XC_MethodReplacement.DO_NOTHING)
                }
            }
        }
    }

    // 6. Hook MessagesController view-once / timer expiration / task processing
    runCatching {
        val mc = TelegramResolver.findMessagesController(classLoader)
        if (mc != null) {
            mc.declaredMethods.filter {
                it.name.contains("DeleteShowOnce", ignoreCase = true) || it.name == "createDeleteShowOnceTask"
            }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(0L))
            }
            mc.declaredMethods.filter {
                it.name == "markMessageContentAsRead" || it.name == "markMessageAsRead2" || it.name == "processLoadedDeleteTask"
            }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.DO_NOTHING)
            }
            mc.declaredMethods.filter {
                it.name == "checkDeletingTask" && it.returnType == java.lang.Boolean.TYPE
            }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(false))
            }
        }
    }

    // 7. Strip FLAG_SECURE on Window so view-once media can always be captured/recorded
    runCatching {
        val windowClass = Class.forName("android.view.Window")
        XposedBridge.hookAllMethods(windowClass, "setFlags", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val flags = (param.args.firstOrNull() as? Number)?.toInt() ?: return
                if ((flags and 0x2000) != 0) {
                    param.args[0] = flags and 0x2000.inv()
                }
            }
        })
        XposedBridge.hookAllMethods(windowClass, "addFlags", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val flags = (param.args.firstOrNull() as? Number)?.toInt() ?: return
                if ((flags and 0x2000) != 0) {
                    param.args[0] = flags and 0x2000.inv()
                }
            }
        })
    }

    // 8. Hook MessageObject secret / voice once getters (l4, S4, g4, m5, R5, m4 in 13.3.0)
    runCatching {
        val mo = TelegramResolver.findMessageObject(classLoader)
        if (mo != null) {
            mo.declaredMethods.filter {
                (it.returnType == java.lang.Boolean.TYPE || it.returnType == java.lang.Boolean::class.java) &&
                    (it.name == "isSecretMedia" || it.name == "isSecretPhotoOrVideo" || it.name == "shouldEncryptPhotoOrVideo" ||
                     it.name == "isVoiceOnce" || it.name == "isRoundOnce" || it.name == "needDrawBluredPreview" ||
                     it.name == "l4" || it.name == "S4" || it.name == "g4" || it.name == "m5" || it.name == "R5" || it.name == "m4")
            }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(false))
            }
        }
    }
}

val TelegramHideStories = patch(
    name = "Hide stories",
    description = "Completely removes stories bar, story rings on avatars, and stops story network fetching.",
) {
    // 1. Suppress story list network requests in ConnectionsManager
    runCatching {
        val cm = runCatching { classLoader.loadClass("org.telegram.tgnet.ConnectionsManager") }.getOrNull()
        if (cm != null) {
            XposedBridge.hookAllMethods(cm, "sendRequest", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val req = param.args.firstOrNull() ?: return
                    val name = req.javaClass.name
                    if (name.contains("stories", ignoreCase = true) ||
                        name.contains("getAllStories", ignoreCase = true) ||
                        name.contains("getAllReadPeerStories", ignoreCase = true) ||
                        name.contains("getStoriesArchive", ignoreCase = true) ||
                        name.contains("getStoriesByID", ignoreCase = true)
                    ) {
                        param.result = 0
                    }
                }
            })
        }
    }

    // 2. Hook StoriesController (wg.f5 / yg.e5 / StoriesController)
    runCatching {
        val sc = TelegramResolver.findStoriesController(classLoader)
        if (sc != null) {
            // All boolean methods on StoriesController (hasStories, hasSelfStories, hasUnreadStories, hasHiddenStories, etc.)
            sc.declaredMethods.filter {
                (it.returnType == java.lang.Boolean.TYPE || it.returnType == java.lang.Boolean::class.java) &&
                    (it.parameterCount <= 2)
            }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(false))
            }

            // Methods returning story lists (ArrayList) -> empty list
            sc.declaredMethods.filter {
                java.util.List::class.java.isAssignableFrom(it.returnType)
            }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = ArrayList<Any>()
                    }
                })
            }

            // Total stories count -> 0 (D, E, x in 13.3.0)
            sc.declaredMethods.filter {
                (it.returnType == java.lang.Integer.TYPE || it.returnType == java.lang.Integer::class.java) &&
                    it.parameterCount <= 1
            }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(0))
            }
        }
    }
}

val TelegramHideStoriesRead = patch(
    name = "Hide Stories read status",
    description = "Prevents the client from sending Stories read receipts.",
) {
    runCatching {
        val cm = runCatching { classLoader.loadClass("org.telegram.tgnet.ConnectionsManager") }.getOrNull()
        if (cm != null) {
            XposedBridge.hookAllMethods(cm, "sendRequest", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val req = param.args.firstOrNull() ?: return
                    if (req.javaClass.name.contains("readStories", ignoreCase = true)) {
                        param.result = 0
                    }
                }
            })
        }
    }
    runCatching {
        val sc = TelegramResolver.findStoriesController(classLoader)
        if (sc != null) {
            sc.declaredMethods.filter {
                (it.name.contains("read", ignoreCase = true) || it.name.contains("mark", ignoreCase = true)) &&
                    it.returnType == java.lang.Boolean.TYPE
            }.forEach { method ->
                XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(true))
            }
        }
    }
}

val TelegramBypassContentRestrictions = patch(
    name = "Bypass content restrictions",
    description = "Allows saving and forwarding content from restricted channels, chats, and users.",
) {
    // 1. Clear noforwards on Message, Chat, UserFull and StoryItem deserialization
    runCatching {
        for (name in listOf(
            "org.telegram.tgnet.TLRPC\$Message",
            "org.telegram.tgnet.TLRPC\$Chat",
            "org.telegram.tgnet.tl.TL_stories\$StoryItem",
        )) {
            val cls = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            cls.declaredMethods.filter { it.name == "readParams" || it.name == "TLdeserialize" }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val target = param.result ?: param.thisObject ?: return
                        runCatching { XposedHelpers.setBooleanField(target, "noforwards", false) }
                    }
                })
            }
        }
        val userFull = runCatching { classLoader.loadClass("org.telegram.tgnet.TLRPC\$UserFull") }.getOrNull()
        if (userFull != null) {
            userFull.declaredMethods.filter { it.name == "readParams" || it.name == "TLdeserialize" }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val target = param.result ?: param.thisObject ?: return
                        runCatching { XposedHelpers.setBooleanField(target, "noforwards_peer_enabled", false) }
                    }
                })
            }
        }
    }

    // 2. Strip FLAG_SECURE on Window so copy-protected chats allow screenshots
    runCatching {
        val windowClass = Class.forName("android.view.Window")
        XposedBridge.hookAllMethods(windowClass, "setFlags", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val flags = (param.args.firstOrNull() as? Number)?.toInt() ?: return
                if ((flags and 0x2000) != 0) {
                    param.args[0] = flags and 0x2000.inv()
                }
            }
        })
        XposedBridge.hookAllMethods(windowClass, "addFlags", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val flags = (param.args.firstOrNull() as? Number)?.toInt() ?: return
                if ((flags and 0x2000) != 0) {
                    param.args[0] = flags and 0x2000.inv()
                }
            }
        })
    }

    // 3. Hook MessageObject.canForwardMessage() -> return true
    runCatching {
        val mo = TelegramResolver.findMessageObject(classLoader)
        if (mo != null) {
            mo.declaredMethods.filter {
                (it.returnType == java.lang.Boolean.TYPE || it.returnType == java.lang.Boolean::class.java) &&
                    (it.name == "canForwardMessage" || it.name == "w")
            }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(true))
            }
        }
    }
}

val TelegramBypassChannelRestrictions = patch(
    name = "Bypass channel restrictions",
    description = "Allows viewing restricted and sensitive channels and content.",
) {
    runCatching {
        val mc = TelegramResolver.findMessagesController(classLoader)
        if (mc != null) {
            mc.declaredMethods.filter { it.name == "getRestrictionReason" }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(null))
            }
            mc.declaredMethods.filter { it.name == "isSensitive" }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(false))
            }
            mc.declaredMethods.filter { it.name == "showSensitiveContent" }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(true))
            }
            mc.declaredMethods.filter { it.name == "checkChannelError" || it.name == "showCantOpenAlert" }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.DO_NOTHING)
            }
        }
    }

    runCatching {
        val mo = TelegramResolver.findMessageObject(classLoader)
        if (mo != null) {
            mo.declaredMethods.filter { it.name == "isRestricted" }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(false))
            }
            mo.declaredMethods.filter { it.name == "getRestrictionReason" }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(null))
            }
        }
    }
}

val TelegramDownloadBoost = patch(
    name = "Download speed boost",
    description = "Accelerates downloads by enabling 512 KB chunks and 8 concurrent parallel connections.",
) {
    if (!isOfficialTelegram(classLoader)) return@patch

    // 1. Enable getfileExperimentalParams in MessagesController (tp / I3 in 13.3.0)
    runCatching {
        val mc = TelegramResolver.findMessagesController(classLoader)
        if (mc != null) {
            mc.declaredConstructors.forEach { c ->
                XposedBridge.hookMethod(c, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val obj = param.thisObject ?: return
                        for (f in obj.javaClass.declaredFields) {
                            if (f.type == java.lang.Boolean.TYPE && (f.name == "getfileExperimentalParams" || f.name == "I3")) {
                                f.isAccessible = true
                                runCatching { f.setBoolean(obj, true) }
                            }
                            if (f.type == java.lang.Boolean.TYPE && (f.name == "android_download_improvement_killswitch" || f.name == "J3")) {
                                f.isAccessible = true
                                runCatching { f.setBoolean(obj, false) }
                            }
                        }
                    }
                })
            }
        }
    }

    // 2. Hook SharedPreferences.getBoolean for getfileExperimentalParams
    runCatching {
        val spClass = Class.forName("android.content.SharedPreferences")
        val getBooleanMethods = spClass.declaredMethods.filter {
            it.name == "getBoolean" && it.parameterCount == 2 && it.parameterTypes[0] == String::class.java
        }
        for (m in getBooleanMethods) {
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val key = param.args[0] as? String ?: return
                    if (key == "getfileExperimentalParams") {
                        param.result = true
                    } else if (key == "android_download_improvement_killswitch") {
                        param.result = false
                    }
                }
            })
        }
    }

    // 3. Fallback: Hook FileLoadOperation (d7) updateParams (t()) to safely ensure 512 KB chunk size
    runCatching {
        val d7Class = runCatching { classLoader.loadClass("org.telegram.messenger.d7") }.getOrNull()
            ?: runCatching { classLoader.loadClass("org.telegram.messenger.FileLoadOperation") }.getOrNull()
        if (d7Class != null) {
            val updateParamsMethods = d7Class.declaredMethods.filter {
                it.parameterCount == 0 && it.returnType == java.lang.Void.TYPE &&
                    (it.name == "t" || it.name == "updateParams")
            }
            for (m in updateParamsMethods) {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val obj = param.thisObject ?: return
                        runCatching {
                            val nField = runCatching { d7Class.getDeclaredField("n") }.getOrNull()
                                ?: d7Class.declaredFields.firstOrNull { it.name == "downloadChunkSizeBig" }
                            if (nField != null && nField.type == java.lang.Integer.TYPE) {
                                nField.isAccessible = true
                                nField.setInt(obj, 524288) // 512 KB
                            }
                            val oField = runCatching { d7Class.getDeclaredField("o") }.getOrNull()
                                ?: d7Class.declaredFields.firstOrNull { it.name == "maxDownloadRequests" }
                            if (oField != null && oField.type == java.lang.Integer.TYPE) {
                                oField.isAccessible = true
                                oField.setInt(obj, 8)
                            }
                            val pField = runCatching { d7Class.getDeclaredField("p") }.getOrNull()
                                ?: d7Class.declaredFields.firstOrNull { it.name == "maxDownloadRequestsBig" }
                            if (pField != null && pField.type == java.lang.Integer.TYPE) {
                                pField.isAccessible = true
                                pField.setInt(obj, 8)
                            }
                            val qField = runCatching { d7Class.getDeclaredField("q") }.getOrNull()
                                ?: d7Class.declaredFields.firstOrNull { it.name == "maxCdnParts" }
                            if (qField != null && qField.type == java.lang.Integer.TYPE) {
                                qField.isAccessible = true
                                qField.setInt(obj, (4294967296L / 524288).toInt())
                            }
                        }
                    }
                })
            }
        }
    }
}

val TelegramHideTyping = patch(
    name = "Hide typing indicator",
    description = "Blocks sending typing status and indicators to other users.",
) {
    runCatching {
        val cm = runCatching { classLoader.loadClass("org.telegram.tgnet.ConnectionsManager") }.getOrNull()
        if (cm != null) {
            XposedBridge.hookAllMethods(cm, "sendRequest", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val req = param.args.firstOrNull() ?: return
                    val name = req.javaClass.name
                    if (name.contains("setTyping", ignoreCase = true) || name.contains("TL_messages_setTyping")) {
                        param.result = 0
                    }
                }
            })
        }
    }
    runCatching {
        val mc = TelegramResolver.findMessagesController(classLoader)
        if (mc != null) {
            mc.declaredMethods.filter { it.name.contains("sendTyping", ignoreCase = true) }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                })
            }
        }
    }
}

val TelegramDisableAutoUpdate = patch(
    name = "Disable auto-update",
    description = "Disables Telegram update availability and in-app update prompts.",
) {
    runCatching {
        for (name in listOf("org.telegram.messenger.SharedConfig", "org.telegram.messenger.tz")) {
            val sc = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            sc.declaredMethods.filter {
                it.returnType == java.lang.Boolean.TYPE && it.name.contains("Update", ignoreCase = true)
            }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(false))
            }
        }
    }

    runCatching {
        val mc = TelegramResolver.findMessagesController(classLoader)
        if (mc != null) {
            mc.declaredMethods.filter { it.name.contains("checkAppUpdate", ignoreCase = true) }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.DO_NOTHING)
            }
        }
    }
}

val TelegramDisableAnalytics = patch(
    name = "Disable analytics",
    description = "Prevents sending telemetry, crashlytics and diagnostic events to Telegram.",
) {
    runCatching {
        val appLoader = runCatching { classLoader.loadClass("org.telegram.messenger.ApplicationLoader") }.getOrNull()
        if (appLoader != null) {
            appLoader.declaredMethods.filter {
                it.name.contains("initAppCenter", ignoreCase = true) || it.name.contains("startPushLog", ignoreCase = true)
            }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.DO_NOTHING)
            }
        }
    }
}

val TelegramDisableDoubleTapReaction = patch(
    name = "Disable double-tap reaction",
    description = "Prevents sending accidental message reactions on double tap in chat view.",
) {
    runCatching {
        for (name in listOf("org.telegram.ui.Cells.ChatMessageCell", "org.telegram.ui.Cells.u0")) {
            val cellClass = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
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
}

val TelegramRevealSpoilers = patch(
    name = "Auto-reveal spoilers",
    description = "Automatically reveals hidden spoiler text without tapping.",
) {
    runCatching {
        val spoilerClass = runCatching { classLoader.loadClass("org.telegram.ui.Components.spoilers.SpoilerEffect") }.getOrNull()
        if (spoilerClass != null) {
            XposedBridge.hookAllMethods(spoilerClass, "isRippling", XC_MethodReplacement.returnConstant(false))
        }
    }
}

val TelegramDisableVibration = patch(
    name = "Disable vibration",
    description = "Turns off haptic vibration feedback for in-app gestures.",
) {
    if (!isOfficialTelegram(classLoader)) return@patch
    runCatching {
        val au = runCatching { classLoader.loadClass("org.telegram.messenger.AndroidUtilities") }.getOrNull()
        if (au != null) {
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
}

val TelegramShowSeconds = patch(
    name = "Show message seconds",
    description = "Displays seconds in message time stamps.",
) {
    runCatching {
        for (name in listOf("org.telegram.messenger.LocaleController", "org.telegram.messenger.dd")) {
            val formatter = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
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
}

val TelegramExactNumbers = patch(
    name = "Exact numbers",
    description = "Displays exact subscriber and member counts without rounding (e.g. 4812 instead of 4.8K).",
) {
    runCatching {
        for (name in listOf("org.telegram.messenger.LocaleController", "org.telegram.messenger.dd")) {
            val lc = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
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
}

val TelegramAntiScreenshotNotification = patch(
    name = "Anti-screenshot notification",
    description = "Prevents sending screenshot notifications in secret chats.",
) {
    runCatching {
        for (name in listOf("org.telegram.messenger.SendMessagesHelper", "org.telegram.messenger.rz")) {
            val smh = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            smh.declaredMethods.filter { it.name.contains("Screenshot", ignoreCase = true) }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.DO_NOTHING)
            }
        }
    }
    runCatching {
        for (name in listOf("org.telegram.messenger.SecretChatHelper", "org.telegram.messenger.nw")) {
            val sch = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            sch.declaredMethods.filter { it.name.contains("Screenshot", ignoreCase = true) }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.DO_NOTHING)
            }
        }
    }
}

val TelegramVoiceToMusic = patch(
    name = "Voice to music player",
    description = "Plays voice and audio notes in the Telegram background music player.",
) {
    runCatching {
        val mo = TelegramResolver.findMessageObject(classLoader)
        if (mo != null) {
            mo.declaredMethods.filter {
                (it.returnType == java.lang.Boolean.TYPE || it.returnType == java.lang.Boolean::class.java) &&
                    (it.name == "isMusic" || it.name.contains("Music", ignoreCase = true))
            }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                })
            }
        }
    }
}

val TelegramAllowChatBlur = patch(
    name = "Allow chat blur",
    description = "Enables blurred chat header and panels on all devices regardless of performance tier.",
) {
    runCatching {
        for (name in listOf("org.telegram.messenger.SharedConfig", "org.telegram.messenger.tz")) {
            val sc = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            sc.declaredMethods.filter {
                (it.returnType == java.lang.Boolean.TYPE || it.returnType == java.lang.Boolean::class.java) &&
                    (it.name.contains("blur", ignoreCase = true) || it.name.contains("chatBlur", ignoreCase = true))
            }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(true))
            }
        }
    }
}

val TelegramDisableReactionEffects = patch(
    name = "Disable reaction effects",
    description = "Disables full-screen emoji burst and fly-in animations when reactions are added.",
) {
    runCatching {
        for (name in listOf(
            "org.telegram.ui.Components.Reactions.ReactionsEffectOverlay",
            "org.telegram.ui.Components.Reactions.ReactionFlyEffect",
        )) {
            val cls = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            cls.declaredMethods.filter { it.name.contains("show", ignoreCase = true) || it.name.contains("start", ignoreCase = true) }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.DO_NOTHING)
            }
        }
    }
}

val TelegramSwipeBack = patch(
    name = "Swipe back on profiles",
    description = "Enables right swipe on profile photos and media tabs to return to previous screen.",
) {
    runCatching {
        val ppv = runCatching { classLoader.loadClass("org.telegram.ui.ProfileActivity") }.getOrNull()
        if (ppv != null) {
            ppv.declaredMethods.filter { it.name == "hasCustomSwipeBack" || it.name == "isSwipeBackEnabled" }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(true))
            }
        }
    }
}

val TelegramStripLinkTracking = patch(
    name = "Strip link tracking",
    description = "Removes tracking parameters (utm_*, fbclid, gclid) from links.",
) {
    runCatching {
        val browser = runCatching { classLoader.loadClass("org.telegram.messenger.browser.Browser") }.getOrNull()
        if (browser != null) {
            browser.declaredMethods.filter {
                it.name == "openUrl" && it.parameterTypes.isNotEmpty() && it.parameterTypes[0] == android.content.Context::class.java
            }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        for (i in param.args.indices) {
                            val arg = param.args[i]
                            if (arg is String && (arg.contains("utm_") || arg.contains("fbclid=") || arg.contains("gclid="))) {
                                val cleaned = arg.replace(Regex("[?&](utm_[^&=]+|fbclid|gclid)=[^&#]*"), "")
                                    .replace("?&", "?").replace("&&", "&")
                                param.args[i] = cleaned
                            } else if (arg is android.net.Uri) {
                                val uriStr = arg.toString()
                                if (uriStr.contains("utm_") || uriStr.contains("fbclid=") || uriStr.contains("gclid=")) {
                                    val cleaned = uriStr.replace(Regex("[?&](utm_[^&=]+|fbclid|gclid)=[^&#]*"), "")
                                        .replace("?&", "?").replace("&&", "&")
                                    param.args[i] = android.net.Uri.parse(cleaned)
                                }
                            }
                        }
                    }
                })
            }
        }
    }
}

val TelegramDisableChatSwipe = patch(
    name = "Disable chat swipe actions",
    description = "Disables sideways swipe gesture on chat rows to prevent accidental archive, mute or delete.",
) {
    runCatching {
        for (name in listOf("org.telegram.ui.DialogsActivity", "org.telegram.ui.Components.ChatListItemAnimator")) {
            val cls = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            cls.declaredMethods.filter { it.name.contains("SwipeAction", ignoreCase = true) || it.name == "isSwipeActionEnabled" }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(false))
            }
        }
    }
}

val TelegramBypassIntegrity = patch(
    name = "Bypass integrity check",
    description = "Spoofs certificate fingerprint so login works on patched APK.",
) {
    runCatching {
        val au = runCatching { classLoader.loadClass("org.telegram.messenger.AndroidUtilities") }.getOrNull()
        if (au != null) {
            XposedBridge.hookAllMethods(
                au,
                "getCertificateSHA256Fingerprint",
                XC_MethodReplacement.returnConstant("49C1522548EBACD46CE322B6FD47F6092BB745D0F88082145CAF35E14DCC38E1"),
            )
        }
    }
}

val TelegramPatches = arrayOf(
    TelegramAmoledBlack,
    TelegramUnlockPremium,
    TelegramRemoveAds,
    TelegramAntiDelete,
    TelegramAntiDisappearingMedia,
    TelegramHideStories,
    TelegramHideStoriesRead,
    TelegramBypassContentRestrictions,
    TelegramBypassChannelRestrictions,
    TelegramDownloadBoost,
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
    TelegramAllowChatBlur,
    TelegramDisableReactionEffects,
    TelegramSwipeBack,
    TelegramStripLinkTracking,
    TelegramDisableChatSwipe,
    TelegramBypassIntegrity,
)
