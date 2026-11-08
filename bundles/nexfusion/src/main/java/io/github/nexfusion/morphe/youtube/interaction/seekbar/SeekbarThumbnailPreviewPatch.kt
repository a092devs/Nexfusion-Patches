package io.github.nexfusion.morphe.youtube.interaction.seekbar

import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexfusion.patch

val seekbarThumbnailPreviewPatch = patch(
    name = "Seekbar thumbnail preview",
    description = "Adds an option to restore the seekbar thumbnail preview.",
) {
    PreferenceScreen.SEEKBAR.addPreferences(
        SwitchPreference("morphe_seekbar_thumbnail_preview", summary = true),
    )
}
