package io.github.nexfusion.morphe.youtube.interaction.seekbar

import app.morphe.extension.shared.settings.preference.ColorPickerPreference
import app.morphe.extension.youtube.patches.theme.SeekbarColorPatch
import io.github.nexfusion.morphe.shared.misc.settings.preference.InputType
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.TextPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexfusion.patch

val seekbarColorPatch = patch(
    name = "Seekbar color",
    description = "Hide or set a custom seekbar color.",
) {
    PreferenceScreen.SEEKBAR.addPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_seekbar_custom_color"),
            TextPreference(
                "morphe_seekbar_custom_color_primary",
                tag = ColorPickerPreference::class.java,
                inputType = InputType.TEXT_CAP_CHARACTERS,
            ),
            TextPreference(
                "morphe_seekbar_custom_color_accent",
                tag = ColorPickerPreference::class.java,
                inputType = InputType.TEXT_CAP_CHARACTERS,
            ),
        )
    )
}
