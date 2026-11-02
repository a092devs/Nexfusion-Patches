package io.github.nexalloy.nexfusion.gboard

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.preference.PreferenceManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.min

/**
 * PixelBoard-style "Advanced settings" screen matching the native Material 3 Gboard settings UI.
 *
 * Implements the exact layout and options from PixelBoard by NexAlloy:
 * - Enable AI Writing Tools
 * - Support All Keyboards
 * - Enable Advanced Voice Typing (Rambler)
 * - Material 3 Expressive Rounded Keys (Belka UI)
 * - Toolbar with back navigation and process restart utility (↻)
 * - Custom attribution footer ("NexAlloy")
 */
object GboardSettingsDialog {

    const val PREF_KEY_AI_WRITING_TOOLS_ENABLED = "pref_ai_writing_tools_enabled"
    const val PREF_KEY_ALL_KEYBOARDS_ENABLED = "pref_ai_writing_tools_all_keyboards_enabled"
    const val PREF_KEY_ADVANCED_VOICE_TYPING = "pref_advanced_voice_typing_enabled"
    const val PREF_KEY_ROUNDED_KEYS_ENABLED = "rounded_keys_enabled"

    // Official Gboard stock preference keys synchronized with our toggles
    const val GBOARD_PREF_ENABLE_ROUNDED_KEYS = "enable_rounded_keys"
    const val GBOARD_PREF_ENABLE_JETSON = "enable_jetson"
    const val GBOARD_PREF_WRITING_TOOLS_OPT_IN = "pref_key_writing_tools_opt_in"
    const val GBOARD_PREF_JARVIS_CONSENT = "jarvis_consent_history"
    const val GBOARD_PREF_AUTO_PUNCTUATION = "pref_key_auto_punctuation"
    const val GBOARD_PREF_ENABLE_AUTO_PUNCTUATION = "pref_key_enable_auto_punctuation"

    fun show(activity: Activity) {
        val isDark = isDarkTheme(activity)
        val themeRes = if (isDark) {
            android.R.style.Theme_DeviceDefault_NoActionBar
        } else {
            android.R.style.Theme_DeviceDefault_Light_NoActionBar
        }
        val dialog = Dialog(activity, themeRes)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val window = dialog.window
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(if (isDark) 0xFF141218.toInt() else 0xFFFEF7FF.toInt()))
        }

        val root = buildContentView(activity, dialog, isDark)
        dialog.setContentView(root)
        dialog.show()
    }

    private fun isDarkTheme(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun dp(context: Context, value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics
        ).toInt()
    }

    private fun getGboardPreferences(context: Context): SharedPreferences {
        val targetContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !context.isDeviceProtectedStorage) {
            runCatching { context.createDeviceProtectedStorageContext() }.getOrNull() ?: context
        } else context
        return PreferenceManager.getDefaultSharedPreferences(targetContext)
    }

    private fun buildContentView(activity: Activity, dialog: Dialog, isDark: Boolean): View {
        val context = activity
        val prefs = getGboardPreferences(context)

        // Colors matching Google Material You palette
        val bgCol = if (isDark) 0xFF141218.toInt() else 0xFFFEF7FF.toInt()
        val cardBg = if (isDark) 0xFF211F26.toInt() else 0xFFF3EDF7.toInt()
        val primaryCol = if (isDark) 0xFFD0BCFF.toInt() else 0xFF65558F.toInt()
        val onSurface = if (isDark) 0xFFE6E0E9.toInt() else 0xFF1D1B20.toInt()
        val onSurfaceVariant = if (isDark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt()
        val dividerCol = if (isDark) 0xFF2F2D36.toInt() else 0xFFE6E0E9.toInt()

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgCol)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val statusBarHeight = runCatching {
            val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else dp(context, 28f)
        }.getOrDefault(dp(context, 28f))

        // Toolbar
        val toolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 8f), statusBarHeight + dp(context, 8f), dp(context, 16f), dp(context, 12f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Back button (←)
        val backBtn = ImageView(context).apply {
            setImageDrawable(BackArrowDrawable(onSurface))
            setPadding(dp(context, 12f), dp(context, 12f), dp(context, 12f), dp(context, 12f))
            background = RippleDrawable(ColorStateList.valueOf(0x33888888), null, null)
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }
        toolbar.addView(backBtn, LinearLayout.LayoutParams(dp(context, 48f), dp(context, 48f)))

        // Title: Advanced settings
        val titleView = TextView(context).apply {
            text = "Advanced settings"
            textSize = 20f
            setTextColor(onSurface)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding(dp(context, 12f), 0, 0, 0)
        }
        val titleParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        toolbar.addView(titleView, titleParams)

        // Restart button (↻)
        val restartBtn = ImageView(context).apply {
            setImageDrawable(RestartIconDrawable(primaryCol))
            setPadding(dp(context, 12f), dp(context, 12f), dp(context, 12f), dp(context, 12f))
            background = RippleDrawable(ColorStateList.valueOf(0x33888888), null, null)
            isClickable = true
            isFocusable = true
            contentDescription = "Restart Gboard"
            setOnClickListener {
                Toast.makeText(context, "Restarting Gboard...", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                postDelayed({
                    android.os.Process.killProcess(android.os.Process.myPid())
                }, 200)
            }
        }
        toolbar.addView(restartBtn, LinearLayout.LayoutParams(dp(context, 48f), dp(context, 48f)))
        rootLayout.addView(toolbar)

        // Scrollable content
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isFillViewport = true
        }

        val scrollContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(context, 8f), 0, dp(context, 24f))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Section header: "Features"
        val sectionTitle = TextView(context).apply {
            text = "Features"
            textSize = 14f
            setTextColor(primaryCol)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding(dp(context, 24f), dp(context, 8f), dp(context, 24f), dp(context, 8f))
        }
        scrollContent.addView(sectionTitle)

        // Card container for switches
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = dp(context, 24f).toFloat()
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(context, 16f)
                rightMargin = dp(context, 16f)
                topMargin = dp(context, 4f)
                bottomMargin = dp(context, 16f)
            }
        }

        // Switch 1: Enable AI Writing Tools
        val swWritingTools = addSwitchRow(
            context = context,
            container = card,
            title = "Enable AI Writing Tools",
            summary = "Force-show Corrections & suggestions > Writing tools > Show writing tools icon in suggestion strip while typing. Force-stop and restart Gboard for changes to take effect.",
            initial = prefs.getBoolean(PREF_KEY_AI_WRITING_TOOLS_ENABLED, true),
            primaryColor = primaryCol,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            onToggle = { isChecked ->
                prefs.edit()
                    .putBoolean(PREF_KEY_AI_WRITING_TOOLS_ENABLED, isChecked)
                    .putBoolean(GBOARD_PREF_WRITING_TOOLS_OPT_IN, isChecked)
                    .putBoolean(GBOARD_PREF_JARVIS_CONSENT, isChecked)
                    .commit()
                // Mirror to credential storage if separate
                runCatching {
                    PreferenceManager.getDefaultSharedPreferences(context)
                        .edit()
                        .putBoolean(PREF_KEY_AI_WRITING_TOOLS_ENABLED, isChecked)
                        .commit()
                }
            }
        )

        addDivider(context, card, dividerCol)

        // Switch 2: Support All Keyboards
        addSwitchRow(
            context = context,
            container = card,
            title = "Support All Keyboards",
            summary = "When enabled, force writing tools to work on every keyboard. Force-stop and restart Gboard for the change to take effect.",
            initial = prefs.getBoolean(PREF_KEY_ALL_KEYBOARDS_ENABLED, true),
            primaryColor = primaryCol,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            onToggle = { isChecked ->
                prefs.edit()
                    .putBoolean(PREF_KEY_ALL_KEYBOARDS_ENABLED, isChecked)
                    .commit()
                runCatching {
                    PreferenceManager.getDefaultSharedPreferences(context)
                        .edit()
                        .putBoolean(PREF_KEY_ALL_KEYBOARDS_ENABLED, isChecked)
                        .commit()
                }
            }
        )

        addDivider(context, card, dividerCol)

        // Switch 3: Enable Advanced Voice Typing (Rambler)
        addSwitchRow(
            context = context,
            container = card,
            title = "Enable Advanced Voice Typing",
            summary = "Voice typing > Dictation type. Force-stop and restart Gboard for changes to take effect.",
            initial = prefs.getBoolean(PREF_KEY_ADVANCED_VOICE_TYPING, true),
            primaryColor = primaryCol,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            onToggle = { isChecked ->
                prefs.edit()
                    .putBoolean(PREF_KEY_ADVANCED_VOICE_TYPING, isChecked)
                    .putBoolean(GBOARD_PREF_ENABLE_JETSON, isChecked)
                    .putBoolean(GBOARD_PREF_AUTO_PUNCTUATION, isChecked)
                    .putBoolean(GBOARD_PREF_ENABLE_AUTO_PUNCTUATION, isChecked)
                    .commit()
                runCatching {
                    PreferenceManager.getDefaultSharedPreferences(context)
                        .edit()
                        .putBoolean(PREF_KEY_ADVANCED_VOICE_TYPING, isChecked)
                        .putBoolean(GBOARD_PREF_ENABLE_JETSON, isChecked)
                        .commit()
                }
            }
        )

        addDivider(context, card, dividerCol)

        // Switch 4: Material 3 Rounded Keys (Belka UI)
        addSwitchRow(
            context = context,
            container = card,
            title = "Material 3 Rounded Keys (Belka UI)",
            summary = "Enable Google's Material 3 Expressive pill-shaped keycaps.",
            initial = prefs.getBoolean(PREF_KEY_ROUNDED_KEYS_ENABLED, true),
            primaryColor = primaryCol,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            onToggle = { isChecked ->
                prefs.edit()
                    .putBoolean(PREF_KEY_ROUNDED_KEYS_ENABLED, isChecked)
                    .putBoolean(GBOARD_PREF_ENABLE_ROUNDED_KEYS, isChecked)
                    .commit()
                runCatching {
                    PreferenceManager.getDefaultSharedPreferences(context)
                        .edit()
                        .putBoolean(PREF_KEY_ROUNDED_KEYS_ENABLED, isChecked)
                        .putBoolean(GBOARD_PREF_ENABLE_ROUNDED_KEYS, isChecked)
                        .commit()
                }
            }
        )

        scrollContent.addView(card)

        // Footer attribution ("NexAlloy")
        val footerView = TextView(context).apply {
            text = "NexAlloy"
            textSize = 13f
            setTextColor(onSurfaceVariant)
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 16f), 0, dp(context, 24f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        scrollContent.addView(footerView)

        scrollView.addView(scrollContent)
        rootLayout.addView(scrollView)
        return rootLayout
    }

    private fun addDivider(context: Context, container: LinearLayout, color: Int) {
        val divider = View(context).apply {
            setBackgroundColor(color)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 1f)
            ).apply {
                leftMargin = dp(context, 16f)
                rightMargin = dp(context, 16f)
            }
        }
        container.addView(divider)
    }

    private fun addSwitchRow(
        context: Context,
        container: LinearLayout,
        title: String,
        summary: String,
        initial: Boolean,
        primaryColor: Int,
        onSurface: Int,
        onSurfaceVariant: Int,
        onToggle: (Boolean) -> Unit
    ): Switch {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 16f), dp(context, 16f), dp(context, 16f), dp(context, 16f))
            isClickable = true
            isFocusable = true
            background = RippleDrawable(ColorStateList.valueOf(0x22888888), null, null)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(context, 16f)
            }
        }

        val titleView = TextView(context).apply {
            text = title
            textSize = 16f
            setTextColor(onSurface)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        textLayout.addView(titleView)

        val summaryView = TextView(context).apply {
            text = summary
            textSize = 13f
            setTextColor(onSurfaceVariant)
            setPadding(0, dp(context, 4f), 0, 0)
        }
        textLayout.addView(summaryView)
        row.addView(textLayout)

        val switchView = Switch(context).apply {
            isChecked = initial
            isClickable = false // handled by row click
        }
        row.addView(switchView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        row.setOnClickListener {
            val newState = !switchView.isChecked
            switchView.isChecked = newState
            onToggle(newState)
        }

        container.addView(row)
        return switchView
    }

    // Material 3 4-point Sparkle Icon Drawable
    class SparkleDrawable(private val color: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@SparkleDrawable.color
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = min(b.width(), b.height()) * 0.44f
            val path = Path().apply {
                moveTo(cx, cy - r)
                quadTo(cx, cy, cx + r, cy)
                quadTo(cx, cy, cx, cy + r)
                quadTo(cx, cy, cx - r, cy)
                quadTo(cx, cy, cx, cy - r)
                close()
            }
            canvas.drawPath(path, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth(): Int = 72
        override fun getIntrinsicHeight(): Int = 72
    }

    // Back Arrow Drawable (←)
    class BackArrowDrawable(private val color: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@BackArrowDrawable.color
            strokeWidth = 5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val span = min(b.width(), b.height()) * 0.32f
            canvas.drawLine(cx - span, cy, cx + span, cy, paint)
            canvas.drawLine(cx - span, cy, cx - span * 0.3f, cy - span * 0.7f, paint)
            canvas.drawLine(cx - span, cy, cx - span * 0.3f, cy + span * 0.7f, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    // Restart Icon Drawable (↻) - clockwise circular refresh arrow
    class RestartIconDrawable(private val color: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@RestartIconDrawable.color
            strokeWidth = 6f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@RestartIconDrawable.color
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = min(b.width(), b.height()) * 0.40f

            // Draw a clockwise circular arrow: arc goes from top (~270°/top) around clockwise
            // to bottom-left (~225°), sweeping about 240°
            val rect = android.graphics.RectF(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(rect, 270f, 240f, false, paint)

            // Arrow head at end of arc (~225° = bottom-left), pointing down-left continuing flow
            val endAngle = 225f
            val angleRad = Math.toRadians(endAngle.toDouble())
            val endX = cx + r * Math.cos(angleRad).toFloat()
            val endY = cy + r * Math.sin(angleRad).toFloat()

            // Direction tangent to arc at end point (clockwise flow = down-left at 225°)
            val dirAngle = endAngle + 90f // tangent direction
            val dx = Math.cos(Math.toRadians(dirAngle.toDouble())).toFloat()
            val dy = Math.sin(Math.toRadians(dirAngle.toDouble())).toFloat()
            val arrowLen = 9f
            val tipX = endX + dx * arrowLen
            val tipY = endY + dy * arrowLen
            // Arrow head base (perpendicular to direction)
            val baseW = 6f
            val px = -dy * baseW
            val py = dx * baseW

            val arrowPath = Path().apply {
                moveTo(tipX, tipY)
                lineTo(endX + px, endY + py)
                lineTo(endX - px, endY - py)
                close()
            }
            canvas.drawPath(arrowPath, fillPaint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            fillPaint.alpha = alpha
        }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            paint.colorFilter = colorFilter
            fillPaint.colorFilter = colorFilter
        }
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}

