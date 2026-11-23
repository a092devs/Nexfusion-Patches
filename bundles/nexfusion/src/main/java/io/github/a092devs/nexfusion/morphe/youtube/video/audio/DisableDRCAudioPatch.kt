package io.github.a092devs.nexfusion.morphe.youtube.video.audio

import app.morphe.extension.shared.patches.DisableDRCAudioPatch as DisableDRCAudioExt
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.anyInstruction
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.newInstance
import io.github.a092devs.nexfusion.morphe.setExtensionIsPatchIncluded
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.VersionCheck
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch

import org.luckypray.dexkit.query.matchers.MethodMatcher

private const val PLAYBACK_AGNOSTIC_USER_PREFERENCES =
    "Lcom/google/android/libraries/youtube/media/interfaces/PlaybackAgnosticUserPreferences;"
private const val PLAYBACK_AGNOSTIC_USER_PREFERENCES_NAME =
    "com.google.android.libraries.youtube.media.interfaces.PlaybackAgnosticUserPreferences"

internal object PlaybackAgnosticUserPreferencesFingerprint : Fingerprint(
    custom = {
        addAnyOf {
            addInvoke {
                declaredClass(PLAYBACK_AGNOSTIC_USER_PREFERENCES_NAME)
            }
        }
        addAnyOf {
            addInvoke {
                returnType(PLAYBACK_AGNOSTIC_USER_PREFERENCES_NAME)
            }
        }
    },
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
            newInstance(PLAYBACK_AGNOSTIC_USER_PREFERENCES),
            methodCall(returnType = PLAYBACK_AGNOSTIC_USER_PREFERENCES),
            location = MatchAfterWithin(15)
        )
    )
)

internal val drcAudioMethod = findMethodDirect {
    val match = PlaybackAgnosticUserPreferencesFingerprint.match()
    match.instructionMatches[0].instruction.methodRef!!
}

val DisableDRCAudio = patch(
    name = "Disable DRC audio",
    description = "Adds options to disable DRC (Dynamic Range Compression) audio and loudness normalization.",
    use = false,
) {
    dependsOn(VersionCheck)

    PreferenceScreen.VIDEO.addPreferences(
        SwitchPreference("morphe_disable_drc_audio_stream", summary = true),
        SwitchPreference("morphe_disable_loudness_normalization", summary = true)
    )

    runCatching {
        ::drcAudioMethod.dexMethod.hookMethod {
            after {
                if (DisableDRCAudioExt.disableDrcAudio()) {
                    it.result = false
                }
            }
        }
    }
}
