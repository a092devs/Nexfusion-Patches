/*
 * Copyright 2026 NexFusion contributors
 */

package io.github.nexfusion.morphe.youtube.interaction.autoplay

import app.morphe.extension.youtube.patches.AutoplayControlPatch as ExtensionAutoplayControlPatch
import app.morphe.extension.youtube.videoplayer.AutoplayButton
import io.github.nexfusion.morphe.shared.misc.settings.preference.InputType
import io.github.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.TextPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.nexfusion.morphe.youtube.layout.player.buttons.addPlayerBottomButton
import io.github.nexfusion.morphe.youtube.layout.player.buttons.playerOverlayButtonsHook
import io.github.nexfusion.morphe.youtube.layout.playlistautoplay.DisablePlaylistAutoplay
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexfusion.morphe.youtube.video.information.VideoInformationPatch
import io.github.nexfusion.morphe.youtube.video.videoid.VideoId
import io.github.nexfusion.morphe.youtube.video.videoid.videoIdHooks
import io.github.nexfusion.patch

val AutoplayControl = patch(
    name = "Autoplay control",
    description = "Adds a player button and a rule for the next video: playlists and queues go on, a Mix stops after a number of videos, a suggested video never plays.",
) {
    dependsOn(
        DisablePlaylistAutoplay,
        playerOverlayButtonsHook,
        VideoInformationPatch,
        VideoId,
    )

    PreferenceScreen.PLAYER.addPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_autoplay_control", summary = true),
            ListPreference("morphe_autoplay_mode"),
            TextPreference("morphe_autoplay_mix_stop_after", inputType = InputType.NUMBER),
        )
    )

    addPlayerBottomButton(AutoplayButton::initializeButton)

    videoIdHooks.add { videoId ->
        ExtensionAutoplayControlPatch.newVideoLoaded(videoId)
    }
}
