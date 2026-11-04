package io.github.nexfusion.morphe.youtube.layout.buttons.navigation

import android.graphics.Canvas
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import app.morphe.extension.youtube.patches.NavigationBarPatch
import io.github.nexfusion.HookDsl
import io.github.nexfusion.IScopedHookCallback
import io.github.nexfusion.hookMethod
import io.github.nexfusion.morphe.setExtensionIsPatchIncluded
import io.github.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.youtube.insertLiteralOverride
import io.github.nexfusion.morphe.youtube.layout.hide.shorts.ShortsBottomBarContainerFingerprint
import io.github.nexfusion.morphe.youtube.misc.contexthook.Endpoint
import io.github.nexfusion.morphe.youtube.misc.contexthook.addOSNameHook
import io.github.nexfusion.morphe.youtube.misc.contexthook.clientContextHookPatch
import io.github.nexfusion.morphe.youtube.misc.navigation.NavigationBarHook
import io.github.nexfusion.morphe.youtube.misc.navigation.hookNavigationButtonCreated
import io.github.nexfusion.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexfusion.morphe.youtube.misc.playservice.is_20_26_or_greater
import io.github.nexfusion.morphe.youtube.misc.playservice.is_20_31_or_greater
import io.github.nexfusion.morphe.youtube.misc.playservice.is_21_35_or_greater
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexfusion.patch
import io.github.nexfusion.scopedHook
import org.luckypray.dexkit.wrap.DexMethod

val NavigationBar = patch(
    name = "Navigation bar",
    description = "Adds options to hide and change the bottom navigation bar (such as the Shorts button) "
            + "and the upper navigation toolbar.",
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
        )
    )

    if (is_20_31_or_greater) {
        navPreferences += SwitchPreference("morphe_disable_auto_hide_navigation_bar", summary = true)
    }

    PreferenceScreen.GENERAL.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_navigation_bar_screen",
            sorting = Sorting.UNSORTED,
            preferences = navPreferences
        )
    )

    // Swap create with notifications button.
    addOSNameHook(
        Endpoint.GUIDE,
        NavigationBarPatch::swapCreateWithNotificationButton
    )
    setExtensionIsPatchIncluded(NavigationBarPatch::class.java)

    // Hide navigation button labels.
    CreatePivotBarFingerprint.hookMethod(scopedHook(DexMethod("Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V").toMethod()) {
        before { param ->
            NavigationBarPatch.hideNavigationButtonLabels(param.thisObject as TextView)
        }
    })

    // Hook navigation button created, in order to hide them.
    hookNavigationButtonCreated.add { button, view ->
        NavigationBarPatch.navigationTabCreated(button, view)
    }

    // TODO Hide navigation bar

    // The translucent navigation bar comes from three feature flags that not every account
    // gets, so all of them are turned on whenever the window is drawn edge to edge. The bar then
    // looks the same on every account, and the opaque option below paints over the same
    // translucent bar everywhere.
    insertLiteralOverride(45630927L, NavigationBarPatch::useTranslucentNavigationBarBackground)
    insertLiteralOverride(45632194L, NavigationBarPatch::useTranslucentNavigationBar)
    insertLiteralOverride(45631257L, NavigationBarPatch::useFeedBehindNavigationBar)
    insertLiteralOverride(45752241L, NavigationBarPatch::useFixedBottomBarRoom)
    insertLiteralOverride(45724388L, false)

    // Android 12 and 13 can submit the player surface from two threads while drawing the blur.
    // The translucent color is retained, and only the duplicate blurred drawing is disabled.
    if (Build.VERSION.SDK_INT in 31..33) {
        val canvases = listOf(
            Canvas::class.java,
            Class.forName("android.graphics.RecordingCanvas")
        )
        canvases.map { it.getMethod("isHardwareAccelerated") }.distinct().forEach { hardwareAccelerated ->
            NavigationBarBlurDrawFingerprint.hookMethod(scopedHook(hardwareAccelerated) {
                before { it.result = false }
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

fun getPivotBarItemProto(pivotBarItem: Any): app.morphe.extension.youtube.innertube.GuideResponseOuterClass.PivotBarItemBytes {
    val bytes = if (pivotBarItem is ByteArray) {
        pivotBarItem
    } else {
        pivotBarItem.javaClass.getMethod("toByteArray").invoke(pivotBarItem) as ByteArray
    }
    return app.morphe.extension.youtube.innertube.GuideResponseOuterClass.PivotBarItemBytes.parseFrom(bytes)
}

fun getPivotBarItemRenderer(pivotBarItem: Any): app.morphe.extension.youtube.innertube.GuideResponseOuterClass.PivotBarItemRenderer? {
    return runCatching {
        val bytes = if (pivotBarItem is ByteArray) {
            pivotBarItem
        } else {
            pivotBarItem.javaClass.getMethod("toByteArray").invoke(pivotBarItem) as ByteArray
        }
        val item = app.morphe.extension.youtube.innertube.GuideResponseOuterClass.PivotBarItemBytes.parseFrom(bytes)
        if (item.hasPivotBarItemRenderer()) {
            app.morphe.extension.youtube.innertube.GuideResponseOuterClass.PivotBarItemRenderer.parseFrom(item.pivotBarItemRenderer)
        } else {
            app.morphe.extension.youtube.innertube.GuideResponseOuterClass.PivotBarItemRenderer.parseFrom(bytes)
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