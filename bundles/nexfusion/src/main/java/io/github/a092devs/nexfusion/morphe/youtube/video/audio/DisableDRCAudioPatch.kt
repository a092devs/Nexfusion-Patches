package io.github.a092devs.nexfusion.morphe.youtube.video.audio

import app.morphe.extension.shared.patches.DisableDRCAudioPatch as DisableDRCAudioExt
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.anyInstruction
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.newInstance
import io.github.a092devs.nexfusion.morphe.setExtensionIsPatchIncluded
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.VersionCheck
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch

internal object PlaybackAgnosticUserPreferencesFingerprint : Fingerprint(
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf(),
            returnType = "Z"
        ),
        methodCall(
            parameters = listOf(),
            returnType = "Ljava/lang/String;",
            location = MatchAfterWithin(3)
        ),
        anyInstruction(
            newInstance("Lcom/google/android/libraries/youtube/media/interfaces/PlaybackAgnosticUserPreferences;"),
            methodCall(returnType = "Lcom/google/android/libraries/youtube/media/interfaces/PlaybackAgnosticUserPreferences;"),
            location = MatchAfterWithin(15)
        )
    )
)

val DisableDRCAudio = patch(
    name = "Disable DRC audio",
    description = "Adds options to disable DRC (Dynamic Range Compression) audio and loudness normalization.",
) {
    dependsOn(VersionCheck)

    PreferenceScreen.VIDEO.addPreferences(
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
