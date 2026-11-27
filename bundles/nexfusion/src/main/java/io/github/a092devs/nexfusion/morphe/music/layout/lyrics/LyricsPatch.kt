package io.github.a092devs.nexfusion.morphe.music.layout.lyrics

import app.morphe.extension.music.patches.components.LyricsPanelFilter
import app.morphe.extension.music.patches.lyrics.LyricsButtonPatch
import app.morphe.extension.music.patches.lyrics.session.LockScreenLyrics
import app.morphe.extension.music.patches.lyrics.session.MiniplayerLyrics
import app.morphe.extension.music.settings.preference.LyricsAiPromptPreference
import app.morphe.extension.music.settings.preference.LyricsClearCachePreference
import app.morphe.extension.music.settings.preference.LyricsCustomFontPreference
import app.morphe.extension.music.settings.preference.LyricsEncapsulationPreference
import app.morphe.extension.music.settings.preference.LyricsLineLimitsPreference
import app.morphe.extension.music.settings.preference.LyricsOrderedListPreference
import app.morphe.extension.music.settings.preference.LyricsTargetLanguagePreference
import app.morphe.extension.music.settings.preference.LyricsTrackFormatPreference
import app.morphe.extension.shared.settings.preference.SeekBarPreference
import io.github.a092devs.nexfusion.morphe.music.misc.litho.filter.LithoFilter
import io.github.a092devs.nexfusion.morphe.music.misc.mediasession.MediaSessionHook
import io.github.a092devs.nexfusion.morphe.music.misc.mediasession.setMetadataHooks
import io.github.a092devs.nexfusion.morphe.music.misc.mediasession.setMetadataWithSessionHooks
import io.github.a092devs.nexfusion.morphe.music.misc.mediasession.setPlaybackStateHooks
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.music.video.information.MusicVideoInformation
import io.github.a092devs.nexfusion.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.a092devs.nexfusion.morphe.shared.misc.proto.createElementProtoParserHookPatch
import io.github.a092devs.nexfusion.morphe.shared.misc.proto.hookElement
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.InputType
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceCategory
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.TextPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.screenInfoPreferenceCategory
import io.github.a092devs.nexfusion.patch

private val musicElementProtoParserHook = createElementProtoParserHookPatch()

val ThirdPartyLyrics = patch(
    name = "Third-party lyrics",
    description = "Adds an option to show synced lyrics with experience enhancement from 20+ providers in the lyrics panel.",
    use = false,
) {
    dependsOn(
        SettingsHook,
        LithoFilter,
        musicElementProtoParserHook,
        MediaSessionHook,
        MusicVideoInformation,
    )

    PreferenceScreen.LYRICS.addPreferences(
        SwitchPreference("morphe_music_lyrics_enabled", summary = true),
        PreferenceCategory(
            key = "morphe_music_lyrics_section_service",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                NonInteractivePreference(
                    key = "morphe_music_lyrics_source",
                    titleKey = null,
                    summaryKey = null,
                    tag = LyricsOrderedListPreference::class.java,
                    selectable = false,
                    dependency = "morphe_music_lyrics_enabled"
                ),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_source_advice",
                    titleKey = null,
                    summaryKey = "morphe_music_lyrics_source_advice_summary",
                    dependency = "morphe_music_lyrics_enabled"
                ),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_translation_provider",
                    summaryKey = null,
                    tag = LyricsOrderedListPreference::class.java,
                    selectable = false,
                    dependency = "morphe_music_lyrics_enabled"
                ),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_translation_language",
                    titleKey = null,
                    summaryKey = null,
                    tag = LyricsTargetLanguagePreference::class.java,
                    selectable = true,
                    dependency = "morphe_music_lyrics_enabled"
                ),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_romanization_provider",
                    summaryKey = null,
                    tag = LyricsOrderedListPreference::class.java,
                    selectable = false,
                    dependency = "morphe_music_lyrics_enabled"
                ),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_romanize_language",
                    titleKey = null,
                    summaryKey = null,
                    tag = LyricsTargetLanguagePreference::class.java,
                    selectable = true,
                    dependency = "morphe_music_lyrics_enabled"
                ),
                SwitchPreference("morphe_music_lyrics_allow_transliteration", summary = true),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_clear_cache",
                    summaryKey = "morphe_music_lyrics_cache_loading_summary",
                    tag = LyricsClearCachePreference::class.java,
                    selectable = true,
                    dependency = "morphe_music_lyrics_enabled"
                )
            )
        ),
        PreferenceCategory(
            key = "morphe_music_lyrics_section_font_animation",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                NonInteractivePreference(
                    key = "morphe_music_lyrics_text_size",
                    summaryKey = "morphe_music_lyrics_text_size_summary",
                    tag = SeekBarPreference::class.java,
                    selectable = true,
                    dependency = "morphe_music_lyrics_enabled"
                ),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_custom_font",
                    titleKey = "morphe_music_lyrics_custom_font_title",
                    summaryKey = "morphe_music_lyrics_custom_font_summary_default",
                    tag = LyricsCustomFontPreference::class.java,
                    selectable = true,
                    dependency = "morphe_music_lyrics_enabled"
                ),
                SwitchPreference("morphe_music_lyrics_word_sync", summary = true),
                SwitchPreference("morphe_music_lyrics_word_smooth_sweep", summary = true),
                SwitchPreference("morphe_music_lyrics_spring_scroll", summary = true),
                SwitchPreference("morphe_music_lyrics_relax_scroll", summary = true),
                SwitchPreference("morphe_music_lyrics_gap_dots", summary = true),
                SwitchPreference("morphe_music_lyrics_focus_spotlight", summary = true),
                SwitchPreference("morphe_music_lyrics_blur_inactive", summary = true),
                SwitchPreference("morphe_music_lyrics_line_scale", summary = true),
                SwitchPreference("morphe_music_lyrics_split_background", summary = true)
            )
        ),
        PreferenceCategory(
            key = "morphe_music_lyrics_section_text_formatting",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_music_lyrics_force_capitalize", summary = true),
                SwitchPreference("morphe_music_lyrics_strip_trailing_punctuation", summary = true),
                SwitchPreference("morphe_music_lyrics_han_form", summary = true),
                SwitchPreference("morphe_music_lyrics_swap_trans_roma", summary = true),
                ListPreference("morphe_music_lyrics_alignment_mode")
            )
        ),
        PreferenceCategory(
            key = "morphe_music_lyrics_section_visibility",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_music_lyrics_show_section", summary = true),
                SwitchPreference(
                    key = "morphe_music_lyrics_use_ai_section",
                    summary = true,
                    tag = LyricsAiPromptPreference::class.java
                ),
                SwitchPreference("morphe_music_lyrics_hide_played", summary = true),
                SwitchPreference("morphe_music_lyrics_hide_unplayed", summary = true),
                SwitchPreference("morphe_music_lyrics_hide_info", summary = true)
            )
        ),
        PreferenceCategory(
            key = "morphe_music_lyrics_section_panel",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_music_lyrics_tap_to_seek", summary = true),
                SwitchPreference("morphe_music_lyrics_long_press_share_mode", summary = true),
                SwitchPreference("morphe_music_lyrics_keep_screen_on", summary = true),
                SwitchPreference("morphe_music_lyrics_dynamic_background", summary = true),
                SwitchPreference("morphe_music_lyrics_dynamic_background_animated", summary = true),
                SwitchPreference("morphe_music_lyrics_show_copy_button", summary = true),
                SwitchPreference("morphe_music_lyrics_show_refresh_button", summary = true),
                SwitchPreference("morphe_music_lyrics_show_translate_button", summary = true),
                SwitchPreference("morphe_music_lyrics_show_romanize_button", summary = true),
                SwitchPreference("morphe_music_lyrics_show_current_button", summary = true),
                SwitchPreference("morphe_music_lyrics_show_fullscreen_button", summary = true)
            )
        ),
        PreferenceCategory(
            key = "morphe_music_lyrics_section_sync",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                NonInteractivePreference(
                    key = "morphe_music_lyrics_offset_ms",
                    summaryKey = "morphe_music_lyrics_offset_ms_summary",
                    tag = SeekBarPreference::class.java,
                    selectable = true,
                    dependency = "morphe_music_lyrics_enabled"
                ),
                SwitchPreference("morphe_music_lyrics_sb_matching", summary = true),
                SwitchPreference("morphe_music_lyrics_miniplayer", summary = true),
                SwitchPreference("morphe_music_lyrics_mediasession", summary = true),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_mini_track_format",
                    summaryKey = "morphe_music_lyrics_mini_track_format_menu_summary",
                    tag = LyricsTrackFormatPreference::class.java,
                    selectable = true,
                    dependency = "morphe_music_lyrics_enabled"
                ),
                TextPreference(
                    key = "morphe_music_lyrics_mini_encapsulation",
                    tag = LyricsEncapsulationPreference::class.java
                ),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_mini_char_limit",
                    titleKey = "morphe_music_lyrics_mini_line_limits_title",
                    summaryKey = "morphe_music_lyrics_mini_line_limits_summary",
                    tag = LyricsLineLimitsPreference::class.java,
                    selectable = true
                ),
                TextPreference(
                    key = "morphe_music_lyrics_mini_instrumental",
                    inputType = InputType.TEXT_MULTI_LINE
                ),
                NonInteractivePreference(
                    key = "morphe_music_lyrics_mini_next_offset_ms",
                    summaryKey = "morphe_music_lyrics_mini_next_offset_ms_summary",
                    tag = SeekBarPreference::class.java,
                    selectable = true,
                    dependency = "morphe_music_lyrics_enabled"
                )
            )
        ),
        PreferenceCategory(
            key = "morphe_settings_music_lyrics_metadata",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                TextPreference(
                    key = "morphe_music_lyrics_custom_regex",
                    inputType = InputType.TEXT_MULTI_LINE
                ),
                TextPreference(
                    key = "morphe_music_lyrics_text_filter",
                    inputType = InputType.TEXT_MULTI_LINE
                ),
                TextPreference(
                    key = "morphe_music_lyrics_credit_line_regex",
                    inputType = InputType.TEXT_MULTI_LINE
                )
            )
        ),
        screenInfoPreferenceCategory(
            key = "morphe_music_lyrics_section_about",
            preferences = setOf(
                NonInteractivePreference(
                    key = "morphe_music_lyrics_about",
                    titleKey = null,
                    dependency = "morphe_music_lyrics_enabled"
                )
            )
        )
    )

    addLithoFilter(LyricsPanelFilter())
    hookElement(LyricsButtonPatch::enableLyricsButton)

    setMetadataWithSessionHooks.add { session, metadata ->
        MiniplayerLyrics.onMediaSessionSetMetadata(session, metadata)
        LockScreenLyrics.onMediaSessionSetMetadata(session, metadata)
    }
    setPlaybackStateHooks.add {
        LockScreenLyrics.onPlaybackState(it)
    }
}
