package io.github.nexfusion.morphe.music.audio.exclusiveaudio

import io.github.nexfusion.compat.XC_MethodReplacement
import io.github.nexfusion.morphe.music.misc.playservice.is_9_32_or_greater
import io.github.nexfusion.morphe.music.misc.playservice.versionCheckPatch
import io.github.nexfusion.patch

val EnableExclusiveAudioPlayback = patch(
    name = "Enable exclusive audio playback",
    description = "Enables the option to play audio without video.",
) {
    dependsOn(
        versionCheckPatch
    )
    val fingerprint = if (is_9_32_or_greater) {
        AllowExclusiveAudioPlaybackFingerprint
    } else {
        AllowExclusiveAudioPlaybackLegacyFingerprint
    }

    fingerprint.hookMethod(XC_MethodReplacement.returnConstant(true))
}
