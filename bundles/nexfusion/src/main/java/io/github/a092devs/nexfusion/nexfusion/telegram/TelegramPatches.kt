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
import io.github.a092devs.nexfusion.PatchExecutor
import io.github.a092devs.nexfusion.patch
import org.luckypray.dexkit.query.enums.StringMatchType
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

private fun PatchExecutor.resolveByControllerGetter(cacheKey: String, getterName: String): Class<*>? =
    runCatching {
        dexkit.getClassDirectOrNull(cacheKey) {
            findMethod {
                matcher {
                    name = getterName
                    paramCount = 0
                }
            }.first { it.returnTypeName != "void" && it.returnTypeName != "java.lang.Object" }.returnType!!
        }?.getInstance(classLoader)
    }.getOrNull()

private fun PatchExecutor.resolveUserConfig(): Class<*>? =
    TelegramResolver.findUserConfig(classLoader)
        ?: resolveByControllerGetter("tg_user_config", "getUserConfig")

private fun PatchExecutor.resolveMessagesController(): Class<*>? =
    TelegramResolver.findMessagesController(classLoader)
        ?: resolveByControllerGetter("tg_messages_controller", "getMessagesController")

private fun PatchExecutor.resolveMessagesStorage(): Class<*>? =
    TelegramResolver.findMessagesStorage(classLoader)
        ?: resolveByControllerGetter("tg_messages_storage", "getMessagesStorage")

private fun PatchExecutor.resolveStoriesController(): Class<*>? =
    TelegramResolver.findStoriesController(classLoader)
        ?: resolveByControllerGetter("tg_stories_controller", "getStoriesController")
        ?: runCatching {
            dexkit.getClassDirectOrNull("tg_stories_controller_tl") {
                findMethod {
                    matcher {
                        addInvoke { declaredClass = "org.telegram.tgnet.tl.TL_stories\$TL_stories_readStories" }
                    }
                }.first().declaredClass!!
            }?.getInstance(classLoader)
        }.getOrNull()

private fun PatchExecutor.resolveApplicationLoader(): Class<*>? =
    runCatching { classLoader.loadClass("org.telegram.messenger.ApplicationLoader") }.getOrNull()
        ?: runCatching {
            dexkit.getClassDirectOrNull("tg_app_loader") {
                findClass {
                    matcher {
                        className("org.telegram.messenger.", StringMatchType.StartsWith)
                        superClass = "android.app.Application"
                    }
                }.first()
            }?.getInstance(classLoader)
        }.getOrNull()

private fun PatchExecutor.resolveAndroidUtilities(): Class<*>? =
    runCatching { classLoader.loadClass("org.telegram.messenger.AndroidUtilities") }.getOrNull()
        ?: runCatching {
            dexkit.getClassDirectOrNull("tg_android_utilities") {
                findClass {
                    matcher {
                        className("org.telegram.messenger.", StringMatchType.StartsWith)
                        usingStrings("fonts/rmedium.ttf", "vibrator")
                    }
                }.first()
            }?.getInstance(classLoader)
        }.getOrNull()

private fun PatchExecutor.resolveSharedConfig(): Class<*>? =
    runCatching { classLoader.loadClass("org.telegram.messenger.SharedConfig") }.getOrNull()
        ?: runCatching {
            dexkit.getClassDirectOrNull("tg_shared_config") {
                findClass {
                    matcher {
                        className("org.telegram.messenger.", StringMatchType.StartsWith)
                        usingStrings("mainconfig")
                        fieldCount(30..500)
                    }
                }.first()
            }?.getInstance(classLoader)
        }.getOrNull()

private fun PatchExecutor.resolveLocaleController(): Class<*>? =
    runCatching { classLoader.loadClass("org.telegram.messenger.LocaleController") }.getOrNull()
        ?: runCatching {
            dexkit.getClassDirectOrNull("tg_locale_controller") {
                findClass {
                    matcher {
                        className("org.telegram.messenger.", StringMatchType.StartsWith)
                        usingStrings("formatDateChat")
                    }
                }.first()
            }?.getInstance(classLoader)
        }.getOrNull()

private fun PatchExecutor.resolveNotificationCenter(): Class<*>? =
    runCatching { classLoader.loadClass("org.telegram.messenger.NotificationCenter") }.getOrNull()
        ?: runCatching {
            dexkit.getClassDirectOrNull("tg_notification_center") {
                findClass {
                    matcher {
                        className("org.telegram.messenger.", StringMatchType.StartsWith)
                        fieldCount(200..1000)
                        addMethod {
                            name = "didReceivedNotification"
                        }
                    }
                }.firstOrNull() ?: findClass {
                    matcher {
                        className("org.telegram.messenger.", StringMatchType.StartsWith)
                        fieldCount(200..1000)
                        addField { type = "android.util.SparseArray" }
                    }
                }.first()
            }?.getInstance(classLoader)
        }.getOrNull()

private fun PatchExecutor.resolveBrowser(): Class<*>? =
    runCatching { classLoader.loadClass("org.telegram.messenger.browser.Browser") }.getOrNull()
        ?: runCatching {
            dexkit.getClassDirectOrNull("tg_browser") {
                findClass {
                    matcher {
                        usingStrings("android.support.customtabs.extra.SESSION")
                    }
                }.first()
            }?.getInstance(classLoader)
        }.getOrNull()

private fun PatchExecutor.resolveSpoilerEffect(): Class<*>? =
    runCatching { classLoader.loadClass("org.telegram.ui.Components.spoilers.SpoilerEffect") }.getOrNull()
        ?: runCatching {
            dexkit.getClassDirectOrNull("tg_spoiler_effect") {
                findMethod {
                    matcher {
                        name = "getSpoilerEffect"
                        paramCount = 0
                    }
                }.first().returnType!!
            }?.getInstance(classLoader)
        }.getOrNull()

private fun PatchExecutor.resolveNotificationsController(): Class<*>? =
    TelegramResolver.findNotificationsController(classLoader)
        ?: resolveByControllerGetter("tg_notifications_controller", "getNotificationsController")

private fun PatchExecutor.resolveMessageObject(): Class<*>? =
    TelegramResolver.findMessageObject(classLoader)
        ?: runCatching {
            dexkit.getClassDirectOrNull("tg_message_object") {
                findClass {
                    matcher {
                        className("org.telegram.messenger.", StringMatchType.StartsWith)
                        addMethod {
                            name = "isOutOwner"
                            paramCount = 0
                        }
                    }
                }.first()
            }?.getInstance(classLoader)
        }.getOrNull()

private fun PatchExecutor.resolveThemeClass(): Class<*>? =
    TelegramResolver.findThemeClass(classLoader)
        ?: runCatching {
            dexkit.getClassDirectOrNull("tg_theme") {
                findClass {
                    matcher {
                        className("org.telegram.ui.ActionBar.", StringMatchType.StartsWith)
                        usingStrings("windowBackgroundWhite", "windowBackgroundGray")
                    }
                }.first()
            }?.getInstance(classLoader)
        }.getOrNull()

private fun PatchExecutor.resolveThemeKeyLookup(): Class<*>? =
    TelegramResolver.findThemeKeyLookup(classLoader)
        ?: resolveThemeClass()

private fun PatchExecutor.resolveTimeStringMethod(): java.lang.reflect.Method? {
    TelegramResolver.findTimeStringMethod(classLoader)?.let { return it }
    val moClass = resolveMessageObject() ?: return null
    val cellClass = runCatching {
        dexkit.getMethodDirectOrNull("tg_time_string_method") {
            findMethod {
                matcher {
                    declaredClass("org.telegram.ui.Cells.", StringMatchType.StartsWith)
                    modifiers = java.lang.reflect.Modifier.STATIC
                    returnType = "java.lang.String"
                    paramTypes(moClass.name, null, "boolean", "int")
                }
            }.first()
        }
    }.getOrNull()
    return runCatching {
        val cls = cellClass?.let { classLoader.loadClass(it.className) } ?: return null
        cls.declaredMethods.firstOrNull {
            it.name == cellClass.name &&
                java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                it.returnType == String::class.java &&
                it.parameterCount == 4 &&
                it.parameterTypes[0] == moClass
        }
    }.getOrNull()
}

val TelegramAmoledBlack = patch(
    name = "AMOLED black",
    description = "Gives Telegram's dark themes pure black screens, bars, chats and dialogs for AMOLED displays.",
    use = false,
) {
    runCatching {
        val themeClass = resolveThemeClass()
        val keyLookupClass = resolveThemeKeyLookup()
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
    use = false,
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
        val userConfig = resolveUserConfig()
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
        val mc = resolveMessagesController()
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
    use = false,
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
        val mo = resolveMessageObject()
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
        val mc = resolveMessagesController()
        if (mc != null) {
            val dkMethod = runCatching {
                dexkit.getMethodDirectOrNull("tg_get_sponsored_msgs") {
                    findMethod {
                        matcher {
                            declaredClass = mc.name
                            addInvoke { declaredClass = "org.telegram.tgnet.TLRPC\$TL_messages_getSponsoredMessages" }
                        }
                    }.first()
                }
            }.getOrNull()
            mc.declaredMethods.filter {
                it.name == "isChannelAdHidden" || it.name == "getSponsoredMessages" || (dkMethod != null && it.name == dkMethod.name)
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
    use = false,
) {
    val deletedMessageIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    val activeCells = java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())
    var prefsLoaded = false

    fun getPrefs(): android.content.SharedPreferences? {
        return runCatching {
            val appLoader = resolveApplicationLoader()
            val ctx = (runCatching { appLoader?.getField("applicationContext")?.get(null) }.getOrNull()
                ?: appLoader?.declaredFields?.firstOrNull {
                    java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                        android.content.Context::class.java.isAssignableFrom(it.type)
                }?.apply { isAccessible = true }?.get(null)) as? Context
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

    fun requestUIRefresh() {
        runCatching {
            val auCls = resolveAndroidUtilities()
            val runOnUIThread = auCls?.methods?.firstOrNull {
                it.name == "runOnUIThread" && it.parameterTypes.size == 1
            } ?: auCls?.declaredMethods?.firstOrNull {
                java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    it.returnType == Void.TYPE &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == java.lang.Runnable::class.java
            }
            val ncCls = resolveNotificationCenter()
            val mcCls = resolveMessagesController()

            val updateMask = runCatching { mcCls?.getField("UPDATE_MASK_ALL")?.getInt(null) }.getOrNull() ?: 255
            val updateInterfaces = runCatching { ncCls?.getField("updateInterfaces")?.getInt(null) }.getOrNull()
            val updateAllMessages = runCatching { ncCls?.getField("updateAllMessages")?.getInt(null) }.getOrNull()

            if (runOnUIThread != null) {
                runOnUIThread.invoke(null, java.lang.Runnable {
                    // 1. Immediately update all visible chat cells and trigger adapter refresh for instant icon display
                    val parentsToNotify = mutableSetOf<Any>()
                    for (cell in activeCells) {
                        if (cell.isAttachedToWindow) {
                            val msgObj = runCatching {
                                cell.javaClass.methods.firstOrNull { it.name == "getMessageObject" }?.invoke(cell)
                            }.getOrNull()
                            val id = getMessageId(msgObj)
                            if (id > 0 && isDeleted(id)) {
                                cell.alpha = 0.72f
                                cell.invalidate()
                                val parent = cell.parent
                                if (parent != null) parentsToNotify.add(parent)
                            }
                        }
                    }
                    for (parent in parentsToNotify) {
                        runCatching {
                            val adapter = parent.javaClass.methods.firstOrNull { it.name == "getAdapter" }?.invoke(parent)
                            adapter?.javaClass?.methods?.firstOrNull { it.name == "notifyDataSetChanged" }?.invoke(adapter)
                        }
                        runCatching {
                            (parent as? View)?.invalidate()
                        }
                    }

                    // 2. Dispatch notification center updates
                    if (ncCls != null) {
                        val getInst = runCatching { ncCls.getMethod("getInstance", Int::class.javaPrimitiveType) }.getOrNull()
                            ?: ncCls.declaredMethods.firstOrNull {
                                java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                                    it.returnType == ncCls &&
                                    it.parameterTypes.size == 1 &&
                                    it.parameterTypes[0] == Int::class.javaPrimitiveType
                            }
                        val postMethod = ncCls.methods.firstOrNull {
                            it.name == "postNotificationName" && it.parameterTypes.size == 2 && it.parameterTypes[0] == Int::class.javaPrimitiveType
                        } ?: ncCls.declaredMethods.firstOrNull {
                            !java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                                it.returnType == Void.TYPE &&
                                it.parameterTypes.size == 2 &&
                                it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                                it.parameterTypes[1] == Array<Any>::class.java
                        }
                        if (getInst != null && postMethod != null) {
                            for (acc in 0..3) {
                                runCatching {
                                    val nc = getInst.invoke(null, acc)
                                    if (updateAllMessages != null) {
                                        postMethod.invoke(nc, updateAllMessages, emptyArray<Any?>())
                                    }
                                    if (updateInterfaces != null) {
                                        postMethod.invoke(nc, updateInterfaces, arrayOf(updateMask))
                                    }
                                }
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
                        if (name.contains("updateDelete", ignoreCase = true) || name.contains("DeleteMessages", ignoreCase = true)) {
                            val messages = (runCatching { XposedHelpers.getObjectField(obj, "messages") as? java.util.ArrayList<*> }.getOrNull()
                                ?: runCatching { XposedHelpers.getObjectField(obj, "ids") as? java.util.ArrayList<*> }.getOrNull())
                            if (messages != null && messages.isNotEmpty()) {
                                markDeleted(messages)
                                messages.clear()
                            }
                        }
                    }
                })
            }
        }
        for (suffix in listOf(
            "TL_updateDeleteMessages",
            "TL_updateDeleteChannelMessages",
            "TL_updateDeleteEphemeralMessages",
            "TL_updateDeleteScheduledMessages",
            "TL_updateDeleteQuickReplyMessages",
        )) {
            for (prefix in listOf("org.telegram.tgnet.tl.TL_update\$", "org.telegram.tgnet.TLRPC\$")) {
                val cls = runCatching { classLoader.loadClass(prefix + suffix) }.getOrNull() ?: continue
                cls.declaredMethods.filter { it.name == "readParams" }.forEach { m ->
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val messages = (runCatching { XposedHelpers.getObjectField(param.thisObject, "messages") as? java.util.ArrayList<*> }.getOrNull()
                                ?: runCatching { XposedHelpers.getObjectField(param.thisObject, "ids") as? java.util.ArrayList<*> }.getOrNull())
                            if (messages != null && messages.isNotEmpty()) {
                                markDeleted(messages)
                                messages.clear()
                            }
                        }
                    })
                }
            }
        }
    }

    // 2. Track messages deleted via MessagesController and suppress remote push deletion
    runCatching {
        val mc = resolveMessagesController()
        if (mc != null) {
            val pushDeleteMethod = runCatching {
                dexkit.getMethodDirectOrNull("tg_delete_msgs_by_push") {
                    findMethod {
                        matcher {
                            declaredClass = "org.telegram.messenger.PushListenerController"
                            usingStrings("processRemoteMessage")
                        }
                    }.first()
                }
            }.getOrNull()
            mc.declaredMethods.filter {
                it.name == "deleteMessagesByPush" ||
                    (it.returnType == java.lang.Void.TYPE &&
                        it.parameterTypes.size == 3 &&
                        it.parameterTypes[0] == java.lang.Long.TYPE &&
                        it.parameterTypes[1] == java.lang.Long.TYPE &&
                        java.util.List::class.java.isAssignableFrom(it.parameterTypes[2]))
            }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val list = param.args.getOrNull(2) as? java.util.List<*>
                        if (list != null && list.isNotEmpty()) {
                            markDeleted(list)
                        }
                        param.result = null
                    }
                })
            }
            @Suppress("UNUSED_VARIABLE")
            val unusedPushRef = pushDeleteMethod
            mc.declaredMethods.filter {
                it.name.startsWith("deleteMessages") && !it.name.contains("Push") &&
                    it.parameterTypes.any { pt -> java.util.List::class.java.isAssignableFrom(pt) } ||
                    (it.parameterTypes.size >= 4 &&
                        java.util.List::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                        java.util.List::class.java.isAssignableFrom(it.parameterTypes[1]) &&
                        it.parameterTypes[2].name.contains("EncryptedChat") &&
                        it.parameterTypes[3] == java.lang.Long.TYPE)
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

    // 3. Mark deleted messages with subtle trash icon indicator in chat bubble timestamp (matching NagramXF)
    runCatching {
        val timeStringMethod = resolveTimeStringMethod()
        if (timeStringMethod != null) {
            XposedBridge.hookMethod(timeStringMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val current = param.args.firstOrNull() ?: return
                    val id = getMessageId(current)
                    if (id > 0 && isDeleted(id)) {
                        val orig = (param.result as? CharSequence)?.toString() ?: ""
                        if (!orig.contains("🗑")) {
                            param.result = if (orig.isEmpty()) "🗑" else "🗑 $orig"
                        }
                    }
                }
            })
            PatcherLog.info { "Telegram: hooked timeString (${timeStringMethod.declaringClass.name}.${timeStringMethod.name}) for deleted message mark" }
        }
    }.onFailure { PatcherLog.info(it) { "Telegram: failed to hook timeString for deleted mark" } }

    // 4. Slightly grey-out / dim deleted chat bubbles (alpha = 0.72f) matching NagramXF and register cell
    runCatching {
        val moClass = resolveMessageObject()
        val timeCellClass = resolveTimeStringMethod()?.declaringClass
        val cellClasses = linkedSetOf<Class<*>>()
        if (timeCellClass != null) cellClasses.add(timeCellClass)
        for (name in listOf("org.telegram.ui.Cells.ChatMessageCell", "org.telegram.ui.Cells.q1", "org.telegram.ui.Cells.u0")) {
            runCatching { classLoader.loadClass(name) }.getOrNull()?.let { cellClasses.add(it) }
        }
        for (cellClass in cellClasses) {
            cellClass.declaredMethods.filter {
                (it.name == "setMessageObject" || (it.parameterTypes.isNotEmpty() && moClass != null && it.parameterTypes[0] == moClass)) &&
                    !java.lang.reflect.Modifier.isStatic(it.modifiers)
            }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? View ?: return
                        activeCells.add(view)
                        val msgObj = param.args.firstOrNull()
                        val id = getMessageId(msgObj)
                        if (id > 0 && isDeleted(id)) {
                            view.alpha = 0.72f
                        } else {
                            view.alpha = 1.0f
                        }
                    }
                })
            }
            cellClass.declaredMethods.filter { it.name == "onAttachedToWindow" }.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? View ?: return
                        activeCells.add(view)
                    }
                })
            }
        }
    }
}

val TelegramAntiDisappearingMedia = patch(
    name = "Anti-disappearing media",
    description = "Keeps view-once photos, videos and voice messages viewable indefinitely across foreground and unopened chats.",
    use = false,
) {
    // 1. Keep view-once messages from expiring or being marked force-expired
    runCatching {
        val mo = resolveMessageObject()
        if (mo != null) {
            XposedBridge.hookAllConstructors(mo, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val obj = param.thisObject ?: return
                    runCatching { XposedHelpers.setBooleanField(obj, "forceExpired", false) }
                    val owner = runCatching { XposedHelpers.getObjectField(obj, "messageOwner") }.getOrNull()
                        ?: runCatching { XposedHelpers.getObjectField(obj, "k") }.getOrNull()
                        ?: obj.javaClass.declaredFields.firstOrNull {
                            it.type.name.endsWith("TLRPC\$Message")
                        }?.apply { isAccessible = true }?.let { runCatching { it.get(obj) }.getOrNull() }
                    if (owner != null) {
                        runCatching { XposedHelpers.setIntField(owner, "destroyTime", 0) }
                        runCatching { XposedHelpers.setLongField(owner, "destroyTimeMillis", 0L) }
                        runCatching { XposedHelpers.setBooleanField(owner, "noforwards", false) }
                        runCatching { XposedHelpers.setIntField(owner, "ttl", 0) }
                        runCatching { XposedHelpers.setIntField(owner, "ttl_period", 0) }
                        runCatching { XposedHelpers.setIntField(owner, "expire_date", 0) }
                        val media = runCatching { XposedHelpers.getObjectField(owner, "media") }.getOrNull()
                        if (media != null) {
                            runCatching { XposedHelpers.setIntField(media, "ttl_seconds", 0) }
                        }
                    }
                }
            })
            mo.declaredMethods.filter {
                (it.returnType == java.lang.Boolean.TYPE || it.returnType == java.lang.Boolean::class.java) &&
                    it.parameterCount == 0 &&
                    (it.name.contains("Expired", ignoreCase = true) || it.name == "isExpiredViewOnce")
            }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(false))
            }
        }
    }

    // 2. Prevent MessagesController from executing view-once deletion tasks
    runCatching {
        val mc = resolveMessagesController()
        val mo = resolveMessageObject()
        if (mc != null) {
            mc.declaredMethods.filter {
                it.name.contains("didViewShowOnce", ignoreCase = true) ||
                    it.name.contains("DeleteShowOnce", ignoreCase = true) ||
                    it.name == "createDeleteShowOnceTask"
            }.forEach { m ->
                if (m.returnType == java.lang.Long.TYPE) {
                    XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(0L))
                } else {
                    XposedBridge.hookMethod(m, XC_MethodReplacement.DO_NOTHING)
                }
            }
            if (mo != null) {
                val readContentsMethod = runCatching {
                    dexkit.getMethodDirectOrNull("tg_read_msg_contents") {
                        findMethod {
                            matcher {
                                declaredClass = mc.name
                                paramTypes(mo.name)
                                returnType = "void"
                                addInvoke { declaredClass = "org.telegram.tgnet.TLRPC\$TL_messages_readMessageContents" }
                            }
                        }.first()
                    }
                }.getOrNull()
                if (readContentsMethod != null) {
                    mc.declaredMethods.firstOrNull {
                        it.name == readContentsMethod.name && it.parameterCount == 1 && it.parameterTypes[0] == mo
                    }?.let { m ->
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val msgObj = param.args.firstOrNull() ?: return
                                val owner = runCatching { XposedHelpers.getObjectField(msgObj, "messageOwner") }.getOrNull()
                                    ?: runCatching { XposedHelpers.getObjectField(msgObj, "k") }.getOrNull()
                                    ?: msgObj.javaClass.declaredFields.firstOrNull {
                                        it.type.name.endsWith("TLRPC\$Message")
                                    }?.apply { isAccessible = true }?.let { runCatching { it.get(msgObj) }.getOrNull() }
                                if (owner != null) {
                                    val ttl = runCatching { XposedHelpers.getIntField(owner, "ttl") }.getOrDefault(0)
                                    val ttlPeriod = runCatching { XposedHelpers.getIntField(owner, "ttl_period") }.getOrDefault(0)
                                    val destroyTime = runCatching { XposedHelpers.getIntField(owner, "destroyTime") }.getOrDefault(0)
                                    val mediaTtl = runCatching {
                                        val media = XposedHelpers.getObjectField(owner, "media")
                                        if (media != null) XposedHelpers.getIntField(media, "ttl_seconds") else 0
                                    }.getOrDefault(0)
                                    if (ttl > 0 || ttlPeriod > 0 || destroyTime > 0 || mediaTtl > 0 || owner.javaClass.name.contains("secret", ignoreCase = true)) {
                                        param.result = null
                                    }
                                }
                            }
                        })
                    }
                }
            }
        }
    }

    // 3. Strip FLAG_SECURE on Window so view-once media can always be captured/recorded
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
}

val TelegramHideStories = patch(
    name = "Hide stories",
    description = "Completely removes stories bar, story rings on avatars, and stops story network fetching.",
    use = false,
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
        val sc = resolveStoriesController()
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

val TelegramBypassContentRestrictions = patch(
    name = "Bypass content restrictions",
    description = "Allows saving and forwarding content from restricted channels, chats, and users.",
    use = false,
) {
    // 1. Clear noforwards on Message, Chat, UserFull and StoryItem deserialization
    runCatching {
        for (name in listOf(
            "org.telegram.tgnet.TLRPC\$Message",
            "org.telegram.tgnet.TLRPC\$Chat",
            "org.telegram.tgnet.TLRPC\$ChatFull",
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

    // 3. Hook MessageObject canForwardMessage() / canSaveMessage() -> return true
    runCatching {
        val mo = resolveMessageObject()
        if (mo != null) {
            mo.declaredMethods.filter {
                (it.returnType == java.lang.Boolean.TYPE || it.returnType == java.lang.Boolean::class.java) &&
                    (it.name == "canForwardMessage" || it.name == "canSaveMessage" || it.name == "canSaveToGallery" || it.name == "w")
            }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(true))
            }
        }
    }
}

val TelegramBypassChannelRestrictions = patch(
    name = "Bypass channel restrictions",
    description = "Allows viewing restricted and sensitive channels and content.",
    use = false,
) {
    runCatching {
        val mc = resolveMessagesController()
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
        val mo = resolveMessageObject()
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
    use = false,
) {
    if (!isOfficialTelegram(classLoader)) return@patch

    // 1. Enable getfileExperimentalParams in MessagesController (tp / I3 in 13.3.0)
    runCatching {
        val mc = resolveMessagesController()
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
    use = false,
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
        val mc = resolveMessagesController()
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
    use = false,
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
        val mc = resolveMessagesController()
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
    use = false,
) {
    runCatching {
        val appLoader = resolveApplicationLoader()
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
    use = false,
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
    description = "Automatically reveals hidden spoiler text and media without tapping.",
    use = false,
) {
    runCatching {
        val spoilerClass = resolveSpoilerEffect()
        if (spoilerClass != null) {
            XposedBridge.hookAllMethods(spoilerClass, "isRevealed", XC_MethodReplacement.returnConstant(true))
            XposedBridge.hookAllMethods(spoilerClass, "setRevealed", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args.isNotEmpty() && param.args[0] is Boolean) {
                        param.args[0] = true
                    }
                }
            })
            XposedBridge.hookAllMethods(spoilerClass, "draw", XC_MethodReplacement.DO_NOTHING)
        }
    }

    runCatching {
        val spoiler2Class = classLoader.loadClass("org.telegram.ui.Components.spoilers.SpoilerEffect2")
        XposedBridge.hookAllMethods(spoiler2Class, "draw", XC_MethodReplacement.DO_NOTHING)
    }

    runCatching {
        val mo = resolveMessageObject()
        if (mo != null) {
            XposedBridge.hookAllMethods(mo, "hasMediaSpoilers", XC_MethodReplacement.returnConstant(false))
        }
    }
}

val TelegramDisableVibration = patch(
    name = "Disable vibration",
    description = "Turns off haptic vibration feedback for in-app gestures.",
    use = false,
) {
    if (!isOfficialTelegram(classLoader)) return@patch
    runCatching {
        val au = resolveAndroidUtilities()
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
    use = false,
) {
    runCatching {
        val fastDateFormat = classLoader.loadClass("org.telegram.messenger.time.FastDateFormat")
        val formatMethod = fastDateFormat.getMethod("format", Long::class.javaPrimitiveType)
        val clockRegex = Regex("""(?<![:\d])\d{1,2}:\d{2}(?![:\d])""")
        val editedId = appContext.resources.getIdentifier("EditedMessage", "string", appContext.packageName)

        val timeMethod = dexkit.getMethodDirectOrNull("tg_time_method") {
            findMethod {
                matcher {
                    if (editedId != 0) addUsingNumber(editedId)
                }
            }.first {
                it.paramTypeNames.any { p -> p.endsWith("MessageObject") } &&
                    (it.returnTypeName == "void" || it.returnTypeName == "java.lang.String")
            }
        }

        val inTimeMethod = ThreadLocal.withInitial { 0 }
        if (timeMethod != null) {
            runCatching {
                timeMethod.hookMethod(object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        inTimeMethod.set(inTimeMethod.get() + 1)
                    }
                    override fun afterHookedMethod(param: MethodHookParam) {
                        inTimeMethod.set((inTimeMethod.get() - 1).coerceAtLeast(0))
                    }
                })
            }
        }

        XposedBridge.hookMethod(formatMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (inTimeMethod.get() <= 0) return
                val time = param.result as? String ?: return
                val millis = (param.args.firstOrNull() as? Number)?.toLong() ?: return
                val match = clockRegex.find(time) ?: return
                val seconds = ((millis / 1000L) % 60L + 60L) % 60L
                val secStr = if (seconds < 10L) ":0$seconds" else ":$seconds"
                param.result = time.substring(0, match.range.last + 1) + secStr + time.substring(match.range.last + 1)
            }
        })
    }
}

val TelegramExactNumbers = patch(
    name = "Exact numbers",
    description = "Displays exact subscriber and member counts without rounding (e.g. 4812 instead of 4.8K).",
    use = false,
) {
    runCatching {
        val lcClasses = linkedSetOf<Class<*>>()
        resolveLocaleController()?.let { lcClasses.add(it) }
        for (name in listOf("org.telegram.messenger.LocaleController", "org.telegram.messenger.dd")) {
            runCatching { classLoader.loadClass(name) }.getOrNull()?.let { lcClasses.add(it) }
        }
        for (lc in lcClasses) {
            lc.declaredMethods.filter {
                it.returnType == String::class.java && (
                    it.name == "formatShortNumber" ||
                        (java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                            it.parameterCount == 2 &&
                            it.parameterTypes[0] == java.lang.Integer.TYPE &&
                            it.parameterTypes[1] == IntArray::class.java)
                    )
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
    use = false,
) {
    runCatching {
        val cm = runCatching { classLoader.loadClass("org.telegram.tgnet.ConnectionsManager") }.getOrNull()
        if (cm != null) {
            XposedBridge.hookAllMethods(cm, "sendRequest", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val req = param.args.firstOrNull() ?: return
                    val name = req.javaClass.name
                    if (name.contains("ScreenshotNotification", ignoreCase = true)) {
                        param.result = 0
                    }
                }
            })
        }
    }
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
    use = false,
) {
    runCatching {
        val mo = resolveMessageObject()
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
    use = false,
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
    use = false,
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
    description = "Makes a swipe right on a profile's photos or media tabs go back, like the rest of the profile.",
    use = false,
) {
    runCatching {
        val profileClass = classLoader.loadClass("org.telegram.ui.ProfileActivity")
        val motionEventClass = android.view.MotionEvent::class.java
        val swipeMethod = profileClass.declaredMethods.firstOrNull {
            (it.name == "isSwipeBackEnabled" || it.name == "hasCustomSwipeBack") &&
                it.returnType == java.lang.Boolean.TYPE &&
                it.parameterTypes.contentEquals(arrayOf(motionEventClass))
        } ?: dexkit.getMethodDirectOrNull("tg_profile_swipe_back") {
            findMethod {
                matcher {
                    declaredClass = "org.telegram.ui.ProfileActivity"
                    returnType = "boolean"
                    paramTypes("android.view.MotionEvent")
                }
            }.first()
        }?.let { md ->
            profileClass.getDeclaredMethod(md.name, motionEventClass)
        }

        if (swipeMethod != null) {
            val inSwipeCheck = ThreadLocal.withInitial { 0 }
            XposedBridge.hookMethod(swipeMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    inSwipeCheck.set(inSwipeCheck.get() + 1)
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    inSwipeCheck.set((inSwipeCheck.get() - 1).coerceAtLeast(0))
                }
            })
            val rectContains = android.graphics.Rect::class.java.getMethod(
                "contains",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            XposedBridge.hookMethod(rectContains, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (inSwipeCheck.get() > 0) {
                        param.result = false
                    }
                }
            })
        }
    }
}

val TelegramStripLinkTracking = patch(
    name = "Strip link tracking",
    description = "Removes tracking parameters (utm_*, fbclid, gclid) from links.",
    use = false,
) {
    runCatching {
        val browser = resolveBrowser()
        if (browser != null) {
            browser.declaredMethods.filter {
                it.parameterTypes.isNotEmpty() && it.parameterTypes[0] == android.content.Context::class.java &&
                    (it.name == "openUrl" || (java.lang.reflect.Modifier.isStatic(it.modifiers) && it.parameterCount >= 4 &&
                        (it.parameterTypes[1] == String::class.java || it.parameterTypes[1] == android.net.Uri::class.java)))
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
    use = false,
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

val TelegramHideReadReceipts = patch(
    name = "Hide read receipts",
    description = "Prevents sending read receipts (double-check ticks) for messages and channels while keeping local badge updates.",
    use = false,
) {
    runCatching {
        val cm = runCatching { classLoader.loadClass("org.telegram.tgnet.ConnectionsManager") }.getOrNull()
        if (cm != null) {
            XposedBridge.hookAllMethods(cm, "sendRequest", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val req = param.args.firstOrNull() ?: return
                    val name = req.javaClass.name
                    if (name.contains("readHistory", ignoreCase = true) ||
                        name.contains("readMessageContents", ignoreCase = true) ||
                        name.contains("readDiscussion", ignoreCase = true) ||
                        name.contains("readEncryptedHistory", ignoreCase = true)
                    ) {
                        param.result = 0
                    }
                }
            })
        }
    }
}

val TelegramHidePhoneNumber = patch(
    name = "Hide phone number",
    description = "Hides and masks your phone number with dots across navigation drawer, settings and profile screens.",
    use = false,
) {
    var myPhone: String? = null
    fun updateMyPhone(u: Any?) {
        if (u != null) {
            val p = runCatching { XposedHelpers.getObjectField(u, "phone") as? String }.getOrNull()
            if (!p.isNullOrEmpty()) {
                myPhone = p
            }
        }
    }

    runCatching {
        val userConfig = resolveUserConfig()
        if (userConfig != null) {
            userConfig.declaredMethods.filter {
                it.parameterCount == 0 && (it.name == "getCurrentUser" || it.name == "f")
            }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        updateMyPhone(param.result)
                    }
                })
            }
        }
    }

    runCatching {
        val tvClass = Class.forName("android.widget.TextView")
        tvClass.declaredMethods.filter {
            it.name == "setText" && it.parameterCount >= 1 && it.parameterTypes[0] == CharSequence::class.java
        }.forEach { m ->
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val cs = param.args[0] as? CharSequence ?: return
                    val str = cs.toString().trim()
                    val digits = str.filter { it.isDigit() }
                    val phone = myPhone?.filter { it.isDigit() }
                    if (phone != null && phone.length >= 6 && digits.contains(phone)) {
                        param.args[0] = "••••••••••••"
                    } else if (str.startsWith("+") && digits.length in 8..15 && digits.length == str.replace(" ", "").removePrefix("+").length) {
                        val tv = param.thisObject as? android.widget.TextView
                        if (tv != null && (tv.maxLines <= 2 || tv.id == android.R.id.text1 || tv.id == android.R.id.text2)) {
                            param.args[0] = "••••••••••••"
                        }
                    }
                }
            })
        }
    }
}

val TelegramOpenExternalLinks = patch(
    name = "Open links in external browser",
    description = "Bypasses the built-in browser and opens all web links directly in your default external browser.",
    use = false,
) {
    runCatching {
        val scClasses = linkedSetOf<Class<*>>()
        resolveSharedConfig()?.let { scClasses.add(it) }
        for (name in listOf("org.telegram.messenger.SharedConfig", "org.telegram.messenger.tz")) {
            runCatching { classLoader.loadClass(name) }.getOrNull()?.let { scClasses.add(it) }
        }
        for (sc in scClasses) {
            sc.declaredMethods.filter {
                (it.returnType == java.lang.Boolean.TYPE || it.returnType == java.lang.Boolean::class.java) &&
                    (it.name.contains("inappBrowser", ignoreCase = true) || it.name.contains("customTabs", ignoreCase = true))
            }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(false))
            }
        }
    }

    runCatching {
        val browser = resolveBrowser()
        if (browser != null) {
            browser.declaredMethods.filter {
                it.parameterTypes.isNotEmpty() && it.parameterTypes[0] == Context::class.java &&
                    (it.name == "openUrl" || (java.lang.reflect.Modifier.isStatic(it.modifiers) && it.parameterCount >= 4 &&
                        (it.parameterTypes[1] == String::class.java || it.parameterTypes[1] == android.net.Uri::class.java)))
            }.forEach { m ->
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val ctx = param.args.firstOrNull() as? Context ?: return
                        var urlStr: String? = null
                        for (arg in param.args) {
                            if (arg is String && (arg.startsWith("http://") || arg.startsWith("https://"))) {
                                urlStr = arg
                                break
                            } else if (arg is android.net.Uri) {
                                urlStr = arg.toString()
                                break
                            }
                        }
                        if (!urlStr.isNullOrEmpty()) {
                            runCatching {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(urlStr))
                                if (ctx !is Activity) {
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                ctx.startActivity(intent)
                                param.result = null
                            }
                        }
                    }
                })
            }
        }
    }
}

val TelegramUseSystemFont = patch(
    name = "Use system font",
    description = "Forces Telegram to use the device's system font instead of bundled Roboto fonts.",
    use = false,
) {
    runCatching {
        val au = resolveAndroidUtilities()
        if (au != null) {
            au.declaredMethods.filter {
                it.returnType == android.graphics.Typeface::class.java &&
                    (it.name == "getTypeface" || (java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                        (it.parameterCount == 0 || (it.parameterCount == 1 && it.parameterTypes[0] == String::class.java))))
            }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(android.graphics.Typeface.DEFAULT))
            }
        }
    }

    runCatching {
        val typefaceClass = android.graphics.Typeface::class.java
        XposedBridge.hookAllMethods(typefaceClass, "createFromAsset", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val path = param.args.getOrNull(1) as? String ?: return
                if (path.contains("fonts/") || path.endsWith(".ttf") || path.endsWith(".otf")) {
                    param.result = android.graphics.Typeface.DEFAULT
                }
            }
        })
    }
}

val TelegramDisableDraftPreviews = patch(
    name = "Disable draft link previews",
    description = "Stops automatic fetching and preview generation of web links while typing drafts in the message input box.",
    use = false,
) {
    runCatching {
        val cm = runCatching { classLoader.loadClass("org.telegram.tgnet.ConnectionsManager") }.getOrNull()
        if (cm != null) {
            XposedBridge.hookAllMethods(cm, "sendRequest", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val req = param.args.firstOrNull() ?: return
                    val name = req.javaClass.name
                    if (name.contains("getWebPagePreview", ignoreCase = true)) {
                        param.result = 0
                    }
                }
            })
        }
    }
}

val TelegramHideSponsoredProxy = patch(
    name = "Hide sponsored proxy channels",
    description = "Hides promotional and sponsored channel banners displayed when using MTProto proxies.",
    use = false,
) {
    runCatching {
        val cm = runCatching { classLoader.loadClass("org.telegram.tgnet.ConnectionsManager") }.getOrNull()
        if (cm != null) {
            XposedBridge.hookAllMethods(cm, "sendRequest", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val req = param.args.firstOrNull() ?: return
                    val name = req.javaClass.name
                    if (name.contains("getPromoData", ignoreCase = true)) {
                        param.result = 0
                    }
                }
            })
        }
    }

    runCatching {
        val mc = resolveMessagesController()
        if (mc != null) {
            val dkMethod = runCatching {
                dexkit.getMethodDirectOrNull("tg_check_promo_data") {
                    findMethod {
                        matcher {
                            declaredClass = mc.name
                            addInvoke { declaredClass = "org.telegram.tgnet.TLRPC\$TL_help_getPromoData" }
                        }
                    }.first()
                }
            }.getOrNull()
            mc.declaredMethods.filter {
                it.name.contains("promo", ignoreCase = true) ||
                    it.name.contains("checkPromoInfo", ignoreCase = true) ||
                    (dkMethod != null && it.name == dkMethod.name)
            }.forEach { m ->
                if (m.returnType == java.lang.Void.TYPE) {
                    XposedBridge.hookMethod(m, XC_MethodReplacement.DO_NOTHING)
                } else {
                    XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(null))
                }
            }
        }
    }
}

val TelegramKeepVideosMuted = patch(
    name = "Keep videos muted",
    description = "Ensures in-chat video messages and media clips play muted by default.",
    use = false,
) {
    runCatching {
        for (name in listOf("org.telegram.messenger.SharedConfig", "org.telegram.messenger.tz")) {
            val sc = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            sc.declaredMethods.filter {
                (it.returnType == java.lang.Boolean.TYPE || it.returnType == java.lang.Boolean::class.java) &&
                    (it.name.contains("soundInChat", ignoreCase = true) || it.name.contains("sound", ignoreCase = true))
            }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(false))
            }
        }
    }
}

val TelegramDisableCameraSwipe = patch(
    name = "Disable in-app camera swipe",
    description = "Prevents accidental activation of the camera swipe gesture in chats.",
    use = false,
) {
    runCatching {
        for (name in listOf("org.telegram.messenger.SharedConfig", "org.telegram.messenger.tz")) {
            val sc = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            sc.declaredMethods.filter {
                (it.returnType == java.lang.Boolean.TYPE || it.returnType == java.lang.Boolean::class.java) &&
                    (it.name.contains("inappCamera", ignoreCase = true) || it.name.contains("camera", ignoreCase = true))
            }.forEach { m ->
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(false))
            }
        }
    }
}

val TelegramPatches = arrayOf(
    TelegramAmoledBlack,
    TelegramRemoveAds,
    TelegramAntiDelete,
    TelegramAntiDisappearingMedia,
    TelegramHideStories,
    TelegramBypassContentRestrictions,
    TelegramBypassChannelRestrictions,
    TelegramDownloadBoost,
    TelegramHideTyping,
    TelegramHideReadReceipts,
    TelegramHidePhoneNumber,
    TelegramOpenExternalLinks,
    TelegramUseSystemFont,
    TelegramDisableDraftPreviews,
    TelegramHideSponsoredProxy,
    TelegramKeepVideosMuted,
    TelegramDisableCameraSwipe,
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
)
