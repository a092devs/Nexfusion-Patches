package io.github.a092devs.nexfusion.morphe.youtube.misc.navigation

import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import app.morphe.extension.shared.ResourceType
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.shared.Utils
import app.morphe.extension.youtube.patches.VersionCheckPatch
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.is_21_43_or_greater
import app.morphe.extension.youtube.shared.NavigationBar
import io.github.a092devs.nexfusion.createProxy
import io.github.a092devs.nexfusion.enumValueOf
import io.github.a092devs.nexfusion.morphe.youtube.misc.backgesture.addBackPressedHook
import io.github.a092devs.nexfusion.morphe.youtube.misc.backgesture.addPredictiveBackGestureHook
import io.github.a092devs.nexfusion.morphe.youtube.misc.backgesture.backGesturePatch
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.scopedHook
import org.luckypray.dexkit.wrap.DexMethod

fun onNavigationTabCreated(button: NavigationBar.NavigationButton, tabView: View) {
    hookNavigationButtonCreated.forEach { it(button, tabView) }
}

val hookNavigationButtonCreated: MutableList<(NavigationBar.NavigationButton, View) -> Unit> =
    mutableListOf()

val NavigationBarHook = patch(
    description = "Hooks the active navigation or search bar.",
) {
    dependsOn(backGesturePatch)

    // Hook the current navigation bar enum value. Note, the 'You' tab does not have an enum value.
    ::initializeButtonsFingerprint.hookMethod(scopedHook(::getNavigationEnumMethod.member) {
        after { NavigationBar.setLastAppNavigationEnum(it.result as Enum<*>) }
    })

    // Hook the creation of navigation tab views.
    if (!is_21_43_or_greater) {
        ::initializeButtonsFingerprint.hookMethod(scopedHook(::pivotBarButtonsCreateDrawableViewFingerprint.member) {
            after { NavigationBar.navigationTabLoaded(it.result as View) }
        })
    }

//    if (!is_20_28_or_greater) {
//        // TODO
//    }

    ::initializeButtonsFingerprint.hookMethod(
        scopedHook(
            PivotBarButtonsCreateResourceStyledViewFingerprint.member
        ) {
            after {
                // The 'You' tab has no YT enum name; upstream detects it inside
                // navigationTabLoaded by comparing the last loaded enum.
                NavigationBar.navigationImageResourceTabLoaded(it.result as View)
            }
        })

    val selectedTab = ThreadLocal<View>()
    ::pivotBarButtonsViewSetSelectedFingerprint.hookMethod {
        before { selectedTab.remove() }
        after { selectedTab.get()?.let { NavigationBar.navigationTabSelected(it, true) } }
    }

    ::pivotBarButtonsViewSetSelectedFingerprint.hookMethod(scopedHook(::pivotBarButtonsViewSetSelectedSubFingerprint.member) {
        after {
            // `setSelect` dispatch to subviews, we need to wait for the root view.
            val isSelected = it.args[0] as Boolean
            if (isSelected) {
                selectedTab.set(it.thisObject as View)
            }
        }
    })

    // Hook onto navigation bar touches. Needed to detect a tap on the selected
    // navigation button closes the search, as the button is not selected again.
    // Also delegates to NavigationBarPatch to handle long-press on Settings / Account switcher.
    PivotBarDispatchTouchEventFingerprint.hookMethod {
        before {
            val event = it.args[0] as? MotionEvent
            if (event != null) {
                runCatching {
                    app.morphe.extension.youtube.patches.NavigationBarPatch.navigationBarTouched(event)
                }
            }
            NavigationBar.navigationBarTouched(event)
        }
    }

    // Hook onto back button pressed.  Needed to fix race problem with
    // Litho filtering based on navigation tab before the tab is updated.
    addBackPressedHook { NavigationBar.onBackPressed() }
    addPredictiveBackGestureHook { NavigationBar.onBackInvoked() }

    // Hook the search bar.
    DexMethod("Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;)Landroid/view/View;").hookMethod {
        after {
            val layout = Utils.getContext().resources.getResourceName(it.args[0] as Int)
            if (layout.contains("/action_bar_search_results_view_")) {
                NavigationBar.searchBarResultsViewLoaded(it.result as View)
            }
        }
    }

    // Hook the back button visibility.
    ToolbarLayoutFingerprint.hookMethod(scopedHook(DexMethod("Landroid/view/ViewGroup;->findViewById(I)Landroid/view/View;").toMember()) {
        val appCompatToolbarClass =
            classLoader.loadClass(AppCompatToolbarBackButtonFingerprint.dexMethod.className)
        val getNavigationIcon = AppCompatToolbarBackButtonFingerprint.method
        val toolbarContainerId = toolbarContainerId
        after {
            if (it.args[0] != toolbarContainerId) return@after
            val layout = it.result as FrameLayout
            val toolbar = Utils.getChildView<View>(
                layout, false
            ) { it: View -> appCompatToolbarClass.isAssignableFrom(it.javaClass) } ?: return@after
            // The obfuscated toolbar does not implement the extension's patch interface (upstream
            // injects it during bytecode patching), so hand the extension's toolbar reference
            // field a forwarding proxy of the interface directly.
            val proxy = toolbar.createProxy { impl ->
                NavigationBar.AppCompatToolbarPatchInterface {
                    getNavigationIcon(impl.get()) as Drawable?
                }
            }
            NavigationBar::class.java.getDeclaredField("toolbarResultsRef")
                .apply { isAccessible = true }
                .set(null, java.lang.ref.WeakReference(proxy))
        }
    })

    // Hook bottom bar container layout listener
    val handledContainers = java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())
    var isHandling = false
    runCatching {
        InitializeBottomBarContainerFingerprint.hookMethod(scopedHook(
            DexMethod("Landroid/view/View;->addOnLayoutChangeListener(Landroid/view/View\$OnLayoutChangeListener;)V").toMethod()
        ) {
            before {
                if (isHandling) return@before
                val container = it.thisObject as? View ?: return@before
                if (!handledContainers.add(container)) return@before
                isHandling = true
                try {
                    app.morphe.extension.youtube.patches.NavigationBarPatch.setNavigationBarGlass(container)
                    app.morphe.extension.youtube.patches.NavigationBarPatch.setNavigationBarOpaque(container)
                    app.morphe.extension.youtube.patches.NavigationBarPatch.hideNavigationBar(container)
                    app.morphe.extension.youtube.patches.NavigationBarPatch.watchSlimStatusBar(container)
                } finally {
                    isHandling = false
                }
            }
        })
    }

    // Fix YT bug of notification tab missing the filled icon.
    val tabActivityCairo = ::navigationEnumClass.clazz.enumValueOf("TAB_ACTIVITY_CAIRO")
    if (tabActivityCairo != null) {
        runCatching {
            ::getNavIconResIdFingerprint.dexMethodList.forEach { dexMethod ->
                val method = runCatching { dexMethod.toMember() as? java.lang.reflect.Method }.getOrNull()
                if (method != null && java.lang.reflect.Modifier.isAbstract(method.modifiers)) {
                    return@forEach
                }
                runCatching {
                    dexMethod.hookMethod {
                        val fillBellCairoBlack = ResourceUtils.getIdentifier(
                            ResourceType.DRAWABLE,
                            if (VersionCheckPatch.IS_20_31_OR_GREATER)
                                "yt_fill_experimental_bell_vd_theme_24"
                            else
                                "morphe_fill_bell_cairo_black_24"
                        )
                        after {
                            val navEnum = it.args[0] as Enum<*>
                            val selected = it.args[1] as Boolean
                            if (navEnum == tabActivityCairo && selected)
                                it.result = fillBellCairoBlack
                        }
                    }
                }
            }
        }
    }
}