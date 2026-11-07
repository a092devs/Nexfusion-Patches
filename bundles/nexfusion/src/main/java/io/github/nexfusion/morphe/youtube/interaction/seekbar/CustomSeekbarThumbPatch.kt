package io.github.nexfusion.morphe.youtube.interaction.seekbar

import app.morphe.extension.youtube.settings.preference.CustomSeekbarThumbPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexfusion.patch

val customSeekbarThumbPatch = patch(
    name = "Custom seekbar thumb",
    description = "Adds an option to customize the seekbar thumb with a custom image or animated GIF.",
) {
    PreferenceScreen.SEEKBAR.addPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_custom_seekbar_thumb", summary = true),
            NonInteractivePreference(
                key = "morphe_custom_seekbar_thumb_file",
                tag = CustomSeekbarThumbPreference::class.java,
                selectable = true,
            ),
            ListPreference("morphe_custom_seekbar_thumb_scale_type"),
        ),
    )
}
