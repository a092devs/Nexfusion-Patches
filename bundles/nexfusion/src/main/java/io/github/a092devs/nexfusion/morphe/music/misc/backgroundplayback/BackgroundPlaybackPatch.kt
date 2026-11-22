package io.github.a092devs.nexfusion.morphe.music.misc.backgroundplayback

import io.github.a092devs.nexfusion.compat.XC_MethodReplacement
import io.github.a092devs.nexfusion.patch

val BackgroundPlayback = patch(
    name = "Remove background playback restrictions",
    description = "Removes restrictions on background playback, including playing kids videos in the background.",
) {
    BackgroundPlaybackDisableFingerprint.hookMethod(XC_MethodReplacement.returnConstant(true))
    KidsBackgroundPlaybackPolicyControllerFingerprint.hookMethod(XC_MethodReplacement.DO_NOTHING)
}
