package io.github.nexfusion.morphe.music.misc.settings

import android.app.Activity
import android.os.Bundle
import app.morphe.extension.music.settings.MusicActivityHook
import app.morphe.extension.music.settings.preference.AppLanguagePreference
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.shared.Utils
import app.morphe.extension.shared.settings.preference.ClearNewSettingsIndicatorPreference
import app.morphe.extension.shared.settings.preference.ImportExportPreference
import app.morphe.extension.shared.settings.preference.SeekBarPreference
import app.morphe.extension.shared.settings.preference.SettingsNamePreference
import app.morphe.extension.shared.settings.preference.SortedListPreference
import app.morphe.extension.shared.settings.preference.about.MorpheAboutPreference
import io.github.nexfusion.compat.XC_MethodReplacement
import io.github.nexfusion.patches.R
import io.github.nexfusion.morphe.music.shared.MusicActivityOnCreateFingerprint
import io.github.nexfusion.morphe.shared.misc.settings.preference.BasePreferenceScreen
import io.github.nexfusion.morphe.shared.misc.settings.preference.InputType
import io.github.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.TextPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.nexfusion.morphe.shared.settings.preferences
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceFragmentCompat_addPreferencesFromResource
import io.github.nexfusion.patch

val SettingsHook = patch(
    description = "Adds settings for Morphe to YouTube Music.",
) {
    val addPreferencesFromResource = ::PreferenceFragmentCompat_addPreferencesFromResource.method

    ::PreferenceFragmentCompat_setPreferencesFromResource.hookMethod {
        before {
            val context = Utils.getContext()
            val preferencesName = context.resources.getResourceName(it.args[0] as Int)
            Logger.printDebug { "setPreferencesFromResource $preferencesName" }
            if (!preferencesName.endsWith("settings_headers")) return@before
            addPreferencesFromResource(it.thisObject, R.xml.yt_morphe_settings_music)
            addPreferencesFromResource(it.thisObject, it.args[0])
            it.result = Unit
        }
    }

    // Add an "About" preference to the top.
    preferences += NonInteractivePreference(
        key = "morphe_settings_music_screen_0_about",
        summaryKey = null,
        icon = "@drawable/morphe_settings_screen_00_about",
        iconBold = "@drawable/morphe_settings_screen_00_about_bold",
        layout = "@layout/morphe_preference_with_icon",
        tag = MorpheAboutPreference::class.java,
        selectable = true
    )

    PreferenceScreen.GENERAL.addPreferences(
        SwitchPreference("morphe_settings_search_history"),
        SwitchPreference("morphe_show_menu_icons"),
        ListPreference(
            key = "morphe_settings_name",
            tag = SettingsNamePreference::class.java,
        ),
        SwitchPreference("morphe_music_hide_get_premium_label"),
        SwitchPreference("morphe_music_hide_music_premium_promotions"),
        SwitchPreference("morphe_music_force_portrait_orientation"),
        SwitchPreference("morphe_music_hide_cast_button"),
        SwitchPreference("morphe_music_hide_filter_bar"),
        SwitchPreference("morphe_music_hide_history_button"),
        SwitchPreference("morphe_music_hide_search_button"),
        SwitchPreference("morphe_music_hide_notification_button"),
    )

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_ab_loop", summary = true),
        SwitchPreference("morphe_music_disable_audio_ducking", summary = true),
        SwitchPreference("morphe_music_exclusive_audio_playback"),
        SwitchPreference("morphe_music_background_playback"),
        SwitchPreference("morphe_crossfade"),
        SwitchPreference("morphe_force_original_audio"),
        SwitchPreference("morphe_disable_drc_audio"),
        SwitchPreference("morphe_remember_repeat_state"),
        SwitchPreference("morphe_remember_shuffle_state"),
        SwitchPreference("morphe_playlist_track_tap"),
        SwitchPreference("morphe_remove_viewer_discretion_dialog"),
        SwitchPreference("morphe_disable_dislike_redirection"),
        SwitchPreference("morphe_music_auto_song_version", summary = true),
        SwitchPreference("morphe_music_enable_audio_video_switch", summary = true),
    )

    PreferenceScreen.FEED.addPreferences(
        SwitchPreference("morphe_music_hide_explore_shelf"),
        SwitchPreference("morphe_music_hide_grid_shelves"),
        SwitchPreference("morphe_music_hide_horizontal_shelves"),
        SwitchPreference("morphe_music_hide_list_shelves"),
        SwitchPreference("morphe_music_hide_new_from_shelf"),
        SwitchPreference("morphe_music_hide_playlist_shelves"),
        SwitchPreference("morphe_music_hide_speed_dial_shelf"),
        SwitchPreference("morphe_music_hide_you_may_also_like_section"),
        SwitchPreference("morphe_music_hide_your_downloads_shelf"),
    )

    PreferenceScreen.LYRICS.addPreferences(
        SwitchPreference("morphe_music_lyrics"),
    )

    PreferenceScreen.SCROBBLING.addPreferences(
        SwitchPreference("morphe_music_scrobbling"),
    )

    PreferenceScreen.RETURN_YOUTUBE_DISLIKE.addPreferences(
        SwitchPreference("morphe_ryd_enabled"),
    )

    PreferenceScreen.SPONSORBLOCK.addPreferences(
        SwitchPreference("morphe_sb_enabled"),
    )

    PreferenceScreen.MISC.addPreferences(
        noTitleUnsortedPreferenceCategory(
            NonInteractivePreference(
                key = "morphe_settings_new_indicator_days",
                tag = SeekBarPreference::class.java,
                selectable = true,
            ),
            NonInteractivePreference(
                key = "morphe_settings_new_indicator_clear",
                tag = ClearNewSettingsIndicatorPreference::class.java,
                selectable = true,
            ),
        ),
        SwitchPreference("morphe_soundproof"),
        SwitchPreference("morphe_music_jam_enabled"),
        TextPreference(
            key = null,
            titleKey = "morphe_pref_import_export_title",
            summaryKey = "morphe_pref_import_export_summary",
            inputType = InputType.TEXT_MULTI_LINE,
            tag = ImportExportPreference::class.java,
        ),
        ListPreference(
            key = "morphe_language",
            tag = SortedListPreference::class.java,
        ),
        ListPreference("morphe_patches_update_notice"),
        NonInteractivePreference(
            key = "morphe_music_app_language",
            summaryKey = null,
            tag = AppLanguagePreference::class.java,
            selectable = true,
        ),
    )

    PreferenceScreen.close()

    runCatching {
        val superOnCreateMethod =
            Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java)

        val superOnCreate = xposed.getInvoker(superOnCreateMethod)

        ::googleApiActivityFingerprint.hookMethod {
            before { param ->
                val activity = param.thisObject as Activity
                activity.setTheme(ResourceUtils.getStyleIdentifier("@style/Theme.YouTubeMusic"))
                MusicActivityHook.initialize(activity)
                activity.theme.applyStyle(R.style.ListDividerNull, true)
                superOnCreate.invokeSpecial(param.thisObject, *param.args)
                param.result = Unit
            }
        }
    }

    runCatching {
        // Remove other methods as they will break as the onCreate method is modified above.
        ::googleApiActivityNOTonCreate.dexMethodList.forEach {
            if (it.returnTypeName == "void") it.hookMethod(XC_MethodReplacement.DO_NOTHING)
        }
    }

    runCatching {
        MusicActivityOnCreateFingerprint.hookMethod {
            before {
                MusicActivityHook.onMainActivityCreate()
            }
        }
    }
}

object PreferenceScreen : BasePreferenceScreen() {
    val ADS = Screen(
        key = "morphe_settings_music_screen_1_ads",
        summaryKey = null,
        icon = "@drawable/morphe_settings_screen_01_ads",
        iconBold = "@drawable/morphe_settings_screen_01_ads_bold",
        layout = "@layout/morphe_preference_with_icon"
    )
    val FEED = Screen(
        key = "morphe_settings_music_screen_2_feed",
        summaryKey = null,
        icon = "@drawable/morphe_settings_screen_03_feed",
        iconBold = "@drawable/morphe_settings_screen_03_feed_bold",
        layout = "@layout/morphe_preference_with_icon"
    )
    val GENERAL = Screen(
        key = "morphe_settings_music_screen_3_general",
        summaryKey = null,
        icon = "@drawable/morphe_settings_screen_04_general",
        iconBold = "@drawable/morphe_settings_screen_04_general_bold",
        layout = "@layout/morphe_preference_with_icon"
    )
    val PLAYER = Screen(
        key = "morphe_settings_music_screen_4_player",
        summaryKey = null,
        icon = "@drawable/morphe_settings_screen_05_player",
        iconBold = "@drawable/morphe_settings_screen_05_player_bold",
        layout = "@layout/morphe_preference_with_icon"
    )
    val LYRICS = Screen(
        key = "morphe_settings_music_screen_5_lyrics",
        summaryKey = null,
        icon = "@drawable/morphe_settings_screen_14_lyrics",
        iconBold = "@drawable/morphe_settings_screen_14_lyrics_bold",
        layout = "@layout/morphe_preference_with_icon",
    )
    val SCROBBLING = Screen(
        key = "morphe_settings_music_screen_6_scrobbling",
        summaryKey = null,
        icon = "@drawable/morphe_settings_screen_13_scrobbling",
        iconBold = "@drawable/morphe_settings_screen_13_scrobbling_bold",
        layout = "@layout/morphe_preference_with_icon",
    )
    val RETURN_YOUTUBE_DISLIKE = Screen(
        key = "morphe_settings_music_screen_7_return_youtube_dislike",
        summaryKey = null,
        icon = "@drawable/morphe_settings_screen_09_return_youtube_dislike",
        iconBold = "@drawable/morphe_settings_screen_09_return_youtube_dislike_bold",
        layout = "@layout/morphe_preference_with_icon",
    )
    val SPONSORBLOCK = Screen(
        key = "morphe_settings_music_screen_8_sponsorblock",
        summaryKey = null,
        icon = "@drawable/morphe_settings_screen_10_sponsorblock",
        iconBold = "@drawable/morphe_settings_screen_10_sponsorblock_bold",
        layout = "@layout/morphe_preference_with_icon",
    )
    val MISC = Screen(
        key = "morphe_settings_music_screen_9_misc",
        summaryKey = null,
        icon = "@drawable/morphe_settings_screen_11_misc",
        iconBold = "@drawable/morphe_settings_screen_11_misc_bold",
        layout = "@layout/morphe_preference_with_icon"
    )

    override fun commit(screen: PreferenceScreenPreference) {
        preferences += screen
    }
}

