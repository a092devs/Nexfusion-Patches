package io.github.nexfusion.nexfusion.telegram

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.cos
import kotlin.math.sin
import kotlin.system.exitProcess

object TelegramSettingsDialog {

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
            window.setBackgroundDrawable(ColorDrawable(if (isDark) 0xFF0E1621.toInt() else 0xFFF4F4F5.toInt()))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val decorView = window.decorView
                var flags = decorView.systemUiVisibility
                if (!isDark) {
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    }
                }
                decorView.systemUiVisibility = flags
            }
        }

        val root = buildContentView(activity, dialog, isDark)
        dialog.setContentView(root)
        dialog.show()
    }

    private fun isDarkTheme(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun dp(context: Context, value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics
        ).toInt()
    }

    private fun buildContentView(activity: Activity, dialog: Dialog, isDark: Boolean): View {
        val context = activity
        val prefs = TelegramPreferences.get(context)

        val bgColor = if (isDark) 0xFF0E1621.toInt() else 0xFFF4F4F5.toInt()
        val cardBg = if (isDark) 0xFF17212B.toInt() else 0xFFFFFFFF.toInt()
        val primaryCol = if (isDark) 0xFF5288C1.toInt() else 0xFF2481CC.toInt()
        val onSurface = if (isDark) 0xFFF5F5F5.toInt() else 0xFF1C1B1F.toInt()
        val onSurfaceVariant = if (isDark) 0xFF8A9AA8.toInt() else 0xFF707579.toInt()
        val dividerCol = if (isDark) 0x1AFFFFFF else 0x14000000

        val statusBarHeight = runCatching {
            val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) context.resources.getDimensionPixelSize(resId) else dp(context, 24f)
        }.getOrDefault(dp(context, 24f))

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Toolbar
        val toolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 4f), statusBarHeight + dp(context, 4f), dp(context, 4f), dp(context, 4f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            minimumHeight = statusBarHeight + dp(context, 56f)
        }

        val backBtn = ImageView(context).apply {
            setImageDrawable(BackArrowDrawable(onSurface))
            val pad = dp(context, 12f)
            setPadding(pad, pad, pad, pad)
            background = RippleDrawable(ColorStateList.valueOf(0x33888888), null, null)
            isClickable = true
            isFocusable = true
            contentDescription = "Navigate up"
            setOnClickListener { dialog.dismiss() }
        }
        toolbar.addView(backBtn, LinearLayout.LayoutParams(dp(context, 48f), dp(context, 48f)).apply {
            gravity = Gravity.CENTER_VERTICAL
        })

        val titleContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 12f), 0, dp(context, 8f), 0)
        }
        val titleView = TextView(context).apply {
            text = "NexFusion Settings"
            textSize = 19f
            setTextColor(onSurface)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val subtitleView = TextView(context).apply {
            text = "Telegram Customizations"
            textSize = 12f
            setTextColor(onSurfaceVariant)
        }
        titleContainer.addView(titleView)
        titleContainer.addView(subtitleView)
        toolbar.addView(titleContainer, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            gravity = Gravity.CENTER_VERTICAL
        })

        // Restart Telegram button
        val restartBtn = ImageView(context).apply {
            setImageDrawable(ResetIconDrawable(primaryCol))
            val pad = dp(context, 12f)
            setPadding(pad, pad, pad, pad)
            background = RippleDrawable(ColorStateList.valueOf(0x33888888), null, null)
            isClickable = true
            isFocusable = true
            contentDescription = "Restart Telegram"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tooltipText = "Restart Telegram"
            }
            setOnClickListener {
                Toast.makeText(context, "Restarting Telegram...", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                Handler(Looper.getMainLooper()).postDelayed({
                    runCatching { activity.finishAffinity() }
                    Process.killProcess(Process.myPid())
                    exitProcess(0)
                }, 150)
            }
        }
        toolbar.addView(restartBtn, LinearLayout.LayoutParams(dp(context, 48f), dp(context, 48f)).apply {
            gravity = Gravity.CENTER_VERTICAL
        })
        rootLayout.addView(toolbar)

        // Scrollable content
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            isFillViewport = true
            clipToPadding = false
        }

        rootLayout.setOnApplyWindowInsetsListener { _, insets ->
            val topInset: Int
            val leftInset: Int
            val rightInset: Int
            val bottomInset: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val safeInsets = insets.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())
                val navInsets = insets.getInsets(WindowInsets.Type.navigationBars())
                topInset = safeInsets.top
                leftInset = safeInsets.left
                rightInset = safeInsets.right
                bottomInset = navInsets.bottom
            } else {
                @Suppress("DEPRECATION")
                topInset = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                leftInset = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                rightInset = insets.systemWindowInsetRight
                @Suppress("DEPRECATION")
                bottomInset = insets.systemWindowInsetBottom
            }
            toolbar.setPadding(leftInset + dp(context, 4f), topInset + dp(context, 4f), rightInset + dp(context, 4f), dp(context, 4f))
            scrollView.setPadding(leftInset, 0, rightInset, bottomInset + dp(context, 16f))
            insets
        }

        val scrollContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val screenWidthDp = context.resources.configuration.screenWidthDp
        val cardMarginH = if (screenWidthDp > 600) {
            dp(context, ((screenWidthDp - 600) / 2f + 16f).coerceAtLeast(16f))
        } else {
            dp(context, 16f)
        }

        fun createCard(): LinearLayout {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(cardBg)
                    cornerRadius = dp(context, 16f).toFloat()
                }
                clipToOutline = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = cardMarginH
                    rightMargin = cardMarginH
                    topMargin = dp(context, 4f)
                    bottomMargin = dp(context, 16f)
                }
            }
        }

        fun addSectionHeader(title: String) {
            val tv = TextView(context).apply {
                text = title
                textSize = 14f
                setTextColor(primaryCol)
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setPadding(dp(context, 24f), dp(context, 12f), dp(context, 24f), dp(context, 4f))
            }
            scrollContent.addView(tv)
        }

        // --- Section 1: Privacy & Security ---
        addSectionHeader("Privacy & Security")
        val privacyCard = createCard()
        addSwitchRow(context, privacyCard, "Ghost Typing", "Hide typing, voice recording, and uploading status in chats",
            prefs.getBoolean(TelegramPreferences.PREF_GHOST_TYPING, false), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_GHOST_TYPING, it).commit()
        }
        addDivider(context, privacyCard, dividerCol)
        addSwitchRow(context, privacyCard, "Anti-Disappearing Media", "Prevent self-destructing timer photos and videos from expiring",
            prefs.getBoolean(TelegramPreferences.PREF_ANTI_DISAPPEARING_MEDIA, true), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_ANTI_DISAPPEARING_MEDIA, it).commit()
        }
        addDivider(context, privacyCard, dividerCol)
        addSwitchRow(context, privacyCard, "Suppress Screenshot Alert", "Take screenshots in secret chats without alerting the other person",
            prefs.getBoolean(TelegramPreferences.PREF_ANTI_SCREENSHOT_ALERT, true), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_ANTI_SCREENSHOT_ALERT, it).commit()
        }
        addDivider(context, privacyCard, dividerCol)
        addSwitchRow(context, privacyCard, "Allow Screenshots", "Bypass copy and screenshot protection (FLAG_SECURE) across restricted chats",
            prefs.getBoolean(TelegramPreferences.PREF_ALLOW_SCREENSHOTS, true), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_ALLOW_SCREENSHOTS, it).commit()
        }
        addDivider(context, privacyCard, dividerCol)
        addSwitchRow(context, privacyCard, "Keep Deleted Messages", "Retain messages that have been deleted by the sender",
            prefs.getBoolean(TelegramPreferences.PREF_KEEP_DELETED_MESSAGES, true), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_KEEP_DELETED_MESSAGES, it).commit()
        }
        addDivider(context, privacyCard, dividerCol)
        addSwitchRow(context, privacyCard, "Hide Phone Number", "Hide phone number from profile and settings headers for enhanced privacy",
            prefs.getBoolean(TelegramPreferences.PREF_HIDE_PHONE_NUMBER, true), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_HIDE_PHONE_NUMBER, it).commit()
        }
        addDivider(context, privacyCard, dividerCol)
        addSwitchRow(context, privacyCard, "Reveal Spoilers", "Automatically uncover spoiler-masked text in all messages",
            prefs.getBoolean(TelegramPreferences.PREF_REVEAL_SPOILERS, false), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_REVEAL_SPOILERS, it).commit()
        }
        scrollContent.addView(privacyCard)

        // --- Section 2: Ads & Sponsored Content ---
        addSectionHeader("Ads & Sponsored Content")
        val adsCard = createCard()
        addSwitchRow(context, adsCard, "Hide Sponsored Ads", "Remove sponsored messages and video advertisements in public channels",
            prefs.getBoolean(TelegramPreferences.PREF_HIDE_ADS, true), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_HIDE_ADS, it).commit()
        }
        addDivider(context, adsCard, dividerCol)
        addSwitchRow(context, adsCard, "Hide Sponsored Proxy Channels", "Hide promotional sponsored proxy channels from the chat list",
            prefs.getBoolean(TelegramPreferences.PREF_HIDE_SPONSORED_PROXY, true), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_HIDE_SPONSORED_PROXY, it).commit()
        }
        addDivider(context, adsCard, dividerCol)
        addSwitchRow(context, adsCard, "Hide Commerce & Promo Teasers", "Remove sponsored commerce bots and promotional catalog teasers",
            prefs.getBoolean(TelegramPreferences.PREF_HIDE_COMMERCE, true), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_HIDE_COMMERCE, it).commit()
        }
        addDivider(context, adsCard, dividerCol)
        addSwitchRow(context, adsCard, "Strip Link Tracking", "Remove marketing tracking parameters (utm_*, etc.) from shared links",
            prefs.getBoolean(TelegramPreferences.PREF_STRIP_LINK_TRACKING, true), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_STRIP_LINK_TRACKING, it).commit()
        }
        addDivider(context, adsCard, dividerCol)
        addSwitchRow(context, adsCard, "Disable Analytics & Telemetry", "Stop Telegram from sending analytics, crash telemetry, and call diagnostics",
            prefs.getBoolean(TelegramPreferences.PREF_DISABLE_ANALYTICS, true), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_DISABLE_ANALYTICS, it).commit()
        }
        addDivider(context, adsCard, dividerCol)
        addSwitchRow(context, adsCard, "Disable Update Checks", "Stop in-app update checks and recurring update prompts",
            prefs.getBoolean(TelegramPreferences.PREF_DISABLE_UPDATE_CHECKS, true), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_DISABLE_UPDATE_CHECKS, it).commit()
        }
        scrollContent.addView(adsCard)

        // --- Section 3: Media & Downloads ---
        addSectionHeader("Media & Downloads")
        val mediaCard = createCard()
        addSwitchRow(context, mediaCard, "Faster Parallel Downloads", "Increase parallel chunk connections for maximum media download speeds",
            prefs.getBoolean(TelegramPreferences.PREF_FASTER_DOWNLOADS, true), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_FASTER_DOWNLOADS, it).commit()
        }
        addDivider(context, mediaCard, dividerCol)
        addSwitchRow(context, mediaCard, "Keep Videos Muted", "Always start video playback muted until manually unmuted",
            prefs.getBoolean(TelegramPreferences.PREF_KEEP_VIDEOS_MUTED, false), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_KEEP_VIDEOS_MUTED, it).commit()
        }
        addDivider(context, mediaCard, dividerCol)
        addSwitchRow(context, mediaCard, "Play Voice One at a Time", "Do not auto-advance to the next voice message when finished",
            prefs.getBoolean(TelegramPreferences.PREF_PLAY_VOICE_ONE_AT_A_TIME, false), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_PLAY_VOICE_ONE_AT_A_TIME, it).commit()
        }
        addDivider(context, mediaCard, dividerCol)
        addSwitchRow(context, mediaCard, "Fix Social Link Previews", "Redirect X/Twitter, Instagram, TikTok, and Reddit links to rich preview proxies",
            prefs.getBoolean(TelegramPreferences.PREF_FIX_LINK_PREVIEWS, true), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_FIX_LINK_PREVIEWS, it).commit()
        }
        scrollContent.addView(mediaCard)

        // --- Section 4: Chat List & Navigation ---
        addSectionHeader("Chat List & Navigation")
        val navCard = createCard()
        addSwitchRow(context, navCard, "Hide Stories Bar", "Remove the stories tray from the top of the chat list",
            prefs.getBoolean(TelegramPreferences.PREF_HIDE_STORIES, true), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_HIDE_STORIES, it).commit()
        }
        addDivider(context, navCard, dividerCol)
        addSwitchRow(context, navCard, "Disable Archive Pull", "Disable pulling down the chat list to reveal archived chats",
            prefs.getBoolean(TelegramPreferences.PREF_DISABLE_ARCHIVE_PULL, false), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_DISABLE_ARCHIVE_PULL, it).commit()
        }
        addDivider(context, navCard, dividerCol)
        addSwitchRow(context, navCard, "Disable Channel Pull", "Prevent pulling up at the bottom of a channel from jumping to next channel",
            prefs.getBoolean(TelegramPreferences.PREF_DISABLE_CHANNEL_PULL, false), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_DISABLE_CHANNEL_PULL, it).commit()
        }
        addDivider(context, navCard, dividerCol)
        addSwitchRow(context, navCard, "Disable Chat Swipe Actions", "Disable horizontal swipe gestures on chats in the chat list",
            prefs.getBoolean(TelegramPreferences.PREF_DISABLE_CHAT_SWIPE, false), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_DISABLE_CHAT_SWIPE, it).commit()
        }
        addDivider(context, navCard, dividerCol)
        addSwitchRow(context, navCard, "Disable Draft Previews", "Hide draft text snippets from appearing on chat list cards",
            prefs.getBoolean(TelegramPreferences.PREF_DISABLE_DRAFT_PREVIEWS, false), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_DISABLE_DRAFT_PREVIEWS, it).commit()
        }
        addDivider(context, navCard, dividerCol)
        addSwitchRow(context, navCard, "Hide Keyboard on Scroll", "Automatically dismiss the software keyboard when scrolling chat messages",
            prefs.getBoolean(TelegramPreferences.PREF_HIDE_KEYBOARD_ON_SCROLL, false), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_HIDE_KEYBOARD_ON_SCROLL, it).commit()
        }
        addDivider(context, navCard, dividerCol)
        addSwitchRow(context, navCard, "Hide Greeting Stickers", "Suppress initial greeting sticker suggestions in newly opened chats",
            prefs.getBoolean(TelegramPreferences.PREF_HIDE_GREETING_STICKERS, true), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_HIDE_GREETING_STICKERS, it).commit()
        }
        addDivider(context, navCard, dividerCol)
        addSwitchRow(context, navCard, "Hide Translate Bar", "Hide the persistent translation banner bar in foreign-language chats",
            prefs.getBoolean(TelegramPreferences.PREF_HIDE_TRANSLATE_BAR, false), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_HIDE_TRANSLATE_BAR, it).commit()
        }
        addDivider(context, navCard, dividerCol)
        addSwitchRow(context, navCard, "Hide Blocked in Groups", "Completely hide messages from blocked contacts inside group conversations",
            prefs.getBoolean(TelegramPreferences.PREF_HIDE_BLOCKED_IN_GROUPS, false), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_HIDE_BLOCKED_IN_GROUPS, it).commit()
        }
        scrollContent.addView(navCard)

        // --- Section 5: Look & Feel ---
        addSectionHeader("Look & Feel")
        val lookCard = createCard()
        addSwitchRow(context, lookCard, "Unlock Local Premium", "Unlock local visual features: animated emoji effects, icons, and status badges",
            prefs.getBoolean(TelegramPreferences.PREF_UNLOCK_PREMIUM, true), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_UNLOCK_PREMIUM, it).commit()
        }
        addDivider(context, lookCard, dividerCol)
        addSwitchRow(context, lookCard, "Bypass Content Restrictions", "Allow copying, forwarding, and saving media from restricted channels",
            prefs.getBoolean(TelegramPreferences.PREF_BYPASS_RESTRICTIONS, true), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_BYPASS_RESTRICTIONS, it).commit()
        }
        addDivider(context, lookCard, dividerCol)
        addSwitchRow(context, lookCard, "AMOLED Pure Black", "Enforce true black (#000000) background in dark theme modes",
            prefs.getBoolean(TelegramPreferences.PREF_AMOLED_BLACK, false), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_AMOLED_BLACK, it).commit()
        }
        addDivider(context, lookCard, dividerCol)
        addSwitchRow(context, lookCard, "Use System Font", "Replace Telegram's bundled Roboto font with your device's system typeface",
            prefs.getBoolean(TelegramPreferences.PREF_USE_SYSTEM_FONT, false), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_USE_SYSTEM_FONT, it).commit()
        }
        addDivider(context, lookCard, dividerCol)
        addSwitchRow(context, lookCard, "Exact Numbers", "Display precise subscriber, view, and reaction counts instead of rounding",
            prefs.getBoolean(TelegramPreferences.PREF_EXACT_NUMBERS, true), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_EXACT_NUMBERS, it).commit()
        }
        addDivider(context, lookCard, dividerCol)
        addSwitchRow(context, lookCard, "Disable Double-Tap Reactions", "Prevent accidental reaction emojis when double-tapping chat messages",
            prefs.getBoolean(TelegramPreferences.PREF_DISABLE_DOUBLE_TAP_REACTIONS, false), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_DISABLE_DOUBLE_TAP_REACTIONS, it).commit()
        }
        addDivider(context, lookCard, dividerCol)
        addSwitchRow(context, lookCard, "Open in External Browser", "Open shared web links in your default system browser instead of internal WebView",
            prefs.getBoolean(TelegramPreferences.PREF_OPEN_EXTERNAL_LINKS, false), primaryCol, onSurface, onSurfaceVariant) {
            prefs.edit().putBoolean(TelegramPreferences.PREF_OPEN_EXTERNAL_LINKS, it).commit()
        }
        scrollContent.addView(lookCard)

        // Footer attribution
        val footerView = TextView(context).apply {
            text = "NexFusion for Telegram"
            textSize = 13f
            setTextColor(onSurfaceVariant)
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 16f), 0, dp(context, 24f))
        }
        scrollContent.addView(footerView)

        scrollView.addView(scrollContent)
        rootLayout.addView(scrollView)
        return rootLayout
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
    ) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = dp(context, 16f)
            val padV = dp(context, 12f)
            setPadding(padH, padV, padH, padV)
            background = RippleDrawable(ColorStateList.valueOf(0x1F888888), null, null)
            isClickable = true
            isFocusable = true
        }

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(context, 12f)
            }
        }

        val titleView = TextView(context).apply {
            text = title
            textSize = 16f
            setTextColor(onSurface)
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }
        textLayout.addView(titleView)

        if (summary.isNotEmpty()) {
            val summaryView = TextView(context).apply {
                text = summary
                textSize = 13f
                setTextColor(onSurfaceVariant)
                setPadding(0, dp(context, 2f), 0, 0)
                setLineSpacing(dp(context, 2f).toFloat(), 1f)
            }
            textLayout.addView(summaryView)
        }
        row.addView(textLayout)

        val switchView = Switch(context).apply {
            isChecked = initial
            isClickable = false
            isFocusable = false
            val trackOff = 0x33888888
            val trackOn = (primaryColor and 0x00FFFFFF) or 0x66000000
            val thumbOff = 0xFFCCCCCC.toInt()
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(trackOn, trackOff)
            )
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(primaryColor, thumbOff)
            )
        }
        row.addView(switchView)

        row.setOnClickListener {
            val newState = !switchView.isChecked
            switchView.isChecked = newState
            onToggle(newState)
        }

        container.addView(row)
    }

    private fun addDivider(context: Context, container: LinearLayout, dividerColor: Int) {
        val divider = View(context).apply {
            setBackgroundColor(dividerColor)
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

    class BackArrowDrawable(private val color: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@BackArrowDrawable.color
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val sz = b.width().coerceAtMost(b.height()) * 0.35f
            val path = Path().apply {
                moveTo(cx + sz * 0.5f, cy)
                lineTo(cx - sz * 0.5f, cy)
                moveTo(cx - sz * 0.1f, cy - sz * 0.5f)
                lineTo(cx - sz * 0.5f, cy)
                lineTo(cx - sz * 0.1f, cy + sz * 0.5f)
            }
            canvas.drawPath(path, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    class ResetIconDrawable(private val color: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@ResetIconDrawable.color
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@ResetIconDrawable.color
            style = Paint.Style.FILL
        }
        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = b.width().coerceAtMost(b.height()) * 0.28f
            val oval = RectF(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(oval, -60f, 270f, false, paint)
            val angleRad = Math.toRadians(-60.0)
            val ax = (cx + r * cos(angleRad)).toFloat()
            val ay = (cy + r * sin(angleRad)).toFloat()
            val arrow = Path().apply {
                moveTo(ax, ay)
                lineTo(ax - 10f, ay - 6f)
                lineTo(ax - 2f, ay - 14f)
                close()
            }
            canvas.drawPath(arrow, fillPaint)
        }
        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            fillPaint.alpha = alpha
        }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) {
            paint.colorFilter = cf
            fillPaint.colorFilter = cf
        }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    class SparkleDrawable(private val color: Int) : Drawable() {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@SparkleDrawable.color
            style = Paint.Style.FILL
        }
        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = b.width().coerceAtMost(b.height()) * 0.40f
            val path = Path().apply {
                moveTo(cx, cy - r)
                quadTo(cx, cy, cx + r, cy)
                quadTo(cx, cy, cx, cy + r)
                quadTo(cx, cy, cx - r, cy)
                quadTo(cx, cy, cx, cy - r)
                close()
            }
            canvas.drawPath(path, fillPaint)
        }
        override fun setAlpha(alpha: Int) { fillPaint.alpha = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { fillPaint.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}
