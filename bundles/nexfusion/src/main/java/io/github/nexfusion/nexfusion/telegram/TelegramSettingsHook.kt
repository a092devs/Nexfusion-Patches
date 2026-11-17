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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XposedHelpers
import java.lang.ref.WeakReference

private const val TAG = "NexFusion"
private const val SHORTCUT_ID = "nexfusion_telegram_settings"
private const val EXTRA_OPEN_SETTINGS = "open_nexfusion_settings"
private const val SEPARATE_PANEL_TAG = "nexfusion_separate_panel"
private const val TOP_PILL_TAG = "nexfusion_top_pill"
private const val TOP_ACTION_BTN_TAG = "nexfusion_action_btn"
private const val POPUP_ITEM_TAG = "nexfusion_popup_item"
private const val BOTTOM_TAB_TAG_KEY = 0x4E455802
private const val LISTENER_ATTACHED_KEY = 0x4E455801

object TelegramSettingsHook {

    private var installed = false
    private var lastScanTime = 0L
    private var lastResumedActivity: WeakReference<Activity>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val periodicScanner = object : Runnable {
        override fun run() {
            val activity = lastResumedActivity?.get()
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                inspectAndInjectHierarchy(activity)
                mainHandler.postDelayed(this, 800)
            }
        }
    }

    fun install(classLoader: ClassLoader) {
        if (installed) return
        installed = true

        val launchActivityClass = runCatching {
            classLoader.loadClass("org.telegram.ui.LaunchActivity")
        }.getOrNull()

        if (launchActivityClass != null) {
            // 1. Hook LaunchActivity.onCreate
            runCatching {
                XposedHelpers.findAndHookMethod(
                    launchActivityClass,
                    "onCreate",
                    Bundle::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val activity = param.thisObject as? Activity ?: return
                            lastResumedActivity = WeakReference(activity)
                            TelegramPreferences.init(activity)
                            setupLauncherShortcut(activity)
                            checkIntent(activity, activity.intent)
                            scheduleHierarchyHooks(activity)
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

            // 3. Hook LaunchActivity.onResume
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
                            scheduleHierarchyHooks(activity)
                            inspectAndInjectHierarchy(activity)
                        }
                    }
                )
            }

            // 4. Hook LaunchActivity.onWindowFocusChanged
            runCatching {
                XposedHelpers.findAndHookMethod(
                    launchActivityClass,
                    "onWindowFocusChanged",
                    Boolean::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val activity = param.thisObject as? Activity ?: return
                            val hasFocus = param.args.firstOrNull() as? Boolean ?: false
                            if (hasFocus) {
                                inspectAndInjectHierarchy(activity)
                            }
                        }
                    }
                )
            }
        }
    }

    private fun checkIntent(activity: Activity, intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false) ||
            intent.action == "android.intent.action.APPLICATION_PREFERENCES"
        ) {
            intent.removeExtra(EXTRA_OPEN_SETTINGS)
            activity.window?.decorView?.post {
                TelegramSettingsDialog.show(activity)
            }
        }
    }

    private fun scheduleHierarchyHooks(activity: Activity) {
        mainHandler.removeCallbacks(periodicScanner)
        mainHandler.post(periodicScanner)

        val decor = activity.window?.decorView ?: return
        if (decor.getTag(LISTENER_ATTACHED_KEY) == true) return
        decor.setTag(LISTENER_ATTACHED_KEY, true)

        decor.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val now = SystemClock.uptimeMillis()
            if (now - lastScanTime >= 200) {
                lastScanTime = now
                inspectAndInjectHierarchy(activity)
            }
        }

        decor.viewTreeObserver?.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val now = SystemClock.uptimeMillis()
                if (now - lastScanTime >= 200) {
                    lastScanTime = now
                    inspectAndInjectHierarchy(activity)
                }
            }
        })
    }

    private fun inspectAndInjectHierarchy(activity: Activity) {
        val decor = activity.window?.decorView ?: return
        runCatching {
            // 1. Separate panel on top of Settings > Accounts
            injectSeparatePanelAboveAccounts(decor, activity)

            // 2. Settings action bar top pill and menu button
            injectSettingsActionBar(decor, activity)

            // 3. More options popup menu
            injectPopupMenu(decor, activity)

            // 4. Bottom navigation tabs and version footer
            scanAndAttachListeners(decor, activity)
        }.onFailure {
            Log.w(TAG, "inspectAndInjectHierarchy error: ${it.message}")
        }
    }

    /**
     * Injects a dedicated separate panel titled "NexFusion" directly above Settings > Accounts,
     * separating the profile header from the settings options.
     */
    private fun injectSeparatePanelAboveAccounts(decor: View, activity: Activity) {
        val recyclerView = findSettingsRecyclerView(decor) ?: return
        if (recyclerView.childCount == 0) return

        val child0 = recyclerView.getChildAt(0) as? ViewGroup ?: return
        if (child0.findViewWithTag<View>(SEPARATE_PANEL_TAG) != null) return

        val dp = { v: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, activity.resources.displayMetrics).toInt()
        }

        child0.post {
            runCatching {
                if (child0.findViewWithTag<View>(SEPARATE_PANEL_TAG) != null) return@runCatching
                val panel = buildSeparatePanel(activity)
                panel.tag = SEPARATE_PANEL_TAG

                // Find the base content view inside child0 (usually subchild 0)
                val baseContent = if (child0.childCount > 0) child0.getChildAt(0) else null
                val baseHeight = baseContent?.height?.takeIf { it > 0 } ?: dp(180f)

                if (child0 is FrameLayout) {
                    val lp = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        leftMargin = dp(14f)
                        rightMargin = dp(14f)
                        topMargin = baseHeight + dp(8f)
                        bottomMargin = dp(12f)
                    }
                    child0.addView(panel, lp)
                    child0.requestLayout()
                    Log.i(TAG, "Injected separate NexFusion panel on top of Settings > Accounts")
                } else if (child0 is LinearLayout) {
                    val lp = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        leftMargin = dp(14f)
                        rightMargin = dp(14f)
                        topMargin = dp(8f)
                        bottomMargin = dp(12f)
                    }
                    child0.addView(panel, lp)
                    child0.requestLayout()
                    Log.i(TAG, "Injected separate NexFusion panel into LinearLayout header")
                }
            }
        }
    }

    private fun findSettingsRecyclerView(root: View): ViewGroup? {
        if (root is ViewGroup && root.javaClass.name.contains("Recycler")) {
            if (containsChildWithText(root, "Account") || containsChildWithText(root, "Chat Settings")) {
                return root
            }
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findSettingsRecyclerView(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun containsChildWithText(group: ViewGroup, text: String): Boolean {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (findTextRecursively(child) == text) return true
        }
        return false
    }

    private fun buildSeparatePanel(activity: Activity): View {
        val context = activity
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val primaryColor = if (isDark) 0xFF5288C1.toInt() else 0xFF2481CC.toInt()
        val cardBg = if (isDark) 0xFF17212B.toInt() else 0xFFFFFFFF.toInt()
        val onSurface = if (isDark) 0xFFF5F5F5.toInt() else 0xFF1C1B1F.toInt()
        val onSurfaceVariant = if (isDark) 0xFF8A9AA8.toInt() else 0xFF707579.toInt()

        val dp = { v: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))

            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16f).toFloat()
                setColor(cardBg)
                if (!isDark) {
                    setStroke(dp(0.8f), 0x1A000000)
                }
            }
            background = RippleDrawable(ColorStateList.valueOf(0x22888888), shape, null)
            isClickable = true
            isFocusable = true
            contentDescription = "NexFusion"
            elevation = dp(1.5f).toFloat()
        }

        // Icon container: circular accent background with SparkleDrawable
        val iconContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42f), dp(42f)).apply {
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
        card.addView(iconContainer)

        // Text Layout: Title + Subtitle
        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleView = TextView(context).apply {
            text = "NexFusion"
            textSize = 16.5f
            setTextColor(onSurface)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val subtitleView = TextView(context).apply {
            text = "Patches & Features"
            textSize = 13f
            setTextColor(onSurfaceVariant)
            setPadding(0, dp(2f), 0, 0)
        }
        textLayout.addView(titleView)
        textLayout.addView(subtitleView)
        card.addView(textLayout)

        // Chevron arrow indicator on right
        val chevron = ImageView(context).apply {
            setImageDrawable(ChevronArrowDrawable(onSurfaceVariant))
            layoutParams = LinearLayout.LayoutParams(dp(16f), dp(16f)).apply {
                marginStart = dp(8f)
            }
        }
        card.addView(chevron)

        card.setOnClickListener {
            TelegramSettingsDialog.show(activity)
        }

        return card
    }

    private fun injectSettingsActionBar(decor: View, activity: Activity) {
        val bar = findSettingsActionBar(decor) ?: return

        // 1. Center Pill in Action Bar: [ ✨ NexFusion ]
        if (bar.findViewWithTag<View>(TOP_PILL_TAG) == null) {
            val pill = buildTopBarPill(activity)
            val dp = { v: Float ->
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, activity.resources.displayMetrics).toInt()
            }
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(38f),
                Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            ).apply {
                bottomMargin = dp(10f)
            }
            bar.addView(pill, lp)
            Log.i(TAG, "Injected NexFusion top bar pill into Telegram Settings")
        }

        // 2. Action Menu Button next to Search / More options
        for (i in 0 until bar.childCount) {
            val child = bar.getChildAt(i)
            if (child is LinearLayout) {
                var hasSearch = false
                for (j in 0 until child.childCount) {
                    val desc = child.getChildAt(j).contentDescription?.toString() ?: ""
                    if (desc == "Search" || desc == "More options") {
                        hasSearch = true
                        break
                    }
                }
                if (hasSearch && child.findViewWithTag<View>(TOP_ACTION_BTN_TAG) == null) {
                    val btn = buildTopBarActionButton(activity)
                    child.addView(btn, 0)
                    Log.i(TAG, "Injected NexFusion action button into Telegram Settings action menu")
                }
            }
        }
    }

    private fun findSettingsActionBar(view: View): FrameLayout? {
        if (view is FrameLayout) {
            var hasQr = false
            var hasActionMenu = false
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val desc = child.contentDescription?.toString() ?: ""
                if (desc == "QR Code") {
                    hasQr = true
                }
                if (child is LinearLayout) {
                    for (j in 0 until child.childCount) {
                        val d = child.getChildAt(j).contentDescription?.toString() ?: ""
                        if (d == "Search" || d == "More options") {
                            hasActionMenu = true
                            break
                        }
                    }
                }
            }
            if (hasQr || hasActionMenu) return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findSettingsActionBar(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun buildTopBarPill(activity: Activity): View {
        val context = activity
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val primaryColor = if (isDark) 0xFF5288C1.toInt() else 0xFF2481CC.toInt()
        val pillBgColor = if (isDark) 0x335288C1.toInt() else 0x222481CC.toInt()
        val borderColor = if (isDark) 0x665288C1.toInt() else 0x552481CC.toInt()

        val dp = { v: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()
        }

        val pill = LinearLayout(context).apply {
            tag = TOP_PILL_TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14f), dp(6f), dp(16f), dp(6f))

            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(19f).toFloat()
                setColor(pillBgColor)
                setStroke(dp(1.2f), borderColor)
            }
            background = RippleDrawable(ColorStateList.valueOf(0x33888888), shape, null)
            isClickable = true
            isFocusable = true
            contentDescription = "NexFusion"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tooltipText = "NexFusion"
            }

            val icon = ImageView(context).apply {
                setImageDrawable(TelegramSettingsDialog.SparkleDrawable(primaryColor))
                layoutParams = LinearLayout.LayoutParams(dp(18f), dp(18f)).apply {
                    marginEnd = dp(6f)
                }
            }
            addView(icon)

            val text = TextView(context).apply {
                text = "NexFusion"
                textSize = 14f
                setTextColor(primaryColor)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            }
            addView(text)

            setOnClickListener {
                TelegramSettingsDialog.show(activity)
            }
        }
        return pill
    }

    private fun buildTopBarActionButton(activity: Activity): View {
        val context = activity
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val primaryColor = if (isDark) 0xFF5288C1.toInt() else 0xFF2481CC.toInt()

        val dp = { v: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()
        }

        val btn = ImageView(context).apply {
            tag = TOP_ACTION_BTN_TAG
            setImageDrawable(TelegramSettingsDialog.SparkleDrawable(primaryColor))
            val pad = dp(10f)
            setPadding(pad, pad, pad, pad)
            background = RippleDrawable(ColorStateList.valueOf(0x33888888), null, null)
            isClickable = true
            isFocusable = true
            contentDescription = "NexFusion"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tooltipText = "NexFusion"
            }
            setOnClickListener {
                TelegramSettingsDialog.show(activity)
            }
            layoutParams = LinearLayout.LayoutParams(dp(48f), dp(48f)).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        }
        return btn
    }

    private fun injectPopupMenu(view: View, activity: Activity) {
        if (view is LinearLayout && view.childCount > 0) {
            var isMoreOptionsPopup = false
            for (i in 0 until view.childCount) {
                val item = view.getChildAt(i)
                val text = findTextRecursively(item)
                if (text == "Day Mode" || text == "Night Mode" || text == "New Group" || text == "Saved Messages") {
                    isMoreOptionsPopup = true
                    break
                }
            }
            if (isMoreOptionsPopup && view.findViewWithTag<View>(POPUP_ITEM_TAG) == null) {
                val menuItem = buildPopupMenuItem(activity, view)
                view.addView(menuItem, 0)
                Log.i(TAG, "Injected NexFusion item into Settings popup menu")
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                injectPopupMenu(view.getChildAt(i), activity)
            }
        }
    }

    private fun buildPopupMenuItem(activity: Activity, popupContainer: ViewGroup): View {
        val context = activity
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val onSurface = if (isDark) 0xFFF5F5F5.toInt() else 0xFF1C1B1F.toInt()
        val primaryColor = if (isDark) 0xFF5288C1.toInt() else 0xFF2481CC.toInt()

        val dp = { v: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()
        }

        val itemFrame = FrameLayout(context).apply {
            tag = POPUP_ITEM_TAG
            background = RippleDrawable(ColorStateList.valueOf(0x22888888), null, null)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48f)
            )
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), 0, dp(16f), 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val icon = ImageView(context).apply {
            setImageDrawable(TelegramSettingsDialog.SparkleDrawable(primaryColor))
            layoutParams = LinearLayout.LayoutParams(dp(22f), dp(22f)).apply {
                marginEnd = dp(18f)
            }
        }
        layout.addView(icon)

        val text = TextView(context).apply {
            text = "NexFusion"
            textSize = 16f
            setTextColor(onSurface)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        layout.addView(text)
        itemFrame.addView(layout)

        itemFrame.setOnClickListener {
            runCatching {
                (popupContainer.parent?.parent?.parent as? View)?.visibility = View.GONE
            }
            TelegramSettingsDialog.show(activity)
        }

        return itemFrame
    }

    private fun scanAndAttachListeners(view: View, activity: Activity) {
        if (view is TextView) {
            val text = view.text?.toString() ?: ""

            // 1. Long-press on "Telegram" chat list header title opens settings
            if (text == "Telegram" && view.isClickable) {
                view.setOnLongClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    TelegramSettingsDialog.show(activity)
                    true
                }
            }

            // 2. "Settings" tab in bottom navigation
            if (text == "Settings" && view.parent is ViewGroup) {
                val tab = view.parent as ViewGroup
                if (tab.getTag(BOTTOM_TAB_TAG_KEY) == null) {
                    tab.setTag(BOTTOM_TAB_TAG_KEY, true)
                    tab.isLongClickable = true
                    tab.setOnLongClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        TelegramSettingsDialog.show(activity)
                        true
                    }
                }
            }

            // 3. Telegram version text at bottom of Settings screen
            if (text.startsWith("Telegram for Android") || text.contains("Telegram for Android v")) {
                if (!text.contains("NexFusion")) {
                    view.text = "${text.trim()} • NexFusion"
                }
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
                scanAndAttachListeners(view.getChildAt(i), activity)
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
                    .setShortLabel("NexFusion")
                    .setLongLabel("NexFusion")
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
        return null
    }

    private class ChevronArrowDrawable(private val color: Int) : android.graphics.drawable.Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@ChevronArrowDrawable.color
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val path = android.graphics.Path()

        override fun draw(canvas: Canvas) {
            val b = bounds
            path.reset()
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            path.moveTo(b.left + w * 0.35f, b.top + h * 0.25f)
            path.lineTo(b.left + w * 0.65f, b.top + h * 0.5f)
            path.lineTo(b.left + w * 0.35f, b.top + h * 0.75f)
            canvas.drawPath(path, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}
