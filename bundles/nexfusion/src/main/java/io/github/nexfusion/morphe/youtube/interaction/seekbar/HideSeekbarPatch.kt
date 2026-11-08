package io.github.nexfusion.morphe.youtube.interaction.seekbar

import app.morphe.extension.youtube.patches.HideSeekbarPatch
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexfusion.morphe.youtube.shared.seekbarOnDrawFingerprint
import io.github.nexfusion.patch

val hideSeekbarPatch = patch(
    name = "Hide seekbar",
    description = "Adds an option to hide the seekbar.",
) {
    PreferenceScreen.SEEKBAR.addPreferences(
        SwitchPreference("morphe_fullscreen_large_seekbar"),
        SwitchPreference("morphe_hide_seekbar"),
        SwitchPreference("morphe_hide_seekbar_thumbnail", summary = true),
    )

    ::seekbarOnDrawFingerprint.hookMethod {
        before { param ->
            if (HideSeekbarPatch.hideSeekbar()) {
                param.result = null
            }
        }
    }
}
