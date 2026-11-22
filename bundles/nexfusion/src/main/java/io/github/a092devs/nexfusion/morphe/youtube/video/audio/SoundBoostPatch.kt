package io.github.a092devs.nexfusion.morphe.youtube.video.audio

import app.morphe.extension.youtube.patches.SoundBoostPatch
import io.github.a092devs.nexfusion.patch
import org.luckypray.dexkit.wrap.DexMethod


@Suppress("unused")
val soundBoostPatch = patch(
    description = "Adds an option to swipe the volume above the maximum level."
) {
    DexMethod("Landroid/media/AudioTrack;->getAudioSessionId()I").hookMethod {
        after {
            SoundBoostPatch.onAudioSessionId(it.result as Int)
        }
    }
}
