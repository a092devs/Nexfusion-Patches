package io.github.a092devs.nexfusion.morphe.youtube.interaction.seekbar

import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch

val seekbarThumbnailPreviewPatch = patch(
    name = "Seekbar thumbnail preview",
    description = "Adds an option to restore the seekbar thumbnail preview.",
    use = false,
) {
    PreferenceScreen.SEEKBAR.addPreferences(
        SwitchPreference("morphe_seekbar_thumbnail_preview", summary = true),
    )
}
