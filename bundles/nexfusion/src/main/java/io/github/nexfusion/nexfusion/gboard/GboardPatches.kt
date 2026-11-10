package io.github.nexfusion.nexfusion.gboard

import android.widget.TextView
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.patch

// Ported from jasonwu1994/Gboard-patches (GPL-3.0): each patch mirrors the matching
// *Policy.java under extensions/extension/src/main/java/dev/jason/gboardpatches/extension/,
// installed here as flag overrides on the shared getter hook (see GboardFlagHook.kt).
//
// All of these features already exist in Gboard behind flags the server rollout leaves off; the
// patches only substitute the stock value, like the upstream flag-family patches do.

val GboardCursorTrackpad = patch(
    name = "Cursor trackpad",
    description = "Enables free-cursor mode: slide on the spacebar to move the text cursor.",
) {
    gboardFlagOverrides(
        "free_cursor" to FlagOverride.promote(),
        "free_cursor_lock_mode" to FlagOverride.promote(),
    )
}

val GboardQuickInsert = patch(
    name = "Quick insert",
    description = "Enables Gboard's super-insert (quick insert) suggestions row with dynamic multi-view.",
    use = false,
) {
    gboardFlagOverrides(
        "super_insert" to FlagOverride.forceBoolean(),
        "super_insert_vk" to FlagOverride.forceBoolean(),
        "super_insert_enable_dynamic_multi_view" to FlagOverride.forceBoolean(),
        "super_insert_contacts" to FlagOverride.forceBoolean(),
        "super_insert_clipboard_query_results" to FlagOverride.forceBoolean(),
        "super_insert_expression_query_results" to FlagOverride.forceBoolean(),
        "super_insert_local_image_results" to FlagOverride.forceBoolean(),
        "super_insert_below_cursor" to FlagOverride.forceBoolean(),
        "enable_si_shortcut_keys" to FlagOverride.forceBoolean(),
        "stop_deferring_access_points_tasks" to FlagOverride.forceBoolean(),
    )
}

val GboardEmojiSize = patch(
    name = "Emoji size setting",
    description = "Enables the emoji size slider in Gboard's emoji panel.",
) {
    gboardFlagOverrides("emoji_scale_supported" to FlagOverride.promote())
}

val GboardAccessPointsMenu = patch(
    name = "Access points menu redesign",
    description = "Uses the redesigned toolbar access-points menu.",
) {
    gboardFlagOverrides("enable_access_points_menu_redesign" to FlagOverride.forceBoolean())
}

val GboardBluetoothMicrophone = patch(
    name = "Bluetooth microphone setting",
    description = "Shows the setting that lets voice typing use a Bluetooth microphone.",
) {
    gboardFlagOverrides("enable_use_bluetooth_setting" to FlagOverride.forceBoolean())
}

val GboardCloseProactiveSuggestions = patch(
    name = "Close proactive suggestions",
    description = "Adds the access point that closes the proactive suggestions row.",
    use = false,
) {
    gboardFlagOverrides("enable_close_proactive_suggestions_access_point" to FlagOverride.forceBoolean())
}

val GboardAccessPointCount = patch(
    name = "Access point count",
    description = "Fixes the toolbar access-point count at 6 (upstream's default; range 3-8).",
    use = false,
) {
    gboardFlagOverrides(
        "config_max_access_points" to FlagOverride.forceLong(6L),
        "config_default_access_points_num_on_bar" to FlagOverride.forceLong(6L),
    )
}

val GboardClipboardCharacterLimit = patch(
    name = "Clipboard character limit",
    description = "Raises the clipboard item text limit to 20000 characters (upstream's default).",
) {
    gboardFlagOverrides("text_clip_item_char_limit" to FlagOverride.forceLong(20_000L))
}

val GboardFlowMode = patch(
    name = "Flow mode",
    description = "Enables the llm_pc flow-mode keys for every language.",
) {
    gboardFlagOverrides(
        "enable_llm_pc" to FlagOverride.forceTrue(),
        "enable_llm_pc_flow_mode" to FlagOverride.forceTrue(),
    )
}

val GboardSettingsMenu = patch(
    name = "Advanced settings menu",
    description = "Adds the Material 3 'Advanced settings' screen at the bottom of Gboard settings, and cleans up promotional links.",
) {
    gboardFlagOverrides(
        "emojify_app_allowlist" to FlagOverride.forceString("*"),
        "fast_access_bar_app_whitelist" to FlagOverride.forceString("*"),
        "proofread_supported_apps" to FlagOverride.forceString("*"),
    )
    GboardSettingsHook.install(classLoader)
}

val GboardRamblerVoiceTyping = patch(
    name = "Gemini Rambler natural voice typing",
    description = "Enables Gemini-powered Rambler natural voice typing with real-time speech cleanup, thought completion, auto-punctuation, multilingual fluidity, and battery-friendly lifecycle.",
) {
    gboardFlagOverrides(
        // Rambler core flags
        "show_rambler_dict_settings" to FlagOverride.forceTrue(),
        "ad_activation_type" to FlagOverride.forceLong(2L),
        "enable_agentic_dictation" to FlagOverride.forceTrue(),
        "config_agentic_dictation" to FlagOverride.forceTrue(),
        "enable_jetson_in_toolbar" to FlagOverride.forceTrue(),
        "filter_rambler_contributed_input_view_session" to FlagOverride.forceTrue(),
        "enable_rambler_toolbar_at_cursor_position" to FlagOverride.forceFalse(),
        "enable_rambler_al_toolbar" to FlagOverride.forceFalse(),
        "enable_rambler_al_toolbar_overflow_menu" to FlagOverride.forceFalse(),
        "enable_hide_rambler_al_idling_state" to FlagOverride.forceTrue(),
        "enable_rambler_al_show_hide_new_animation" to FlagOverride.forceFalse(),
        "enable_rambler_al_teleporting_animation" to FlagOverride.forceFalse(),
        "rambler_motion_with_gradient_color" to FlagOverride.forceTrue(),
        "agentic_dictation_spot_light2" to FlagOverride.forceTrue(),
        "agentic_dictation_new_gi_motion" to FlagOverride.forceTrue(),
        "enable_jetson_s3_quota_experiment_override" to FlagOverride.forceTrue(),
        "agentic_dictation_disable_pre_session_quota_check" to FlagOverride.forceTrue(),
        "enable_pcm_audio_caching_for_offline_fallback" to FlagOverride.forceTrue(),
        "agentic_dictation_enable_seamless_fallback" to FlagOverride.forceTrue(),
        "agentic_dictation_enable_proactive_parallel_asr" to FlagOverride.forceTrue(),

        // Suppress voice donation, promo nag banners, and internal dogfood feedback
        "rambler_al_in_situ_edu_tooltip" to FlagOverride.forceFalse(),
        "agentic_dictation_enable_promo_banner" to FlagOverride.forceFalse(),
        "agentic_dictation_promo_banner" to FlagOverride.forceFalse(),
        "agentic_dictation_promo_banner_max_show_count" to FlagOverride.forceLong(0L),
        "pref_key_always_show_agentic_dictation_promo_banner" to FlagOverride.forceFalse(),
        "pref_key_always_show_agentic_dictation_onboarding" to FlagOverride.forceFalse(),
        "agentic_dictation_show_onboarding" to FlagOverride.forceFalse(),
        "agentic_dictation_edits_tooltip" to FlagOverride.forceFalse(),
        "voice_donation_promo_banner" to FlagOverride.forceFalse(),
        "dictation_promo_banner" to FlagOverride.forceFalse(),
        "dictation_promo_banner_max_show_count" to FlagOverride.forceLong(0L),

        // Advanced Voice / NGA flags
        "enable_nga" to FlagOverride.forceTrue(),
        "enable_advanced_features_in_consolidated_sd_stack" to FlagOverride.forceTrue(),
        "enable_dictation_feature_split_install" to FlagOverride.forceTrue(),
        "handle_fallback_inside_sd_stack" to FlagOverride.forceTrue(),
        "enable_sticky_mic_background" to FlagOverride.forceTrue(),
        "immediately_end_dictation_on_keyboard_hidden" to FlagOverride.forceTrue(),
        "enable_soda_longform_experiment" to FlagOverride.forceTrue(),
    )
    enableGboardPreferences(
        booleans = mapOf(
            "enable_jetson" to true,
            "pref_key_auto_punctuation" to true,
            "pref_key_enable_auto_punctuation" to true,
            "pref_key_use_google_voice_typing" to true,
            "pref_advanced_voice_typing_enabled" to true,
            "voice_donation_opt_out" to true,
            "has_shown_voice_donation_promo" to true,
            "pref_key_always_show_agentic_dictation_promo_banner" to false,
        )
    )
    GboardRamblerHook.install(classLoader)
}

val GboardGrammarChecker = patch(
    name = "Grammar checker",
    description = "Enables Gboard's on-device grammar checker.",
) {
    gboardFlagOverrides("enable_grammar_checker" to FlagOverride.forceTrue())
}

val GboardInlineSuggestions = patch(
    name = "Inline suggestions",
    description = "Enables client-side inline suggestions.",
) {
    gboardFlagOverrides("enable_inline_suggestions_on_client_side" to FlagOverride.forceTrue())
}

val GboardKeyShapeSelection = patch(
    name = "Key shape selection",
    description = "Enables the extra key shapes in the theme editor (more_pill_keys).",
) {
    gboardFlagOverrides("more_pill_keys" to FlagOverride.forceTrue())
}

val GboardAiWritingTools = patch(
    name = "AI Writing Tools",
    description = "Enables Gboard's Gemini-powered AI writing assistant, Proofread, and style rephrasing across apps.",
) {
    gboardFlagOverrides(
        // Core writing tools & toolbar access point flags
        "jarvis" to FlagOverride.forceTrue(),
        "writing_tools" to FlagOverride.forceTrue(),
        "writing_tools_v2" to FlagOverride.forceTrue(),
        "enable_writing_tools_v2" to FlagOverride.forceTrue(),
        "writing_tools_v2_on_toolbar" to FlagOverride.forceTrue(),
        "enable_writing_tools_v2_on_toolbar" to FlagOverride.forceTrue(),
        "writing_tools_cooperative_mode" to FlagOverride.forceTrue(),
        "enable_writing_tools_cooperative_mode" to FlagOverride.forceTrue(),
        "writing_tools_cooperative_mode_min_words_to_show_access_point" to FlagOverride.forceLong(0L),
        "writing_tools_cooperative_mode_min_shown_times_before_triggering_tooltip" to FlagOverride.forceLong(0L),
        "writing_tools_cooperative_mode_min_shown_cumulative_time_before_triggering_tooltip_secs" to FlagOverride.forceLong(0L),
        "writing_tools_enable_stable_entrance" to FlagOverride.forceTrue(),
        "enable_writing_tools_replace_button" to FlagOverride.forceTrue(),
        "writing_tools_replace_button" to FlagOverride.forceTrue(),
        "writing_tools_enable_streaming_ui" to FlagOverride.forceTrue(),
        "writing_tools_suggest_style" to FlagOverride.forceTrue(),
        "enable_writing_tools_suggest_style" to FlagOverride.forceTrue(),
        "writing_tools_my_style" to FlagOverride.forceTrue(),
        "enable_writing_tools_my_style" to FlagOverride.forceTrue(),
        "writing_tools_my_style_plus" to FlagOverride.forceTrue(),
        "enable_writing_tools_my_style_plus" to FlagOverride.forceTrue(),
        "writing_tools_for_minors" to FlagOverride.forceTrue(),
        "enable_writing_tools_for_minors" to FlagOverride.forceTrue(),
        "writing_tools_voice_commands" to FlagOverride.forceTrue(),
        "enable_writing_tools_voice_commands" to FlagOverride.forceTrue(),
        "writing_tools_prompts" to FlagOverride.forceTrue(),
        "writing_tools_suggestions" to FlagOverride.forceTrue(),
        "writing_tools_4c" to FlagOverride.forceTrue(),
        "writing_tools_context_opt_in" to FlagOverride.forceTrue(),
        "writing_tools_backend_type" to FlagOverride.forceLong(1L),
        "writing_tools_v2_backend_type" to FlagOverride.forceLong(1L),
        "writing_tools_pi_override" to FlagOverride.forceTrue(),
        "writing_tools_v2_allow_empty_user_instruction" to FlagOverride.forceTrue(),
        "writing_tools_v2_show_predefined_prompts" to FlagOverride.forceTrue(),
        "writing_tools_v2_display_instruction_suggestions_in_zero_state" to FlagOverride.forceTrue(),
        "writing_tools_v2_enable_suggested_instructions" to FlagOverride.forceTrue(),
        "writing_tools_v2_enable_suggested_instructions_in_draft_response" to FlagOverride.forceTrue(),

        // Writing helper & stylization
        "writing_helper" to FlagOverride.forceTrue(),
        "writing_helper_on_selected_text" to FlagOverride.forceTrue(),
        "writing_helper_enable_access_point_animation" to FlagOverride.forceTrue(),
        "writing_helper_enable_text_stylization_internal" to FlagOverride.forceTrue(),
        "writing_helper_chip_in_spellchecker" to FlagOverride.forceTrue(),
        "writing_helper_server_target" to FlagOverride.forceString("staging-gboard-imagen-pa.sandbox.googleapis.com"),
        "gboard_imagen_api_host" to FlagOverride.forceString("staging-gboard-imagen-pa.sandbox.googleapis.com"),
        "writing_helper_model_version" to FlagOverride.forceString("IMAGE_AND_TEXT_GENERATION"),
        "writing_helper_text_stylization_model_version" to FlagOverride.forceString("IMAGE_AND_TEXT_GENERATION"),
        "writing_helper_supported_language_tags" to FlagOverride.forceString("*"),
        "llm_pc_supported_language_tags" to FlagOverride.forceString("*"),
        "writing_tools_supported_locales" to FlagOverride.forceString("*"),
        "proofread_supported_locales" to FlagOverride.forceString("*"),
        "proofread_supported_apps" to FlagOverride.forceString("*"),
        "enable_device_intelligence" to FlagOverride.forceTrue(),
        "enable_access_point_keyboard" to FlagOverride.forceTrue(),
        "enable_nga_lab_modeless_smartedit" to FlagOverride.forceTrue(),
        "enable_on_device_proofread" to FlagOverride.forceTrue(),
        "writing_tools_enable_hybrid" to FlagOverride.forceTrue(),
        "nga_lab_modeless_smartedit_regex_version" to FlagOverride.forceString("v3"),

        // Proofread & Fix It flags
        "config_proofread" to FlagOverride.forceTrue(),
        "proofread" to FlagOverride.forceTrue(),
        "proofread_config" to FlagOverride.forceTrue(),
        "proofread_chip" to FlagOverride.forceTrue(),
        "proofread_lock" to FlagOverride.forceTrue(),
        "proofread_suggestion" to FlagOverride.forceTrue(),
        "proofreader" to FlagOverride.forceTrue(),

        // Access point order overrides ensuring jarvis (AI writing tools) appears on the keyboard toolbar
        "config_access_points_order" to FlagOverride.forceString("jarvis;clipboard;translate;settings;gif_search;sticker;search;one_handed;textediting;floating_keyboard"),
        "access_points_order" to FlagOverride.forceString("jarvis;clipboard;translate;settings;gif_search;sticker;search;one_handed;textediting;floating_keyboard"),
        "access_points_order_for_new_user_on_phone" to FlagOverride.forceString("jarvis;clipboard;translate;settings;gif_search;sticker;search;one_handed;textediting;floating_keyboard"),
        "config_default_access_points_num_on_bar" to FlagOverride.forceLong(6L),
        "config_max_access_points" to FlagOverride.forceLong(6L),
    )
    enableGboardWritingToolsPreference()
}

val GboardRoundedKeycaps = patch(
    name = "Rounded keycaps (Belka UI)",
    description = "Enables Material 3 Belka rounded pill keycaps, circular layout styling, and persistent preference.",
) {
    gboardFlagOverrides(
        "belka_rounded_keyboard" to FlagOverride.forceTrue(),
        "belka_ui" to FlagOverride.forceTrue(),
        "belka_background" to FlagOverride.forceTrue(),
        "more_pill_keys" to FlagOverride.forceTrue(),
        "enable_rounded_keys" to FlagOverride.forceTrue(),
        "enable_rounded_key_by_default" to FlagOverride.forceTrue(),
        "semi_rounded_key_radius_max_dp" to FlagOverride.forceLong(20L),
        "rounded_key_banner" to FlagOverride.forceTrue(),
        "rounded_key_prompt" to FlagOverride.forceTrue(),
    )
    enableGboardRoundedKeysPreference()
}

val GboardScanTextOcr = patch(
    name = "Scan text (Camera OCR)",
    description = "Unlocks the Camera OCR scan text viewfinder in the Gboard toolbar to extract physical text.",
    use = false,
) {
    gboardFlagOverrides(
        "enable_ocr" to FlagOverride.forceTrue(),
        "config_ocr" to FlagOverride.forceTrue(),
        "ocr_password_allowlist" to FlagOverride.forceTrue(),
    )
}

val GboardScreenshotChips = patch(
    name = "Screenshot suggestion chips",
    description = "Shows floating suggestion chips for recently captured screenshots and clipboard images.",
    use = false,
) {
    gboardFlagOverrides(
        "enable_screenshot_in_clipboard" to FlagOverride.forceTrue(),
        "enable_clipboard_image_suggestion" to FlagOverride.forceTrue(),
        "enable_screenshot_notification_flow" to FlagOverride.forceTrue(),
        "enable_screenshot_chip" to FlagOverride.forceTrue(),
        "enable_clipboard_recent_screenshot" to FlagOverride.forceTrue(),
        "clipboard_screenshot_enabled_at_least_once" to FlagOverride.forceTrue(),
        "should_auto_enable_enable_screenshot_in_clipboard" to FlagOverride.forceTrue(),
        "enable_extended_clipboard_history" to FlagOverride.forceTrue(),
        "enable_system_clipboard_direct_paste_for_quick_paste" to FlagOverride.forceTrue(),
        "writing_tools_v2_enable_screenshot_context" to FlagOverride.forceTrue(),
        "enable_auto_paste_chips" to FlagOverride.forceTrue(),
        "pref_key_enable_clipboard_suggestion" to FlagOverride.forceTrue(),
    )
    enableGboardScreenshotPreferences()
}

val GboardEmojiPaletteV2 = patch(
    name = "Emoji Palette V2",
    description = "Enables the redesigned emoji drawer layout with dedicated frequent/recent emoji tabs.",
) {
    gboardFlagOverrides(
        "emoji_palette_v2" to FlagOverride.promote(),
        "enable_frequent_emoji_recent_tab" to FlagOverride.promote(),
        "enable_emoji_frequent_recent_switch_option" to FlagOverride.promote(),
    )
}

val GboardSmartCompose = patch(
    name = "Smart Compose autocompletion",
    description = "Enables in-line predictive text completion and ghost text across all input fields.",
    use = false,
) {
    gboardFlagOverrides(
        "enable_smart_compose" to FlagOverride.promote(),
        "enable_inline_suggestions_on_all_fields" to FlagOverride.promote(),
        "enable_inline_completion" to FlagOverride.promote(),
        "enable_delight5_smart_compose" to FlagOverride.promote(),
        "smart_compose_status" to FlagOverride.forceLong(1L),
        "enable_inline_suggestions_on_client_side" to FlagOverride.promote(),
    )
}

val GboardSpellCheckerChips = patch(
    name = "Spell checker suggestion chips",
    description = "Forces modern inline spell checker suggestion chips and grammar highlight strip on Android 12+.",
) {
    gboardFlagOverrides(
        "enable_spellchecker_chips_ui" to FlagOverride.forceTrue(),
        "enable_spellchecker_chips_ui_web_view" to FlagOverride.forceTrue(),
        "spellchecker_enable_chips_ui" to FlagOverride.forceTrue(),
        "spellchecker_enable_chips_ui_web_view" to FlagOverride.forceTrue(),
        "enable_spell_checker_extension" to FlagOverride.forceTrue(),
        "writing_helper_chip_in_spellchecker" to FlagOverride.forceTrue(),
        "enable_spell_checker_highlight_on_web_input" to FlagOverride.forceTrue(),
        "enable_spell_checker_training_cache" to FlagOverride.forceTrue(),
    )
    GboardRamblerHook.install(classLoader)
}

val GboardEmogen = patch(
    name = "Emogen (GenAI Emoji Maker)",
    description = "Enables Gboard's generative AI emoji maker and access point.",
    use = false,
) {
    gboardFlagOverrides(
        "enable_emogen" to FlagOverride.forceBoolean(),
        "emogen_entry_point" to FlagOverride.forceBoolean(),
        "emogen_feature_enabled" to FlagOverride.forceBoolean(),
        "emogen_in_access_points" to FlagOverride.forceBoolean(),
        "gboard_imagen_api_host" to FlagOverride.forceString("staging-gboard-imagen-pa.sandbox.googleapis.com"),
    )
}

val GboardSignToText = patch(
    name = "Sign-to-Text (Signboard ASL)",
    description = "Enables Sign-to-Text camera translation for American Sign Language.",
    use = false,
) {
    gboardFlagOverrides(
        "enable_signboard" to FlagOverride.forceBoolean(),
        "signboard_prompt_manual_trigger" to FlagOverride.forceBoolean(),
        "on_demand_feature_split" to FlagOverride.forceBoolean(),
        "auto_show_signboard" to FlagOverride.forceBoolean(),
        "signboard_auto_show_prompt" to FlagOverride.forceBoolean(),
        "signboard_download" to FlagOverride.forceBoolean(),
        "signboard_download_dialog" to FlagOverride.forceBoolean(),
        "signboard_move_ap_prompt" to FlagOverride.forceBoolean(),
        "use_4c_in_signboard" to FlagOverride.forceBoolean(),
    )
}

val GboardIncognitoMode = patch(
    name = "Manual Incognito mode toggle",
    description = "Adds a manual Incognito mode toggle button in Gboard's toolbar access points.",
) {
    gboardFlagOverrides(
        "enable_incognito_mode_entry_point" to FlagOverride.forceBoolean(),
        "allow_incognito_mode_switch" to FlagOverride.forceBoolean(),
    )
}

val GboardSmartAutoCorrection = patch(
    name = "Smart auto-correction & fast learning",
    description = "Enhances auto-correction accuracy, enables reverting auto-corrected words on backspace, and optimizes user history learning.",
    use = false,
) {
    gboardFlagOverrides(
        "auto_correct_with_replace_text" to FlagOverride.forceBoolean(),
        "enable_revert_auto_correct_on_pk" to FlagOverride.forceBoolean(),
        "intended_oov_min_count" to FlagOverride.forceLong(2L),
        "min_char_num_limit_for_single_word_predictions" to FlagOverride.forceLong(2L),
        "threshold_for_candidates_from_user_history" to FlagOverride.forceDouble(-5.0),
        "add_pending_oovs_on_reset_max_ms" to FlagOverride.forceLong(500L),
        "add_pending_oovs_on_create_max_ms" to FlagOverride.forceLong(500L),
        "enable_pk_spatial_model_features" to FlagOverride.forceBoolean(),
        "enable_pk_auto_correction_trigger_predictor" to FlagOverride.forceBoolean(),
        "enable_post_correction_trigger_predictor" to FlagOverride.forceBoolean(),
        "populate_preemptive_next_key_predictions" to FlagOverride.forceBoolean(),
        "enable_space_transposition_post_correction" to FlagOverride.forceBoolean(),
        "update_streaming_touch_data_set_is_gesture" to FlagOverride.forceBoolean(),
        "enable_two_stage_decoder" to FlagOverride.forceBoolean(),
        "fix_first_auto_correction_commit" to FlagOverride.forceBoolean(),
        "learning_for_editor_without_auto_correction" to FlagOverride.forceBoolean(),
        "enable_pc_user_history_ngram_count" to FlagOverride.forceBoolean(),
        "populate_ngram_count_with_context" to FlagOverride.forceBoolean(),
        "populate_ngram_timestamp_in_user_history" to FlagOverride.forceBoolean(),
        "enable_multiword_predictions_from_user_history" to FlagOverride.forceBoolean(),
        "enable_multiword_suggestions_from_user_history" to FlagOverride.forceBoolean(),
        "enable_dynamic_lm_unigram_continuation_scoring" to FlagOverride.forceBoolean(),
        "preserve_context_in_dynamic_lm_compute_history" to FlagOverride.forceBoolean(),
        "enable_dynamic_lm_noise_filter" to FlagOverride.forceBoolean(),
        "enable_oov_deduplication" to FlagOverride.forceBoolean(),
    )
}

val GboardCandidateEmojiVibrancy = patch(
    name = "Candidate strip emoji vibrancy",
    description = "Ensures emoji glyphs in suggestion candidates render at full opacity (alpha 255) instead of dull translucent text styles.",
    use = false,
) {
    runCatching {
        val autoCenterClass = classLoader.loadClass("com.google.android.libraries.inputmethod.widgets.text.AutoCenterScaleTextView")
        XposedBridge.hookAllMethods(
            autoCenterClass,
            "onTextChanged",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val tv = param.thisObject as? TextView ?: return
                    GboardCandidateEmojiRuntime.adjustCandidatePaint(tv)
                }
            }
        )
    }
    Unit
}

val GboardClipboardHistorySlider = patch(
    name = "Extended clipboard history retention slider",
    description = "Configures Gboard's native clipboard history retention slider with discrete duration choices (1h, 24h, 7d, 30d, Never).",
) {
    gboardFlagOverrides(
        "enable_extended_clipboard_history" to FlagOverride.forceBoolean(),
        "enable_system_clipboard_direct_paste_for_quick_paste" to FlagOverride.forceBoolean(),
    )
    GboardClipboardHistoryRuntime.install(classLoader)
}

val GboardSignatureBypass = patch(
    name = "Signature verification bypass",
    description = "Bypasses internal signature checks for Google Play services and offline speech/AI models.",
) {
    GboardSignatureBypassHook.install(classLoader)
}

val GboardGoogleSansTypography = patch(
    name = "Google Sans Flex typography",
    description = "Enables Google Sans Flex keycap font styling and GM3 expressive design.",
) {
    gboardFlagOverrides(
        "google_sans_flex" to FlagOverride.forceTrue(),
        "platform_google_sans_flex" to FlagOverride.forceTrue(),
        "gm3_expressive" to FlagOverride.forceTrue(),
    )
}

val GboardUndoControls = patch(
    name = "Undo action controls",
    description = "Enables proactive 1-tap Undo buttons in the suggestion strip after text replacements and autocorrection.",
    use = false,
) {
    gboardFlagOverrides(
        "undo_proactive_post_correction" to FlagOverride.forceTrue(),
        "undo_proactive_auto_correction_cooperative" to FlagOverride.forceTrue(),
        "undo_enable_chunk_fusion" to FlagOverride.forceTrue(),
        "nga_enable_undo_delete" to FlagOverride.forceTrue(),
        "undo_stop_deferred_range_notification" to FlagOverride.forceFalse(),
        "undo_stop_pk_handling" to FlagOverride.forceFalse(),
        "undo_promotion_banner_threshold" to FlagOverride.forceLong(1L),
        "undo_max_chunk" to FlagOverride.forceLong(100L),
        "undo_truncate_suffix_len" to FlagOverride.forceLong(64L),
        "undo_suppress_app_pattern" to FlagOverride.forceString(""),
    )
}

val GboardQuickAndMagicEmoji = patch(
    name = "Quick & Magic Emoji (Emojify)",
    description = "Enables the fast-access emoji bar and Gemini-powered Magic Emoji suggestion button.",
) {
    gboardFlagOverrides(
        "enable_fast_access_bar" to FlagOverride.promote(),
        "fast_access_bar_app_whitelist" to FlagOverride.forceString("*"),
        "enable_emojify" to FlagOverride.forceTrue(),
        "enable_emojify_settings_option" to FlagOverride.forceTrue(),
        "enable_emojify_language_tags" to FlagOverride.forceString("*"),
        "emojify_app_allowlist" to FlagOverride.forceString("*"),
        "enable_emojify_shortcut_when_followed_by_emoji" to FlagOverride.forceTrue(),
        "enable_emojify_when_talkback_on" to FlagOverride.forceTrue(),
        "enable_emojify_model" to FlagOverride.forceTrue(),
    )
    enableGboardPreferences(
        booleans = mapOf(
            "enable_emojify" to true,
        )
    )
}

val GboardKeypressHaptics = patch(
    name = "Keypress vibration intensity control",
    description = "Enables hardware VibrationEffect vibration strength and intensity slider on Android 11+.",
) {
    gboardFlagOverrides(
        "vibration_effect_min_sdk" to FlagOverride.forceLong(30L),
        "vibration_strength_supported" to FlagOverride.forceTrue(),
        "enable_vibration_strength_slider" to FlagOverride.forceTrue(),
        "enable_haptic_feedback_level" to FlagOverride.forceTrue(),
    )
}

val GboardSplitKeyboard = patch(
    name = "Split keyboard on all devices",
    description = "Enables split keyboard layout mode across phones, foldables, and tablets.",
    use = false,
) {
    gboardFlagOverrides(
        "enable_split_keyboard_on_foldable" to FlagOverride.forceTrue(),
        "split_keyboard_support_all_devices" to FlagOverride.forceTrue(),
        "enable_split_keyboard_in_landscape" to FlagOverride.forceTrue(),
        "force_split_keyboard" to FlagOverride.forceTrue(),
    )
}

val GboardTerminalPasteAndSelectAll = patch(
    name = "Terminal paste & auto select-all",
    description = "Fixes paste shortcuts in raw terminal input fields (Termux) and automatically selects all text before copy/cut when nothing is selected.",
) {
    GboardEditingHook.install(classLoader)
}

val GboardSpacebarLogo = patch(
    name = "G Logo on Spacebar",
    description = "Renders the Google G logo icon on the spacebar with clean keycap centering.",
    use = false,
) {
    GboardSpacebarLogoHook.install(classLoader)
}

val GboardDeveloperOptions = patch(
    name = "Developer options & internal flags",
    description = "Unlocks Gboard's internal developer options and experimental feature flag menus in settings.",
    use = false,
) {
    gboardFlagOverrides(
        "enable_dev_options" to FlagOverride.forceTrue(),
        "show_debug_menu" to FlagOverride.forceTrue(),
        "enable_feature_flags_menu" to FlagOverride.forceTrue(),
    )
}

val GboardBackupRestore = patch(
    name = "Backup & Restore settings",
    description = "Enables Gboard personal dictionary, preferences, and custom theme backup and restore options.",
    use = false,
) {
    gboardFlagOverrides(
        "enable_backup_restore" to FlagOverride.forceTrue(),
        "enable_gboard_backup" to FlagOverride.forceTrue(),
    )
}

val GboardPatches = arrayOf(
    GboardSettingsMenu,
    GboardRamblerVoiceTyping,
    GboardAiWritingTools,
    GboardRoundedKeycaps,
    GboardSpellCheckerChips,
    GboardCandidateEmojiVibrancy,
    GboardSmartAutoCorrection,
    GboardClipboardHistorySlider,
    GboardSignatureBypass,
    GboardGoogleSansTypography,
    GboardUndoControls,
    GboardQuickAndMagicEmoji,
    GboardKeypressHaptics,
    GboardSplitKeyboard,
    GboardTerminalPasteAndSelectAll,
    GboardSpacebarLogo,
    GboardDeveloperOptions,
    GboardBackupRestore,
    GboardCursorTrackpad,
    GboardQuickInsert,
    GboardEmojiSize,
    GboardAccessPointsMenu,
    GboardBluetoothMicrophone,
    GboardCloseProactiveSuggestions,
    GboardAccessPointCount,
    GboardClipboardCharacterLimit,
    GboardFlowMode,
    GboardGrammarChecker,
    GboardInlineSuggestions,
    GboardKeyShapeSelection,
    GboardScanTextOcr,
    GboardScreenshotChips,
    GboardEmojiPaletteV2,
    GboardSmartCompose,
    GboardEmogen,
    GboardSignToText,
    GboardIncognitoMode,
)



