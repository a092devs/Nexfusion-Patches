package io.github.a092devs.nexfusion.morphe.music.audio.exclusiveaudio

import io.github.a092devs.nexfusion.compat.XC_MethodReplacement
import io.github.a092devs.nexfusion.morphe.music.misc.playservice.is_9_32_or_greater
import io.github.a092devs.nexfusion.morphe.music.misc.playservice.versionCheckPatch
import io.github.a092devs.nexfusion.patch

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
