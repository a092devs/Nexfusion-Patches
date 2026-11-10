package io.github.nexfusion.nexfusion.gboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XposedBridge
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap

/**
 * Ported from jasonwu1994/Gboard-patches (GPL-3.0) GboardSpacebarLogo:
 *
 * Hooks Gboard's SoftKeyView binding to render a clean Google "G" logo or icon
 * on the spacebar, with automatic restoration when unbinding or disabled.
 */
object GboardSpacebarLogoHook {

    private const val TAG = "NexFusion"
    private const val OVERLAY_TAG = "nexfusion:spacebar-logo"
    private const val KEYCODE_SPACE = 62

    // Known Gboard resource identifiers
    private const val ID_SPACEBAR = 0x7f0b05b5
    private const val ID_PRIMARY_LABEL = 0x7f0b05f5
    private const val ID_DRAWABLE_GOOGLE_G = 0x7f080467

    private val originalLabelVisibilities = WeakHashMap<View, Int>()

    fun install(classLoader: ClassLoader) {
        val softKeyViewClass = runCatching {
            classLoader.loadClass("com.google.android.libraries.inputmethod.widgets.SoftKeyView")
        }.getOrNull() ?: return

        // Hook all methods on SoftKeyView that bind or update keys
        runCatching {
            val bindMethods = softKeyViewClass.declaredMethods.filter {
                it.parameterCount in 1..3 && (it.name == "a" || it.name == "b" || it.name == "j" || it.name == "k")
            }

            for (method in bindMethods) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? View ?: return
                        if (isSpacebarView(view, param.args)) {
                            applySpacebarLogo(view)
                        }
                    }
                })
            }
            Log.i(TAG, "NexFusion: Hooked Gboard spacebar logo")
        }
    }

    private fun isSpacebarView(view: View, args: Array<Any?>): Boolean {
        // Direct ID check
        if (view.id == ID_SPACEBAR) return true

        // Content description check
        val desc = view.contentDescription?.toString()?.lowercase() ?: ""
        if (desc.contains("space") || desc.contains("spacebar") || desc.contains("空白")) return true

        // Metadata check
        for (arg in args) {
            if (arg != null && isSpaceMetadata(arg)) {
                return true
            }
        }
        return false
    }

    private fun isSpaceMetadata(metadata: Any): Boolean {
        return runCatching {
            val clazz = metadata.javaClass
            // SoftKeyDef or similar metadata containing keycode 62
            for (field in clazz.declaredFields) {
                if (field.type == Int::class.javaPrimitiveType) {
                    field.isAccessible = true
                    if (field.getInt(metadata) == KEYCODE_SPACE) return true
                }
            }
            false
        }.getOrDefault(false)
    }

    internal fun applySpacebarLogo(softKeyView: View) {
        val root = softKeyView as? ViewGroup ?: return
        try {
            val context = root.context
            val labelView = root.findViewById<View>(ID_PRIMARY_LABEL)
                ?: findTextViewChild(root)

            if (labelView != null) {
                if (!originalLabelVisibilities.containsKey(labelView)) {
                    originalLabelVisibilities[labelView] = labelView.visibility
                }
                labelView.visibility = View.INVISIBLE
            }

            // Retrieve Google G drawable or fallback icon
            val drawable = runCatching {
                context.getDrawable(ID_DRAWABLE_GOOGLE_G)
            }.getOrNull()

            val existingOverlay = root.findViewWithTag<ImageView>(OVERLAY_TAG)
            if (existingOverlay != null) {
                if (drawable != null) existingOverlay.setImageDrawable(drawable)
                existingOverlay.visibility = View.VISIBLE
                return
            }

            if (drawable != null) {
                val overlay = ImageView(context).apply {
                    tag = OVERLAY_TAG
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    isClickable = false
                    isFocusable = false
                    setImageDrawable(drawable)
                }
                val sizePx = (24 * context.resources.displayMetrics.density).toInt()
                val lp = FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER)
                root.addView(overlay, lp)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to apply spacebar logo", t)
        }
    }

    private fun findTextViewChild(parent: ViewGroup): View? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is android.widget.TextView) return child
            if (child is ViewGroup) {
                val found = findTextViewChild(child)
                if (found != null) return found
            }
        }
        return null
    }
}

