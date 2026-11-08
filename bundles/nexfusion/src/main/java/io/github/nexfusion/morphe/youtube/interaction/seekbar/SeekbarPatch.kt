package io.github.nexfusion.morphe.youtube.interaction.seekbar

import io.github.nexfusion.morphe.youtube.layout.hide.time.hideTimestampPatch
import io.github.nexfusion.patch

@Suppress("unused")
val seekbarPatch = patch(
    name = "Seekbar",
    description = "Adds options to disable precise seeking when swiping up on the seekbar, " +
            "slide to seek instead of playing at 2x speed when pressing and holding, " +
            "custom seekbar thumb styling, " +
            "custom seekbar colors, " +
            "thumbnail preview, " +
            "live stream DVR, " +
            "hiding the timestamp, " +
            "hiding the seekbar, " +
            "and tapping the player seekbar to seek."
) {
    dependsOn(
        disablePreciseSeekingGesturePatch,
        enableSlideToSeekPatch,
        enableTapToSeekPatch,
        customSeekbarThumbPatch,
        hideSeekbarPatch,
        seekbarColorPatch,
        seekbarThumbnailPreviewPatch,
        liveStreamDVRPatch,
        hideTimestampPatch,
    )

    // Prevent Shorts seekbar from detaching when leaving settings or switching Shorts
    io.github.nexfusion.morphe.youtube.insertLiteralOverride(45421233L, false)
}
