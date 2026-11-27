package io.github.a092devs.nexfusion.morphe.music.misc.settings

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
import io.github.a092devs.nexfusion.compat.XC_MethodReplacement
import io.github.a092devs.nexfusion.patches.R
import io.github.a092devs.nexfusion.morphe.music.shared.MusicActivityOnCreateFingerprint
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.BasePreferenceScreen
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.InputType
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.TextPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.a092devs.nexfusion.morphe.shared.settings.preferences
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceFragmentCompat_addPreferencesFromResource
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceFragmentCompat_getPreferenceScreen
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceGroup_findPreference
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.Preference_setTitle
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.applyCustomSettingsName
import io.github.a092devs.nexfusion.patch

val SettingsHook = patch(
    description = "Adds settings for Morphe to YouTube Music.",
) {
    val addPreferencesFromResource = ::PreferenceFragmentCompat_addPreferencesFromResource.method
    val getPreferenceScreenMethod = runCatching { ::PreferenceFragmentCompat_getPreferenceScreen.method }.getOrNull()
    val findPreferenceMethod = runCatching { ::PreferenceGroup_findPreference.method }.getOrNull()
    val setTitleMethod = runCatching { ::Preference_setTitle.method }.getOrNull()

    ::PreferenceFragmentCompat_setPreferencesFromResource.hookMethod {
        before { param ->
            val context = Utils.getContext()
            val preferencesName = context.resources.getResourceName(param.args[0] as Int)
            Logger.printDebug { "setPreferencesFromResource $preferencesName" }
            if (!preferencesName.endsWith("settings_headers")) return@before
            addPreferencesFromResource.invoke(param.thisObject, R.xml.yt_morphe_settings_music)
            if (getPreferenceScreenMethod != null && findPreferenceMethod != null && setTitleMethod != null) {
                applyCustomSettingsName(
                    param.thisObject,
                    getPreferenceScreenMethod,
                    findPreferenceMethod,
                    setTitleMethod,
                )
            }
            addPreferencesFromResource.invoke(param.thisObject, param.args[0])
            param.result = Unit
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
        SwitchPreference("morphe_open_system_share_sheet", summary = true),
    )

    PreferenceScreen.PLAYER.addPreferences(
        ListPreference(
            key = "morphe_video_quality_default_wifi",
            entriesKey = "morphe_video_quality_default_entries",
            entryValuesKey = "morphe_video_quality_default_entry_values"
        ),
        ListPreference(
            key = "morphe_video_quality_default_mobile",
            entriesKey = "morphe_video_quality_default_entries",
            entryValuesKey = "morphe_video_quality_default_entry_values"
        ),
        SwitchPreference("morphe_remember_video_quality_last_selected", summary = true),
        SwitchPreference("morphe_remember_video_quality_last_selected_toast", summary = true),
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
                val baseTheme = ResourceUtils.getStyleIdentifier("Theme.YouTubeMusic")
                if (baseTheme != 0) activity.setTheme(baseTheme)
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

    val cl = app.morphe.extension.shared.Utils.getContext()?.classLoader
        ?: io.github.a092devs.nexfusion.compat.XposedBridge::class.java.classLoader
    val musicExtensionPatches = listOf(
        "app.morphe.extension.music.patches.AndroidAutoPatch",
        "app.morphe.extension.music.patches.AppleMusicArtworkPatch",
        "app.morphe.extension.music.patches.AutoSongVersionPatch",
        "app.morphe.extension.music.patches.CastFixesPatch",
        "app.morphe.extension.music.patches.ChangeHeaderPatch",
        "app.morphe.extension.music.patches.ChangeMiniplayerColorPatch",
        "app.morphe.extension.music.patches.ChangeStartPagePatch",
        "app.morphe.extension.music.patches.CrossfadePatch",
        "app.morphe.extension.music.patches.DeviceFilesSearchPatch",
        "app.morphe.extension.music.patches.DisableAudioDuckingPatch",
        "app.morphe.extension.music.patches.DisableAutoCaptionsPatch",
        "app.morphe.extension.music.patches.DisableDislikeRedirectionPatch",
        "app.morphe.extension.music.patches.DisableSwipeToChangeTrackPatch",
        "app.morphe.extension.music.patches.DownloadsPatch",
        "app.morphe.extension.music.patches.EnableAudioVideoSwitchPatch",
        "app.morphe.extension.music.patches.EnableForcedMiniplayerPatch",
        "app.morphe.extension.music.patches.EnableSwipeToDismissMiniplayerPatch",
        "app.morphe.extension.music.patches.ForceOriginalAudioPatch",
        "app.morphe.extension.music.patches.ForcePortraitOrientationPatch",
        "app.morphe.extension.music.patches.HideAdsPatch",
        "app.morphe.extension.music.patches.HideFlyoutMenuComponentsPatch",
        "app.morphe.extension.music.patches.LoopVideoPatch",
        "app.morphe.extension.music.patches.MiniplayerPreviousNextButtonsPatch",
        "app.morphe.extension.music.patches.MusicMediaNotificationControlsPatch",
        "app.morphe.extension.music.patches.NavigationBarPatch",
        "app.morphe.extension.music.patches.OpenSystemShareSheetPatch",
        "app.morphe.extension.music.patches.PauseWhenMutedPatch",
        "app.morphe.extension.music.patches.PlaybackSpeedPatch",
        "app.morphe.extension.music.patches.PlaylistTagsPatch",
        "app.morphe.extension.music.patches.PlaylistTrackTapPatch",
        "app.morphe.extension.music.patches.QueueLikeFilterPatch",
        "app.morphe.extension.music.patches.RememberRepeatStatePatch",
        "app.morphe.extension.music.patches.RememberShuffleStatePatch",
        "app.morphe.extension.music.patches.RememberVideoQualityPatch",
        "app.morphe.extension.music.patches.ReturnYouTubeDislikePatch",
        "app.morphe.extension.music.patches.SettingsMenuFilterPatch",
        "app.morphe.extension.music.patches.SonosCastPatch",
        "app.morphe.extension.music.patches.SoundProofPatch",
        "app.morphe.extension.music.patches.StartupAnimationPatch",
        "app.morphe.extension.music.patches.ThemePatch",
        "app.morphe.extension.music.patches.VideoEffectMenuPatch",
        "app.morphe.extension.music.patches.lyrics.LyricsPatch",
        "app.morphe.extension.music.patches.scrobble.ScrobblePatch",
        "app.morphe.extension.music.patches.spoof.SpoofVideoStreamsPatch",
    )
    for (className in musicExtensionPatches) {
        runCatching {
            val cls = Class.forName(className, false, cl)
            val isPatchIncludedMethod = cls.getDeclaredMethod("isPatchIncluded")
            io.github.a092devs.nexfusion.compat.XposedBridge.hookMethod(
                isPatchIncludedMethod,
                XC_MethodReplacement.returnConstant(true)
            )
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
    val VIDEO = Screen(
        key = "morphe_settings_music_screen_10_video",
        summaryKey = null,
        icon = "@drawable/morphe_settings_screen_12_video",
        iconBold = "@drawable/morphe_settings_screen_12_video_bold",
        layout = "@layout/morphe_preference_with_icon"
    )

    override fun commit(screen: PreferenceScreenPreference) {
        preferences += screen
    }
}

