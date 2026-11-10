package io.github.nexfusion.morphe.music.misc.backgroundplayback

import io.github.nexfusion.compat.XC_MethodReplacement
import io.github.nexfusion.patch

val BackgroundPlayback = patch(
    name = "Remove background playback restrictions",
    description = "Removes restrictions on background playback, including playing kids videos in the background.",
) {
    BackgroundPlaybackDisableFingerprint.hookMethod(XC_MethodReplacement.returnConstant(true))
    KidsBackgroundPlaybackPolicyControllerFingerprint.hookMethod(XC_MethodReplacement.DO_NOTHING)
}
