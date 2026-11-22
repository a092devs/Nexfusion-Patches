package io.github.a092devs.nexfusion.morphe.music.misc.audio.drc

import app.morphe.extension.shared.patches.DisableDRCAudioPatch as DisableDRCAudioExt
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.setExtensionIsPatchIncluded
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.youtube.video.audio.PlaybackAgnosticUserPreferencesFingerprint
import io.github.a092devs.nexfusion.patch

val DisableDRCAudio = patch(
    name = "Disable DRC audio",
    description = "Adds options to disable DRC (Dynamic Range Compression) audio and loudness normalization.",
) {
    PreferenceScreen.MISC.addPreferences(
        SwitchPreference("morphe_disable_drc_audio_stream", summary = true),
        SwitchPreference("morphe_disable_loudness_normalization", summary = true)
    )

    runCatching {
        PlaybackAgnosticUserPreferencesFingerprint.hookMethod {
            after {
                if (DisableDRCAudioExt.disableDrcAudio()) {
                    it.result = false
                }
            }
        }
    }
}
