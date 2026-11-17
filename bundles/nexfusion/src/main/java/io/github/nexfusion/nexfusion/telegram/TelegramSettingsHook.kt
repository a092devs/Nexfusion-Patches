package io.github.nexfusion.nexfusion.telegram

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XposedHelpers
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

private const val TAG = "NexFusion"
private const val SHORTCUT_ID = "nexfusion_telegram_settings"
private const val EXTRA_OPEN_SETTINGS = "open_nexfusion_settings"
private const val SETTINGS_ROW_TAG = "nexfusion_settings_row"

object TelegramSettingsHook {

    private var installed = false
    private val injectedContainers = Collections.newSetFromMap(WeakHashMap<ViewGroup, Boolean>())
    private var lastResumedActivity: WeakReference<Activity>? = null

    fun install(classLoader: ClassLoader) {
        if (installed) return
        installed = true

        val launchActivityClass = runCatching {
            classLoader.loadClass("org.telegram.ui.LaunchActivity")
        }.getOrNull()

        if (launchActivityClass != null) {
            // 1. Hook LaunchActivity.onCreate to register dynamic launcher shortcut
            runCatching {
                XposedHelpers.findAndHookMethod(
                    launchActivityClass,
                    "onCreate",
                    Bundle::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val activity = param.thisObject as? Activity ?: return
                            TelegramPreferences.init(activity)
                            setupLauncherShortcut(activity)
                            checkIntent(activity, activity.intent)
                        }
                    }
                )
            }

            // 2. Hook LaunchActivity.onNewIntent
            runCatching {
                XposedHelpers.findAndHookMethod(
                    launchActivityClass,
                    "onNewIntent",
                    Intent::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val activity = param.thisObject as? Activity ?: return
                            val intent = param.args.firstOrNull() as? Intent ?: return
                            checkIntent(activity, intent)
                        }
                    }
                )
            }

            // 3. Hook LaunchActivity.onResume to scan views for title / tabs / version
            runCatching {
                XposedHelpers.findAndHookMethod(
                    launchActivityClass,
                    "onResume",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val activity = param.thisObject as? Activity ?: return
                            TelegramPreferences.init(activity)
                            lastResumedActivity = WeakReference(activity)
                            checkIntent(activity, activity.intent)
                            attachTitleAndTabListeners(activity)
                        }
                    }
                )
            }
        }

        // 4. Hook ViewGroup.addView to inject the native row into Telegram's Settings list
        runCatching {
            val addViewMethods = ViewGroup::class.java.declaredMethods.filter {
                it.name == "addView" && it.parameterTypes.isNotEmpty() && View::class.java.isAssignableFrom(it.parameterTypes[0])
            }
            val hook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val container = param.thisObject as? ViewGroup ?: return
                    val child = param.args.firstOrNull() as? View ?: return
                    inspectAndInjectSettingsRow(container, child)
                }
            }
            for (m in addViewMethods) {
                runCatching { XposedHelpers.findAndHookMethod(ViewGroup::class.java, m.name, *m.parameterTypes, hook) }
            }
        }
    }

    private fun checkIntent(activity: Activity, intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false) ||
            intent.action == "android.intent.action.APPLICATION_PREFERENCES"
        ) {
            intent.removeExtra(EXTRA_OPEN_SETTINGS)
            activity.window.decorView.post {
                TelegramSettingsDialog.show(activity)
            }
        }
    }

    private fun setupLauncherShortcut(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            runCatching {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
                val intent = Intent(context, context.javaClass).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(EXTRA_OPEN_SETTINGS, true)
                }
                val iconBitmap = createShortcutIcon()
                val shortcut = ShortcutInfo.Builder(context, SHORTCUT_ID)
                    .setShortLabel("NexFusion Settings")
                    .setLongLabel("NexFusion Telegram Settings")
                    .setIcon(Icon.createWithBitmap(iconBitmap))
                    .setIntent(intent)
                    .build()
                shortcutManager.dynamicShortcuts = listOf(shortcut)
            }
        }
    }

    private fun createShortcutIcon(): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2481CC.toInt() }
        canvas.drawCircle(size / 2f, size / 2f, size * 0.45f, disc)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = size * 0.42f
        }
        val metrics = textPaint.fontMetrics
        canvas.drawText("N", size / 2f, size / 2f - (metrics.ascent + metrics.descent) / 2f, textPaint)
        return bitmap
    }

    private fun attachTitleAndTabListeners(activity: Activity) {
        val decor = activity.window?.decorView ?: return
        decor.postDelayed({
            scanViewsRecursively(decor, activity)
        }, 300)
    }

    private fun scanViewsRecursively(view: View, activity: Activity) {
        if (view is TextView) {
            val text = view.text?.toString() ?: ""
            // Long-press on "Telegram" chat list header title opens settings
            if (text == "Telegram" && view.isClickable) {
                view.setOnLongClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    TelegramSettingsDialog.show(activity)
                    true
                }
            }
            // Long-press on "Settings" tab in bottom navigation opens settings
            if (text == "Settings" && view.parent is ViewGroup) {
                val tab = view.parent as ViewGroup
                tab.isLongClickable = true
                tab.setOnLongClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    TelegramSettingsDialog.show(activity)
                    true
                }
            }
            // Tap or long-press on Telegram version text at bottom of Settings screen
            if (text.startsWith("Telegram for Android") || text.contains("Telegram for Android v")) {
                view.isClickable = true
                view.setOnClickListener {
                    TelegramSettingsDialog.show(activity)
                }
                view.setOnLongClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    TelegramSettingsDialog.show(activity)
                    true
                }
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                scanViewsRecursively(view.getChildAt(i), activity)
            }
        }
    }

    private fun inspectAndInjectSettingsRow(container: ViewGroup, child: View) {
        if (child.tag == SETTINGS_ROW_TAG) return
        if (injectedContainers.contains(container)) return

        // Check if the container is a RecyclerView or list in Telegram Settings
        if (!container.javaClass.name.contains("Recycler") && !container.javaClass.name.contains("ListView")) return

        val text = findTextRecursively(child)
        // Detect Settings screen anchor row: "Chat Settings" or "Privacy & Security"
        if (text == "Chat Settings" || text == "Privacy & Security") {
            injectedContainers.add(container)
            container.post {
                runCatching {
                    val activity = findActivity(container) ?: return@runCatching
                    if (container.findViewWithTag<View>(SETTINGS_ROW_TAG) != null) return@runCatching

                    val customRow = buildNativeSettingsRow(activity)
                    customRow.tag = SETTINGS_ROW_TAG

                    // Find index of the anchor row and insert right below it
                    var targetIndex = -1
                    for (i in 0 until container.childCount) {
                        if (container.getChildAt(i) === child) {
                            targetIndex = i + 1
                            break
                        }
                    }
                    if (targetIndex >= 0 && targetIndex <= container.childCount) {
                        container.addView(customRow, targetIndex)
                        Log.i(TAG, "Injected NexFusion Settings row into Telegram Settings screen")
                    }
                }
            }
        }
    }

    private fun findTextRecursively(view: View): String? {
        if (view is TextView) {
            val t = view.text?.toString()?.trim()
            if (!t.isNullOrEmpty()) return t
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findTextRecursively(view.getChildAt(i))
                if (found != null) return found
            }
        }
        null
        return null
    }

    private fun findActivity(view: View): Activity? {
        var context = view.context
        while (context is android.content.ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return lastResumedActivity?.get()
    }

    private fun buildNativeSettingsRow(activity: Activity): View {
        val context = activity
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val onSurface = if (isDark) 0xFFF5F5F5.toInt() else 0xFF1C1B1F.toInt()
        val onSurfaceVariant = if (isDark) 0xFF8A9AA8.toInt() else 0xFF707579.toInt()
        val cardBg = if (isDark) 0xFF17212B.toInt() else 0xFFFFFFFF.toInt()
        val primaryColor = if (isDark) 0xFF5288C1.toInt() else 0xFF2481CC.toInt()

        val dp = { v: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            background = RippleDrawable(ColorStateList.valueOf(0x1F888888), ColorDrawable(cardBg), null)
            isClickable = true
            isFocusable = true
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(16f)
                rightMargin = dp(16f)
                topMargin = dp(4f)
                bottomMargin = dp(4f)
            }
        }

        // Icon container: FrameLayout 40dp x 40dp
        val iconContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f)).apply {
                marginEnd = dp(16f)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor((primaryColor and 0x00FFFFFF) or 0x26000000)
            }
        }
        val iconView = ImageView(context).apply {
            setImageDrawable(TelegramSettingsDialog.SparkleDrawable(primaryColor))
            layoutParams = FrameLayout.LayoutParams(dp(24f), dp(24f), Gravity.CENTER)
        }
        iconContainer.addView(iconView)
        row.addView(iconContainer)

        // Text container
        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleView = TextView(context).apply {
            text = "NexFusion Settings"
            textSize = 16f
            setTextColor(onSurface)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val subtitleView = TextView(context).apply {
            text = "Privacy, ads, media, downloads & extra tweaks"
            textSize = 13f
            setTextColor(onSurfaceVariant)
            setPadding(0, dp(2f), 0, 0)
        }
        textLayout.addView(titleView)
        textLayout.addView(subtitleView)
        row.addView(textLayout)

        row.setOnClickListener {
            TelegramSettingsDialog.show(activity)
        }

        return row
    }
}
