package io.github.a092devs.nexfusion.morphe.youtube.layout.buttons.navigation

import android.graphics.Canvas
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import app.morphe.extension.youtube.patches.NavigationBarPatch
import io.github.a092devs.nexfusion.HookDsl
import io.github.a092devs.nexfusion.IScopedHookCallback
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.setExtensionIsPatchIncluded
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.youtube.insertLiteralOverride
import io.github.a092devs.nexfusion.morphe.youtube.layout.hide.shorts.ShortsBottomBarContainerFingerprint
import io.github.a092devs.nexfusion.morphe.youtube.misc.contexthook.Endpoint
import io.github.a092devs.nexfusion.morphe.youtube.misc.contexthook.addOSNameHook
import io.github.a092devs.nexfusion.morphe.youtube.misc.contexthook.clientContextHookPatch
import io.github.a092devs.nexfusion.morphe.youtube.misc.navigation.NavigationBarHook
import io.github.a092devs.nexfusion.morphe.youtube.misc.navigation.hookNavigationButtonCreated
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.VersionCheck
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.is_20_26_or_greater
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.is_20_31_or_greater
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.is_21_30_or_greater
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.is_21_35_or_greater
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.scopedHook
import org.luckypray.dexkit.wrap.DexMethod

val NavigationBar = patch(
    name = "Navigation bar",
    description = "Adds options to configure the bottom navigation bar (including floating Glass navigation bar with custom size/opacity, animated tabs, and hiding buttons) and toolbar.",
) {
    dependsOn(
        NavigationBarHook,
        VersionCheck,
        clientContextHookPatch,
    )

    val navPreferences = mutableSetOf(
        SwitchPreference("morphe_hide_home_button"),
        SwitchPreference("morphe_hide_shorts_button"),
        SwitchPreference("morphe_hide_create_button"),
        SwitchPreference("morphe_hide_subscriptions_button"),
        SwitchPreference("morphe_hide_notifications_button"),
        SwitchPreference("morphe_show_search_button"),
        SwitchPreference("morphe_show_watch_later_button"),
        SwitchPreference("morphe_show_settings_button"),
        SwitchPreference("morphe_show_settings_button_type", summary = true),
        NonInteractivePreference(
            key = "morphe_navigation_buttons_order",
            tag = app.morphe.extension.youtube.settings.preference.NavigationButtonsOrderPreference::class.java,
            selectable = true
        ),
        SwitchPreference("morphe_swap_create_with_notifications_button", summary = true),
        SwitchPreference("morphe_hide_navigation_bar"),
        SwitchPreference("morphe_narrow_navigation_buttons", summary = true),
        SwitchPreference("morphe_hide_navigation_button_labels"),
        SwitchPreference("morphe_disable_icon_only_navigation_buttons", summary = true)
    )

    if (is_21_30_or_greater) {
        navPreferences += SwitchPreference("morphe_you_button_account_switcher", summary = true)
    }

    if (is_21_35_or_greater) {
        navPreferences += SwitchPreference("morphe_disable_toolbar_navigation_tabs", summary = true)
    }

    navPreferences += listOf(
        SwitchPreference("morphe_hide_navigation_new_content_dot"),
        SwitchPreference("morphe_navigation_bar_animations", summary = true),
        ListPreference("morphe_navigation_bar_style"),
        SwitchPreference("morphe_navigation_bar_glass", summary = true),
        NonInteractivePreference(
            key = "morphe_navigation_bar_glass_opacity",
            tag = app.morphe.extension.shared.settings.preference.SeekBarPreference::class.java
        ),
        SwitchPreference("morphe_navigation_bar_glass_round", summary = true),
        SwitchPreference("morphe_navigation_bar_glass_edge", summary = true),
        NonInteractivePreference(
            key = "morphe_navigation_bar_glass_bottom_margin",
            tag = app.morphe.extension.shared.settings.preference.SeekBarPreference::class.java
        ),
        NonInteractivePreference(
            key = "morphe_navigation_bar_glass_height",
            titleKey = "morphe_navigation_bar_glass_size_title",
            summaryKey = "morphe_navigation_bar_glass_size_summary",
            tag = app.morphe.extension.youtube.settings.preference.YouTubeGlassNavigationBarPreference::class.java,
            selectable = true
        )
    )

    if (is_20_31_or_greater) {
        navPreferences += SwitchPreference("morphe_disable_auto_hide_navigation_bar", summary = true)
    }

    val toolbarPreferences = mutableSetOf(
        SwitchPreference("morphe_hide_toolbar_cast_button", titleKey = "morphe_hide_cast_button_title"),
        SwitchPreference("morphe_hide_toolbar_chat_button"),
        SwitchPreference("morphe_hide_toolbar_companion_button"),
        SwitchPreference("morphe_hide_toolbar_create_button", titleKey = "morphe_hide_create_button_title"),
        SwitchPreference("morphe_hide_toolbar_microphone_button"),
        SwitchPreference("morphe_hide_toolbar_notification_button", titleKey = "morphe_hide_notifications_button_title"),
        SwitchPreference("morphe_hide_toolbar_search_button"),
        SwitchPreference("morphe_show_toolbar_settings_button", titleKey = "morphe_show_settings_button_title"),
        SwitchPreference(
            "morphe_show_toolbar_settings_button_type",
            titleKey = "morphe_show_settings_button_type_title",
            summaryKey = "morphe_show_settings_button_type_summary"
        ),
        NonInteractivePreference(
            "morphe_toolbar_buttons_order",
            tag = app.morphe.extension.youtube.settings.preference.NavigationButtonsOrderPreference::class.java,
            selectable = true
        )
    )

    PreferenceScreen.GENERAL.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_navigation_bar_screen",
            sorting = Sorting.UNSORTED,
            preferences = navPreferences
        ),
        PreferenceScreenPreference(
            key = "morphe_toolbar_screen",
            sorting = Sorting.UNSORTED,
            preferences = toolbarPreferences
        )
    )

    // Swap create with notifications button.
    addOSNameHook(Endpoint.GUIDE) { osName ->
        NavigationBarPatch.swapCreateWithNotificationButton(osName)
    }
    setExtensionIsPatchIncluded(NavigationBarPatch::class.java)

    // Hide navigation button labels.
    CreatePivotBarFingerprint.hookMethod(scopedHook(DexMethod("Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V").toMethod()) {
        before { param ->
            NavigationBarPatch.hideNavigationButtonLabels(param.thisObject as TextView)
        }
    })

    // Hook navigation button created, in order to hide them and initialize navigation bar styles.
    hookNavigationButtonCreated.add { button, view ->
        NavigationBarPatch.navigationTabCreated(button, view)
        runCatching {
            val textId = view.resources.getIdentifier("text", "id", view.context.packageName)
            if (textId != 0) {
                view.findViewById<View>(textId)?.visibility = View.GONE
            }
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    if (child is TextView) {
                        child.visibility = View.GONE
                    }
                }
            }
        }
        val container = (view.parent?.parent as? View) ?: (view.parent as? View) ?: view
        NavigationBarPatch.setNavigationBarGlass(container)
        NavigationBarPatch.setNavigationBarOpaque(container)
        NavigationBarPatch.hideNavigationBar(container)
    }

    // Hook PivotBar measurement and container styling (Glass navigation bar, Opaque, and Hide)
    runCatching {
        val pivotBarClass = runCatching {
            classLoader.loadClass("com.google.android.libraries.youtube.rendering.ui.pivotbar.PivotBar")
        }.getOrNull()
        if (pivotBarClass != null) {
            io.github.a092devs.nexfusion.compat.XposedHelpers.findAndHookMethod(
                pivotBarClass,
                "onMeasure",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : io.github.a092devs.nexfusion.compat.XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        param.args[1] = NavigationBarPatch.getNavigationBarHeightMeasureSpec(view, param.args[1] as Int)
                        val container = (view.parent as? View) ?: view
                        NavigationBarPatch.setNavigationBarGlass(container)
                        NavigationBarPatch.setNavigationBarOpaque(container)
                        NavigationBarPatch.hideNavigationBar(container)
                    }
                }
            )

            io.github.a092devs.nexfusion.compat.XposedHelpers.findAndHookMethod(
                pivotBarClass,
                "setTranslationY",
                Float::class.javaPrimitiveType,
                object : io.github.a092devs.nexfusion.compat.XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        param.args[0] = NavigationBarPatch.getNavigationBarTranslationY(view, param.args[0] as Float)
                    }
                }
            )

            val paddingHook = object : io.github.a092devs.nexfusion.compat.XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as View
                    param.args[3] = NavigationBarPatch.getNavigationBarPadding(view, param.args[3] as Int)
                }
            }
            io.github.a092devs.nexfusion.compat.XposedHelpers.findAndHookMethod(
                pivotBarClass,
                "setPadding",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                paddingHook
            )
            io.github.a092devs.nexfusion.compat.XposedHelpers.findAndHookMethod(
                pivotBarClass,
                "setPaddingRelative",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                paddingHook
            )
        }
    }

    // Build PivotBar items (Search, Settings, Watch Later)
    runCatching {
        val factoryMethod = PivotBarRendererFingerprint.method
        val protoParamType = factoryMethod.parameterTypes[0]
        val defaultInstanceField = protoParamType.declaredFields.firstOrNull {
            java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == protoParamType
        } ?: protoParamType.getDeclaredField("a")
        defaultInstanceField.isAccessible = true
        val defaultInstance = defaultInstanceField.get(null)

        val parseMethod = protoParamType.superclass.methods.firstOrNull {
            it.name == "parseFrom" &&
                    java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    it.parameterTypes.size == 3 &&
                    it.parameterTypes[0].isAssignableFrom(protoParamType) &&
                    it.parameterTypes[1] == ByteArray::class.java
        } ?: protoParamType.superclass.methods.firstOrNull {
            it.name == "parseFrom" &&
                    java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    it.parameterTypes.size == 2 &&
                    it.parameterTypes[0].isAssignableFrom(protoParamType) &&
                    it.parameterTypes[1] == ByteArray::class.java
        }

        val registry = runCatching {
            val registryClass = classLoader.loadClass("com.google.protobuf.ExtensionRegistryLite")
            val registryMethod = registryClass.getDeclaredMethod("getGeneratedRegistry")
            registryMethod.isAccessible = true
            registryMethod.invoke(null)
        }.getOrNull()

        io.github.a092devs.nexfusion.compat.XposedHelpers.findAndHookMethod(
            NavigationBarPatch::class.java,
            "buildPivotBarItem",
            ByteArray::class.java,
            object : io.github.a092devs.nexfusion.compat.XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    val bytes = param.args[0] as? ByteArray ?: return null
                    val proto = runCatching {
                        if (parseMethod != null) {
                            if (parseMethod.parameterTypes.size == 3 && registry != null) {
                                parseMethod.invoke(null, defaultInstance, bytes, registry)
                            } else {
                                parseMethod.invoke(null, defaultInstance, bytes)
                            }
                        } else null
                    }.onFailure { android.util.Log.e("NexFusionNav", "Error parsing proto in buildPivotBarItem", it) }.getOrNull() ?: return null
                    val optional = runCatching { factoryMethod.invoke(null, proto) }.onFailure { android.util.Log.e("NexFusionNav", "Error factoryMethod in buildPivotBarItem", it) }.getOrNull() ?: return null
                    val item = runCatching {
                        optional.javaClass.getMethod("orElse", Any::class.java).invoke(optional, null)
                    }.onFailure { android.util.Log.e("NexFusionNav", "Error orElse in buildPivotBarItem", it) }.getOrNull()
                    android.util.Log.i("NexFusionNav", "buildPivotBarItem built item: $item (${item?.javaClass})")
                    return item
                }
            }
        )
    }

    // Initialize custom pivot bar buttons
    runCatching {
        PivotBarRendererFingerprint.hookMethod {
            before { param ->
                val obj = param.args[0] ?: return@before
                android.util.Log.i("NexFusionNav", "PivotBarRendererFingerprint called with obj: ${obj.javaClass}")
                NavigationBarPatch.createPivotBarItems(obj)
            }
        }
    }
    runCatching {
        PivotBarIconOnlyRendererFingerprint.hookMethod {
            before { param ->
                val obj = param.args[0] ?: return@before
                NavigationBarPatch.createIconOnlyPivotBarItems(obj)
            }
        }
    }

    // Apply Navigation Bar buttons ordering and custom items list
    runCatching {
        val listBuilderMethod = ::pivotBarRendererListBuilderMethod.method
        val returnType = listBuilderMethod.returnType
        val copyOfMethod = returnType.declaredMethods.firstOrNull {
            java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    it.returnType == returnType &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == java.util.Collection::class.java
        } ?: returnType.methods.firstOrNull {
            java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    it.returnType == returnType &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == java.util.Collection::class.java
        } ?: returnType.declaredMethods.firstOrNull {
            java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    returnType.isAssignableFrom(it.returnType) &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0].isAssignableFrom(java.util.Collection::class.java)
        } ?: returnType.methods.firstOrNull {
            java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    returnType.isAssignableFrom(it.returnType) &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0].isAssignableFrom(java.util.Collection::class.java)
        }
        android.util.Log.i("NexFusionNav", "Found copyOfMethod: $copyOfMethod on returnType: $returnType")

        listBuilderMethod.hookMethod {
            after { param ->
                runCatching {
                    val currentList = param.result as? List<Any> ?: return@after
                    val modified = NavigationBarPatch.getPivotBarRendererList(currentList) ?: return@after
                    if (copyOfMethod != null) {
                        copyOfMethod.isAccessible = true
                        val res = copyOfMethod.invoke(null, modified)
                        android.util.Log.i("NexFusionNav", "Invoked copyOfMethod: res=$res (${res?.javaClass})")
                        if (res != null && returnType.isInstance(res)) {
                            param.result = res
                        }
                    } else if (returnType.isInstance(modified)) {
                        param.result = modified
                    }
                }.onFailure {
                    android.util.Log.e("NexFusionNav", "Error in pivotBarRendererList hook", it)
                }
            }
        }
    }

    // Clip Live Chat scroll button
    runCatching {
        MoreCommentsIconFingerprint.hookAllMethods {
            after { param ->
                (param.result as? View)?.let { view ->
                    NavigationBarPatch.clipLiveChatScrollButton(view)
                }
            }
        }
    }

    // The translucent navigation bar comes from three feature flags that not every account
    // gets, so all of them are turned on whenever the window is drawn edge to edge. The bar then
    // looks the same on every account, and the opaque option below paints over the same
    // translucent bar everywhere.
    insertLiteralOverride(45630927L, NavigationBarPatch::useTranslucentNavigationBarBackground)
    insertLiteralOverride(45632194L, NavigationBarPatch::useTranslucentNavigationBar)
    insertLiteralOverride(45631257L, NavigationBarPatch::useFeedBehindNavigationBar)
    insertLiteralOverride(45633329L, NavigationBarPatch::useTranslucentNavigationBarStyle)
    insertLiteralOverride(45752241L, NavigationBarPatch::useFixedBottomBarRoom)
    insertLiteralOverride(45724388L, false)

    runCatching {
        val canvases = listOfNotNull(
            Canvas::class.java,
            runCatching { Class.forName("android.graphics.RecordingCanvas") }.getOrNull()
        )
        canvases.map { it.getMethod("isHardwareAccelerated") }.distinct().forEach { hardwareAccelerated ->
            NavigationBarBlurDrawFingerprint.hookMethod(scopedHook(hardwareAccelerated) {
                after {
                    it.result = NavigationBarPatch.useNavigationBarBlur((it.result as? Boolean) ?: false)
                }
            })
        }
    }

    // With the translucent navigation bar, the navigation bar makes room for the system
    // navigation buttons. If the navigation bar is hidden in the Shorts player, nothing makes
    // that room, so the Shorts player makes it itself. The first parameter is the layout
    // of the Shorts player fragment.
    ShortsBottomBarContainerFingerprint.hookMethod {
        before { NavigationBarPatch.setShortsPlayerBottomInset(it.args[0] as View) }
    }
    // Replace the height at the constructor call, after other hooks may have changed getHeight().
    val reserveRoom = ::shortsBottomRoomConstructor.constructor
    val heightParameter = reserveRoom.parameterTypes.toList().zipWithNext().indexOfFirst { (view, height) ->
        view == View::class.java && height == Int::class.javaPrimitiveType
    } + 1
    ShortsBottomBarContainerFingerprint.hookMethod(scopedHook(reserveRoom) {
        before {
            it.args[heightParameter] = NavigationBarPatch.getShortsPlayerBottomRoom(it.args[heightParameter] as Int)
        }
    })

    // With the keyboard open, the bottom inset also holds the keyboard, so the bar covering
    // the system navigation buttons grows as tall as the keyboard and covers the page.
    // The padding is first stored in a field of the bar, and the same register is then passed
    // to setPadding. Some targets set it in two places.
    val paddingField = ::navigationBarBottomPaddingField.field
    val updateHeight: HookDsl<IScopedHookCallback>.() -> Unit = {
        before {
            if (innerDepth == 0 && paddingField.declaringClass.isInstance(it.thisObject)) {
                val view = it.thisObject as View
                paddingField.setInt(view, NavigationBarPatch.getNavigationBarBottomPadding(view, paddingField.getInt(view)))
            }
        }
    }
    val updatePadding: HookDsl<IScopedHookCallback>.() -> Unit = {
        before {
            if (innerDepth == 0 && paddingField.declaringClass.isInstance(it.thisObject)) {
                it.args[3] = NavigationBarPatch.getNavigationBarBottomPadding(it.thisObject as View, it.args[3] as Int)
            }
        }
    }
    val setPadding = paddingField.declaringClass.getMethod(
        "setPadding", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
    )
    val paddingHooks = (::navigationBarRelayoutMethods.dexMethodList.map {
        it.toMethod() to updateHeight
    } + (setPadding to updatePadding)).toTypedArray()
    ::navigationBarBottomPaddingMethods.dexMethodList.forEach {
        it.toMethod().hookMethod(scopedHook(*paddingHooks))
    }

    // The keyboard branch reads its insets before constructing the bottom-margin update.
    // Replace that margin where it is consumed, while leaving the other margins unchanged.
    runCatching {
        PageBottomMarginFingerprint.hookMethod(scopedHook(::pageKeyboardInsetsMethod.method) {
            before { outerParam.setObjectExtra("navigationKeyboardMargin", true) }
        })
        PageBottomMarginFingerprint.hookMethod(scopedHook(::pageBottomMarginConstructor.constructor) {
            before {
                if (it.args[1] == 1 && outerParam.getObjectExtra("navigationKeyboardMargin") == true) {
                    it.args[0] = 0
                }
            }
        })
    }
    runCatching {
        PageBottomMarginLegacyFingerprint.hookMethod(scopedHook(::pageKeyboardInsetsMethod.method) {
            before { outerParam.setObjectExtra("navigationKeyboardMargin", true) }
        })
        PageBottomMarginLegacyFingerprint.hookMethod(scopedHook(::pageBottomMarginConstructor.constructor) {
            before {
                if (it.args[1] == 1 && outerParam.getObjectExtra("navigationKeyboardMargin") == true) {
                    it.args[0] = 0
                }
            }
        })
    }

    // A feed behind the navigation bar includes the keyboard in its bottom padding.
    if (is_20_26_or_greater && Build.VERSION.SDK_INT >= 30) {
        val setFeedPadding = View::class.java.getMethod(
            "setPadding", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        )
        FeedListPaddingFingerprint.hookMethod(scopedHook(setFeedPadding) {
            before {
                if (innerDepth != 0) return@before
                val insets = (it.thisObject as View).rootWindowInsets ?: return@before
                val keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom
                val systemBars = insets.getInsets(WindowInsets.Type.systemBars()).bottom
                val bottom = it.args[3] as Int
                if (bottom >= keyboard && keyboard > systemBars) {
                    it.args[3] = bottom - keyboard + systemBars
                }
            }
        })
    }

    // TODO Paint over the translucent status bar and navigation bar

    // Animated navigation tabs.
    insertLiteralOverride(45680008L, NavigationBarPatch::useAnimatedNavigationButtons)

    // TODO Narrow navigation buttons

    // disableAutoHidingNavigationBar
    if (is_20_31_or_greater) {
        listOf(
            AutoHideNavigationBarOnFeedScrollingFingerprint,
            AutoHideNavigationBarOnDismissMiniplayerFingerprint,
        ).forEach {
            it.hookMethod {
                before { param ->
                    if (NavigationBarPatch.disableAutoHidingNavigationBar()) {
                        param.result = null
                    }
                }
            }
        }
    }
}

fun getPivotBarItemProto(pivotBarItem: Any): app.morphe.extension.shared.innertube.GuideResponseOuterClass.PivotBarItemBytes {
    val bytes = if (pivotBarItem is ByteArray) {
        pivotBarItem
    } else {
        val protoObj = runCatching {
            val field = pivotBarItem.javaClass.declaredFields.firstOrNull {
                !java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                        it.type.name != "boolean" &&
                        !it.type.name.startsWith("j$.util.Optional") &&
                        !it.type.name.startsWith("java.lang")
            } ?: pivotBarItem.javaClass.getDeclaredField("a")
            field.isAccessible = true
            field.get(pivotBarItem)
        }.getOrNull() ?: pivotBarItem
        protoObj.javaClass.getMethod("toByteArray").invoke(protoObj) as ByteArray
    }
    return app.morphe.extension.shared.innertube.GuideResponseOuterClass.PivotBarItemBytes.parseFrom(bytes)
}

fun getPivotBarItemRenderer(pivotBarItem: Any): app.morphe.extension.shared.innertube.GuideResponseOuterClass.PivotBarItemRenderer? {
    return runCatching {
        val item = getPivotBarItemProto(pivotBarItem)
        if (item.hasPivotBarItemRenderer()) {
            app.morphe.extension.shared.innertube.GuideResponseOuterClass.PivotBarItemRenderer.parseFrom(item.pivotBarItemRenderer)
        } else if (item.hasPivotBarIconOnlyItemRenderer()) {
            val iconOnly = app.morphe.extension.shared.innertube.GuideResponseOuterClass.PivotBarIconOnlyItemRendererBytes.parseFrom(item.pivotBarIconOnlyItemRenderer)
            app.morphe.extension.shared.innertube.GuideResponseOuterClass.PivotBarItemRenderer.newBuilder()
                .setPivotIdentifier(iconOnly.pivotIdentifier)
                .setAccessibility(
                    app.morphe.extension.shared.innertube.GuideResponseOuterClass.Accessibility.newBuilder()
                        .setAccessibilityData(
                            app.morphe.extension.shared.innertube.GuideResponseOuterClass.AccessibilityData.newBuilder()
                                .setLabel(iconOnly.accessibility.runs.text)
                        )
                )
                .build()
        } else {
            val bytes = if (pivotBarItem is ByteArray) pivotBarItem else {
                val protoObj = runCatching {
                    pivotBarItem.javaClass.getDeclaredField("a").apply { isAccessible = true }.get(pivotBarItem)
                }.getOrNull() ?: pivotBarItem
                protoObj.javaClass.getMethod("toByteArray").invoke(protoObj) as ByteArray
            }
            app.morphe.extension.shared.innertube.GuideResponseOuterClass.PivotBarItemRenderer.parseFrom(bytes)
        }
    }.getOrNull()
}

fun isPivotBar(view: View?): Boolean {
    return view != null && view.javaClass.name.endsWith("PivotBar")
}

fun setNavigationBarSystemButtonsRoom(navigationBar: View?, room: Int) {
    if (navigationBar == null) return
    runCatching {
        val cls = navigationBar.javaClass
        val m = cls.methods.firstOrNull { it.name == "patch_setSystemButtonsRoom" }
        if (m != null) {
            m.invoke(navigationBar, room)
        } else {
            cls.declaredFields.firstOrNull { it.type == Int::class.javaPrimitiveType }?.let { f ->
                f.isAccessible = true
                f.setInt(navigationBar, room)
            }
        }
    }
}
