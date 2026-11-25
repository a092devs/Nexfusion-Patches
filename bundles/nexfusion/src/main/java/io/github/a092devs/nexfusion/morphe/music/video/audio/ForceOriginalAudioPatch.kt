package io.github.a092devs.nexfusion.morphe.music.video.audio

import io.github.a092devs.nexfusion.morphe.music.misc.playservice.is_9_26_or_greater
import io.github.a092devs.nexfusion.morphe.music.misc.playservice.versionCheckPatch
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.music.shared.MusicActivityOnCreateFingerprint
import io.github.a092devs.nexfusion.morphe.shared.misc.audio.tracks.forceOriginalAudioPatch

val ForceOriginalAudio = forceOriginalAudioPatch(
    block = {
        dependsOn(versionCheckPatch)
    },
    fixUseLocalizedAudioTrackFlag = { !is_9_26_or_greater },
    forcedServerAdaptiveStreaming = { is_9_26_or_greater },
    mainActivityOnCreateFingerprint = MusicActivityOnCreateFingerprint,
    subclassExtensionClassDescriptor = "Lapp/morphe/extension/music/patches/ForceOriginalAudioPatch;",
    preferenceScreen = PreferenceScreen.VIDEO,
)
