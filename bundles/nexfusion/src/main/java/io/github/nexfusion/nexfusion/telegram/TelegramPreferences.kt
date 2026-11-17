package io.github.nexfusion.nexfusion.telegram

import android.content.Context
import android.content.SharedPreferences

object TelegramPreferences {
    private const val PREFS_NAME = "nexfusion_telegram_prefs"
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun getContext(): Context? {
        if (appContext != null) return appContext
        return runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Context
        }.getOrNull()?.also { appContext = it }
    }

    fun get(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun get(): SharedPreferences? {
        val ctx = appContext ?: getContext() ?: return null
        return get(ctx)
    }

    // Privacy & Security
    const val PREF_GHOST_TYPING = "pref_ghost_typing"
    const val PREF_ANTI_DISAPPEARING_MEDIA = "pref_anti_disappearing_media"
    const val PREF_ANTI_SCREENSHOT_ALERT = "pref_anti_screenshot_alert"
    const val PREF_ALLOW_SCREENSHOTS = "pref_allow_screenshots"
    const val PREF_KEEP_DELETED_MESSAGES = "pref_keep_deleted_messages"
    const val PREF_HIDE_PHONE_NUMBER = "pref_hide_phone_number"
    const val PREF_REVEAL_SPOILERS = "pref_reveal_spoilers"

    // Ads & Telemetry
    const val PREF_HIDE_ADS = "pref_hide_ads"
    const val PREF_HIDE_SPONSORED_PROXY = "pref_hide_sponsored_proxy"
    const val PREF_HIDE_COMMERCE = "pref_hide_commerce"
    const val PREF_DISABLE_ANALYTICS = "pref_disable_analytics"
    const val PREF_DISABLE_UPDATE_CHECKS = "pref_disable_update_checks"

    // Media & Downloads
    const val PREF_FASTER_DOWNLOADS = "pref_faster_downloads"
    const val PREF_KEEP_VIDEOS_MUTED = "pref_keep_videos_muted"
    const val PREF_PLAY_VOICE_ONE_AT_A_TIME = "pref_play_voice_one_at_a_time"

    // Links & Previews
    const val PREF_STRIP_LINK_TRACKING = "pref_strip_link_tracking"
    const val PREF_FIX_LINK_PREVIEWS = "pref_fix_link_previews"
    const val PREF_OPEN_EXTERNAL_LINKS = "pref_open_external_links"
    const val PREF_EXACT_NUMBERS = "pref_exact_numbers"

    // Feed & Navigation
    const val PREF_HIDE_STORIES = "pref_hide_stories"
    const val PREF_DISABLE_ARCHIVE_PULL = "pref_disable_archive_pull"
    const val PREF_DISABLE_CHANNEL_PULL = "pref_disable_channel_pull"
    const val PREF_DISABLE_CHAT_SWIPE = "pref_disable_chat_swipe"
    const val PREF_DISABLE_DRAFT_PREVIEWS = "pref_disable_draft_previews"
    const val PREF_HIDE_KEYBOARD_ON_SCROLL = "pref_hide_keyboard_on_scroll"
    const val PREF_HIDE_GREETING_STICKERS = "pref_hide_greeting_stickers"
    const val PREF_HIDE_TRANSLATE_BAR = "pref_hide_translate_bar"
    const val PREF_HIDE_BLOCKED_IN_GROUPS = "pref_hide_blocked_in_groups"

    // Look & Feel
    const val PREF_UNLOCK_PREMIUM = "pref_unlock_premium"
    const val PREF_BYPASS_RESTRICTIONS = "pref_bypass_restrictions"
    const val PREF_AMOLED_BLACK = "pref_amoled_black"
    const val PREF_USE_SYSTEM_FONT = "pref_use_system_font"
    const val PREF_DISABLE_DOUBLE_TAP_REACTIONS = "pref_disable_double_tap_reactions"

    private fun resolve(context: Context?): SharedPreferences? =
        context?.let { get(it) } ?: get()

    fun isHideAdsEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_HIDE_ADS, true) ?: true
    fun isHideStoriesEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_HIDE_STORIES, true) ?: true
    fun isGhostTypingEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_GHOST_TYPING, false) ?: false
    fun isAntiDisappearingMediaEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_ANTI_DISAPPEARING_MEDIA, true) ?: true
    fun isAntiScreenshotAlertEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_ANTI_SCREENSHOT_ALERT, true) ?: true
    fun isAllowScreenshotsEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_ALLOW_SCREENSHOTS, true) ?: true
    fun isKeepDeletedMessagesEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_KEEP_DELETED_MESSAGES, true) ?: true
    fun isHidePhoneNumberEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_HIDE_PHONE_NUMBER, true) ?: true
    fun isRevealSpoilersEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_REVEAL_SPOILERS, false) ?: false

    fun isHideSponsoredProxyEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_HIDE_SPONSORED_PROXY, true) ?: true
    fun isHideCommerceEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_HIDE_COMMERCE, true) ?: true
    fun isDisableAnalyticsEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_DISABLE_ANALYTICS, true) ?: true
    fun isDisableUpdateChecksEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_DISABLE_UPDATE_CHECKS, true) ?: true

    fun isFasterDownloadsEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_FASTER_DOWNLOADS, true) ?: true
    fun isKeepVideosMutedEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_KEEP_VIDEOS_MUTED, false) ?: false
    fun isPlayVoiceOneAtATimeEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_PLAY_VOICE_ONE_AT_A_TIME, false) ?: false

    fun isStripLinkTrackingEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_STRIP_LINK_TRACKING, true) ?: true
    fun isFixLinkPreviewsEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_FIX_LINK_PREVIEWS, true) ?: true
    fun isOpenExternalLinksEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_OPEN_EXTERNAL_LINKS, false) ?: false
    fun isExactNumbersEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_EXACT_NUMBERS, true) ?: true

    fun isDisableArchivePullEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_DISABLE_ARCHIVE_PULL, false) ?: false
    fun isDisableChannelPullEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_DISABLE_CHANNEL_PULL, false) ?: false
    fun isDisableChatSwipeActionsEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_DISABLE_CHAT_SWIPE, false) ?: false
    fun isDisableDraftPreviewsEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_DISABLE_DRAFT_PREVIEWS, false) ?: false
    fun isHideKeyboardOnScrollEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_HIDE_KEYBOARD_ON_SCROLL, false) ?: false
    fun isHideGreetingStickersEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_HIDE_GREETING_STICKERS, true) ?: true
    fun isHideTranslateBarEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_HIDE_TRANSLATE_BAR, false) ?: false
    fun isHideBlockedInGroupsEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_HIDE_BLOCKED_IN_GROUPS, false) ?: false

    fun isUnlockPremiumEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_UNLOCK_PREMIUM, true) ?: true
    fun isBypassRestrictionsEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_BYPASS_RESTRICTIONS, true) ?: true
    fun isAmoledBlackEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_AMOLED_BLACK, false) ?: false
    fun isUseSystemFontEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_USE_SYSTEM_FONT, false) ?: false
    fun isDisableDoubleTapReactionsEnabled(context: Context? = null): Boolean = resolve(context)?.getBoolean(PREF_DISABLE_DOUBLE_TAP_REACTIONS, false) ?: false
}
