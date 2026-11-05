package io.github.nexfusion.morphe.youtube.layout.dearrow

import app.morphe.extension.youtube.patches.dearrow.DeArrowPatch
import app.morphe.extension.youtube.settings.preference.DeArrowAboutPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.screenInfoPreferenceCategory
import io.github.nexfusion.morphe.shared.misc.settings.preference.PreferenceCategory
import io.github.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.TextPreference
import io.github.nexfusion.morphe.youtube.misc.imageurlhook.addImageUrlErrorCallbackHook
import io.github.nexfusion.morphe.youtube.misc.imageurlhook.addImageUrlHook
import io.github.nexfusion.morphe.youtube.misc.imageurlhook.addImageUrlSuccessCallbackHook
import io.github.nexfusion.morphe.youtube.misc.imageurlhook.cronetImageUrlHookPatch
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexfusion.patch

val deArrowPatch = patch(
    name = "DeArrow",
    description = "Adds options to replace video thumbnails and titles using the DeArrow API, " +
            "or replace video thumbnails with image captures from the video.",
) {
    dependsOn(
        cronetImageUrlHookPatch,
        // Thumbnails that fail to load are loaded again by mounting the Litho views again.
//        lithoRelayoutPatch,
        // Titles are replaced by the same hooks that restore the original titles.
//        restoreOriginalTitlesPatch,
    )
    val entries = "morphe_dearrow_thumbnail_options_entries"
    val values = "morphe_dearrow_thumbnail_options_entry_values"
    PreferenceScreen.DEARROW.addPreferences(
        PreferenceCategory(
            key = "morphe_dearrow_thumbnails_category",
            titleKey = "morphe_dearrow_thumbnails_title",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                ListPreference(
                    key = "morphe_dearrow_thumbnail_home",
                    titleKey = "morphe_dearrow_titles_home_title",
                    entriesKey = entries,
                    entryValuesKey = values
                ),
                ListPreference(
                    key = "morphe_dearrow_thumbnail_subscription",
                    titleKey = "morphe_dearrow_titles_subscription_title",
                    entriesKey = entries,
                    entryValuesKey = values
                ),
                ListPreference(
                    key = "morphe_dearrow_thumbnail_library",
                    titleKey = "morphe_dearrow_titles_library_title",
                    entriesKey = entries,
                    entryValuesKey = values
                ),
                ListPreference(
                    key = "morphe_dearrow_thumbnail_player",
                    titleKey = "morphe_dearrow_titles_player_title",
                    entriesKey = entries,
                    entryValuesKey = values
                ),
                ListPreference(
                    key = "morphe_dearrow_thumbnail_search",
                    titleKey = "morphe_dearrow_titles_search_title",
                    entriesKey = entries,
                    entryValuesKey = values
                ),
                NonInteractivePreference("morphe_dearrow_thumbnail_stills_about"),
                ListPreference("morphe_dearrow_thumbnail_stills_time"),
            )
        ),
        PreferenceCategory(
            key = "morphe_dearrow_general_category",
            titleKey = "morphe_settings_screen_04_general_title",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                TextPreference("morphe_dearrow_api_url"),
                SwitchPreference("morphe_dearrow_connection_toast", summary = true),
            )
        ),
        screenInfoPreferenceCategory(
            key = "morphe_dearrow_about_category",
            preferences = setOf(
                NonInteractivePreference(
                    key = "morphe_dearrow_about",
                    titleKey = null,
                    // Custom about preference with link to the DeArrow website.
                    tag = DeArrowAboutPreference::class.java,
                    selectable = true,
                )
            )
        )
    )

    addImageUrlHook(DeArrowPatch::overrideImageURL)
    addImageUrlSuccessCallbackHook(DeArrowPatch::handleCronetSuccess)
    addImageUrlErrorCallbackHook(DeArrowPatch::handleCronetFailure)
}