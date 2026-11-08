package io.github.nexfusion.nexfusion.linkedin

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import io.github.nexfusion.IHook
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import java.lang.ref.WeakReference
import java.util.WeakHashMap

// Keep feed on reopen: prevents feed from auto-refreshing when resuming the app.
val KeepFeed = patch(
    name = "Keep feed on reopen",
    description = "Keeps your place in the feed when you return to LinkedIn instead of reloading it.",
) {
    try {
        val prefsClass = classLoader.loadClass("com.linkedin.android.infra.data.FlagshipSharedPreferences")
        val method = prefsClass.declaredMethods.firstOrNull {
            it.name == "getAppLastBackgroundTimeStamp" && it.returnType == Long::class.javaPrimitiveType
        } ?: throw NoSuchMethodException("getAppLastBackgroundTimeStamp")

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val orig = param.result as? Long ?: return
                if (orig <= 0L) return
                val stack = Thread.currentThread().stackTrace
                val isFeed = stack.any { elem ->
                    val name = elem.className
                    name.contains("MainFeedFragment") ||
                        name.contains("MainFeedSessionManager") ||
                        name.contains("GetLongAppBackgroundTimeMillis") ||
                        name.contains("NumericExpressionEvaluator")
                }
                if (isFeed) {
                    param.result = System.currentTimeMillis()
                }
            }
        })
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn KeepFeed hook failed" }
    }
}

// Customize navigation bar & hide bottom tabs
val CustomizeNavigationBar = patch(
    name = "Customize navigation bar",
    description = "Removes selected bottom navigation tabs (My Network, Post, Notifications, Jobs, Video, News) while keeping at least 3 tabs.",
) {
    installNavigationBarFilter()
}

val HideTabLabels = patch(
    name = "Hide navigation bar labels",
    description = "Hides text labels under the bottom navigation icons.",
    use = false,
) {
    installTabArrangementHook(hideLabels = true, hideBadges = false)
}

val HideTabBadges = patch(
    name = "Hide navigation bar badges",
    description = "Hides notification count badges on the bottom navigation bar.",
    use = false,
) {
    installTabArrangementHook(hideLabels = false, hideBadges = true)
}

private fun IHook.installNavigationBarFilter() {
    try {
        val managerClass = classLoader.loadClass("com.linkedin.android.home.HomeNavTabsManager")
        val getTabs = managerClass.declaredMethods.firstOrNull {
            it.name == "getHomeNavTabs" && List::class.java.isAssignableFrom(it.returnType)
        }
        if (getTabs != null) {
            XposedBridge.hookMethod(getTabs, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val tabs = param.result as? List<*> ?: return
                    param.result = filterHomeTabs(tabs)
                }
            })
        }
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn HomeNavTabsManager hook failed" }
    }

    try {
        val barClass = classLoader.loadClass("com.linkedin.android.home.bottomnav.SlimBottomBar")
        val setItems = barClass.declaredMethods.firstOrNull {
            it.name == "setItems" && it.parameterTypes.size == 1 && List::class.java.isAssignableFrom(it.parameterTypes[0])
        }
        if (setItems != null) {
            XposedBridge.hookMethod(setItems, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val tabs = param.args[0] as? List<*> ?: return
                    param.args[0] = filterHomeTabs(tabs)
                }
            })
        }
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn SlimBottomBar.setItems hook failed" }
    }
}

private fun filterHomeTabs(tabs: List<*>): List<*> {
    if (tabs.size <= 3) return tabs
    val filtered = ArrayList<Any?>()
    for (tab in tabs) {
        if (tab == null) continue
        val tabName = getTabIdentifier(tab)
        // Keep HOME always, filter unwanted tabs if needed, but ensure >= 3 tabs remain
        if (tabName == "nav_jobs" || tabName == "JOBS" || tabName == "VIDEO" || tabName == "NEWS") {
            // Unwanted tabs to remove
            continue
        }
        filtered.add(tab)
    }
    return if (filtered.size >= 3) filtered else tabs
}

private fun getTabIdentifier(tab: Any): String {
    return runCatching {
        var cls: Class<*>? = tab.javaClass
        while (cls != null && cls != Any::class.java) {
            try {
                val field = cls.getDeclaredField("trackingControlName")
                field.isAccessible = true
                val value = field.get(tab) as? String
                if (!value.isNullOrEmpty()) return value
            } catch (_: NoSuchFieldException) {}
            cls = cls.superclass
        }
        if (tab is Enum<*>) tab.name else tab.toString()
    }.getOrDefault("")
}

private val preparedBars = WeakHashMap<ViewGroup, Boolean>()

private fun IHook.installTabArrangementHook(hideLabels: Boolean, hideBadges: Boolean) {
    try {
        val barClass = classLoader.loadClass("com.linkedin.android.home.bottomnav.SlimBottomBar")
        val resize = barClass.declaredMethods.firstOrNull {
            it.name == "resizeTabsToCorrectSizes" && it.parameterTypes.isEmpty()
        } ?: return

        XposedBridge.hookMethod(resize, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val bar = param.thisObject as? View ?: return
                val row = findChildById(bar, "bb_slim_bottom_bar_item_container") as? ViewGroup ?: return
                if (preparedBars.put(row, true) == null) {
                    row.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        arrangeTabs(row, hideLabels, hideBadges)
                    }
                }
                arrangeTabs(row, hideLabels, hideBadges)
            }
        })
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn tab arrangement hook failed" }
    }
}

private fun arrangeTabs(tabs: ViewGroup, hideLabels: Boolean, hideBadges: Boolean) {
    val badgeViews = arrayOf(
        "slim_notification_badge_determinate_frame_layout_next",
        "slim_notification_badge_determinate_frame_layout_current",
        "slim_notification_badge_indeterminate_image"
    )
    for (i in 0 until tabs.childCount) {
        val tab = tabs.getChildAt(i) as? ViewGroup ?: continue
        if (hideLabels) {
            val label = findChildById(tab, "slim_notification_badge_label")
            val icon = findChildById(tab, "slim_notification_badge_icon")
            if (label != null && icon != null) {
                label.visibility = View.INVISIBLE
                val offset = (tab.height - icon.height) / 2f - icon.top
                for (j in 0 until tab.childCount) {
                    tab.getChildAt(j).translationY = offset
                }
            }
        }
        if (hideBadges) {
            for (name in badgeViews) {
                val badge = findChildById(tab, name)
                badge?.scaleX = 0f
                badge?.scaleY = 0f
            }
        }
    }
}

private fun findChildById(parent: View, name: String): View? {
    val id = parent.resources.getIdentifier(name, "id", parent.context.packageName)
    return if (id != 0) parent.findViewById(id) else null
}

// Swipe between feeds: Swipe left/right on the home feed to switch between "For you" and "Network".
val SwipeFeeds = patch(
    name = "Swipe between feeds",
    description = "Swipe left and right on the home feed to switch between For you and Network.",
) {
    try {
        val composeViewClass = classLoader.loadClass("androidx.compose.ui.platform.AndroidComposeView")
        val dispatchTouch = composeViewClass.declaredMethods.firstOrNull {
            it.name == "dispatchTouchEvent" && it.parameterTypes.size == 1 && it.parameterTypes[0] == MotionEvent::class.java
        } ?: throw NoSuchMethodException("AndroidComposeView.dispatchTouchEvent")

        val swipeHandler = FeedSwipeGestureHandler()
        XposedBridge.hookMethod(dispatchTouch, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as? View ?: return
                val event = param.args[0] as? MotionEvent ?: return
                swipeHandler.onTouch(view, event)
            }
        })
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn SwipeFeeds hook failed" }
    }
}

private class FeedSwipeGestureHandler {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var multiTouch = false
    private var handledDownTime = -1L
    private var lastSwitch = 0L

    fun onTouch(view: View, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                downTime = event.eventTime
                multiTouch = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
            }
            MotionEvent.ACTION_UP -> {
                if (multiTouch || handledDownTime == downTime) return
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (event.eventTime - downTime > 600L) return
                val rootView = view.rootView ?: return
                if (Math.abs(dx) < rootView.width * 0.25f || Math.abs(dx) < Math.abs(dy) * 2.5f) return
                handledDownTime = downTime
                val targetTab = if (dx < 0) 1 else 0
                if (switchTo(rootView, targetTab)) {
                    event.action = MotionEvent.ACTION_CANCEL
                }
            }
        }
    }

    private fun switchTo(root: View, tabIndex: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastSwitch < 700L) return true
        lastSwitch = now

        val composeViews = ArrayList<View>()
        collectComposeViews(root, composeViews)
        for (cv in composeViews) {
            val provider = cv.accessibilityNodeProvider ?: continue
            val ids = getSemanticsNodeIds(cv) ?: continue
            for (id in ids) {
                val info = provider.createAccessibilityNodeInfo(id) ?: continue
                if (!info.isVisibleToUser) continue
                val text = (info.contentDescription ?: info.text)?.toString() ?: ""
                val isTarget = if (tabIndex == 0) {
                    text.startsWith("For you") || text.startsWith("Grow") || text.contains(", 1 of 2")
                } else {
                    text.startsWith("Network") || text.startsWith("Catch up") || text.contains(", 2 of 2")
                }
                if (isTarget) {
                    val bounds = Rect()
                    info.getBoundsInScreen(bounds)
                    mainHandler.post {
                        simulateTap(root, bounds.exactCenterX(), bounds.exactCenterY())
                    }
                    return true
                }
            }
        }
        return false
    }

    private fun collectComposeViews(view: View, out: MutableList<View>) {
        if (view.javaClass.name.endsWith("AndroidComposeView")) out.add(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectComposeViews(view.getChildAt(i), out)
            }
        }
    }

    private fun simulateTap(root: View, x: Float, y: Float) {
        val origin = IntArray(2)
        root.getLocationOnScreen(origin)
        val relX = x - origin[0]
        val relY = y - origin[1]
        val time = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, relX, relY, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        val up = MotionEvent.obtain(time, time + 40L, MotionEvent.ACTION_UP, relX, relY, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        root.dispatchTouchEvent(down)
        root.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun getSemanticsNodeIds(view: View): IntArray? {
        return runCatching {
            var cls: Class<*>? = view.javaClass
            while (cls != null && cls != Any::class.java) {
                for (f in cls.declaredFields) {
                    if (f.name.contains("node") || f.type.simpleName.contains("IntObjectMap")) {
                        f.isAccessible = true
                        val map = f.get(view) ?: continue
                        val keysField = map.javaClass.declaredFields.firstOrNull { it.name == "keys" } ?: continue
                        keysField.isAccessible = true
                        return keysField.get(map) as? IntArray
                    }
                }
                cls = cls.superclass
            }
            null
        }.getOrNull()
    }
}
