package io.github.nexfusion.nexfusion.linkedin

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import io.github.nexfusion.IHook
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch

// Copy text: Long press any post, comment, or Compose text to select and copy it.
val CopyText = patch(
    name = "Copy text",
    description = "Long press a post, comment, or any text to select and copy it in place or in a sheet.",
) {
    try {
        val composeViewClass = classLoader.loadClass("androidx.compose.ui.platform.AndroidComposeView")
        val dispatchTouch = composeViewClass.declaredMethods.firstOrNull {
            it.name == "dispatchTouchEvent" && it.parameterTypes.size == 1 && it.parameterTypes[0] == MotionEvent::class.java
        } ?: throw NoSuchMethodException("AndroidComposeView.dispatchTouchEvent")

        val copyTextHandler = CopyTextGestureHandler()
        XposedBridge.hookMethod(dispatchTouch, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as? View ?: return
                val event = param.args[0] as? MotionEvent ?: return
                if (copyTextHandler.onTouch(view, event)) {
                    param.result = true
                }
            }
        })
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn CopyText hook failed" }
    }
}

internal class CopyTextGestureHandler {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var gestureDownTime = -1L
    private var downRawX = 0f
    private var downRawY = 0f
    private var pendingLongPress: Runnable? = null
    private var swallowGesture = false

    fun onTouch(view: View, event: MotionEvent): Boolean {
        if (swallowGesture) {
            if (event.downTime == gestureDownTime) return true
            swallowGesture = false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.downTime != gestureDownTime) {
                    cancelLongPress()
                    gestureDownTime = event.downTime
                    downRawX = event.rawX
                    downRawY = event.rawY
                    val runnable = Runnable {
                        onLongPress(view, downRawX.toInt(), downRawY.toInt())
                    }
                    pendingLongPress = runnable
                    mainHandler.postDelayed(runnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pendingLongPress != null) {
                    val slop = ViewConfiguration.get(view.context).scaledTouchSlop
                    if (Math.abs(event.rawX - downRawX) > slop || Math.abs(event.rawY - downRawY) > slop) {
                        cancelLongPress()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelLongPress()
            }
        }
        return false
    }

    private fun cancelLongPress() {
        pendingLongPress?.let { mainHandler.removeCallbacks(it) }
        pendingLongPress = null
    }

    private fun onLongPress(view: View, x: Int, y: Int) {
        cancelLongPress()
        try {
            val text = findTextAtCoordinates(view, x, y) ?: return
            if (text.isBlank()) return
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            swallowGesture = true
            showTextSelectionDialog(view.context, text)
        } catch (t: Throwable) {
            PatcherLog.info(t) { "NexFusion: LinkedIn CopyText onLongPress failed" }
        }
    }

    private fun findTextAtCoordinates(view: View, x: Int, y: Int): String? {
        val provider = getAccessibilityProvider(view) ?: return null
        val bounds = Rect()
        val nodeIds = getSemanticsNodeIds(view) ?: intArrayOf(0)

        var bestText: String? = null
        var minArea = Long.MAX_VALUE

        for (id in nodeIds) {
            val info = provider.createAccessibilityNodeInfo(id) ?: continue
            info.getBoundsInScreen(bounds)
            if (bounds.contains(x, y)) {
                val nodeText = info.text?.toString() ?: info.contentDescription?.toString()
                if (!nodeText.isNullOrBlank()) {
                    val area = bounds.width().toLong() * bounds.height().toLong()
                    if (area in 1 until minArea) {
                        minArea = area
                        bestText = nodeText
                    }
                }
            }
        }
        return bestText
    }

    private fun getAccessibilityProvider(view: View): AccessibilityNodeProvider? {
        return view.accessibilityNodeProvider ?: runCatching {
            val delegateField = view.javaClass.declaredFields.firstOrNull {
                it.type.simpleName.contains("AccessibilityDelegate")
            } ?: return@runCatching null
            delegateField.isAccessible = true
            val delegate = delegateField.get(view) ?: return@runCatching null
            val method = delegate.javaClass.getMethod("getAccessibilityNodeProvider", View::class.java)
            method.invoke(delegate, view) as? AccessibilityNodeProvider
        }.getOrNull()
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

    companion object {
        fun showTextSelectionDialog(context: Context, text: String) {
            val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val themeRes = if (isNight) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
            val builder = AlertDialog.Builder(context, themeRes)

            val textView = TextView(builder.context).apply {
                setText(text)
                setTextIsSelectable(true)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setLineSpacing(0f, 1.15f)
                setTextColor(if (isNight) 0xFFE8EAED.toInt() else 0xFF1F1F1F.toInt())
                val pad = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, context.resources.displayMetrics).toInt()
                setPadding(pad, pad / 2, pad, pad / 2)
            }

            val scroll = ScrollView(builder.context).apply {
                addView(textView)
            }

            builder.setTitle("Select text")
                .setView(scroll)
                .setPositiveButton("Copy all") { _, _ ->
                    copyToClipboard(context, text)
                }
                .setNegativeButton("Close", null)
                .show()
        }

        fun copyToClipboard(context: Context, text: String) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            cm.setPrimaryClip(ClipData.newPlainText("LinkedIn", text))
            if (Build.VERSION.SDK_INT < 33) {
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
