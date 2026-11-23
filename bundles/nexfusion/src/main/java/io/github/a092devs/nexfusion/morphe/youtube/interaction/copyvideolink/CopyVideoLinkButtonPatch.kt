package io.github.a092devs.nexfusion.morphe.youtube.interaction.copyvideolink

import app.morphe.extension.youtube.videoplayer.CopyVideoLinkButton
import io.github.a092devs.nexfusion.patches.R
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.a092devs.nexfusion.morphe.youtube.layout.buttons.overlay.addPlayerOverlayPreferences
import io.github.a092devs.nexfusion.morphe.youtube.layout.player.buttons.addPlayerBottomButton
import io.github.a092devs.nexfusion.morphe.youtube.layout.player.buttons.playerOverlayButtonsHook
import io.github.a092devs.nexfusion.morphe.youtube.misc.playercontrols.ControlInitializer
import io.github.a092devs.nexfusion.morphe.youtube.misc.playercontrols.LegacyPlayerControls
import io.github.a092devs.nexfusion.morphe.youtube.misc.playercontrols.addLegacyBottomControl
import io.github.a092devs.nexfusion.morphe.youtube.misc.playercontrols.initializeLegacyBottomControl
import io.github.a092devs.nexfusion.morphe.youtube.video.information.VideoInformationPatch
import io.github.a092devs.nexfusion.patch

val CopyVideoLinkButtonPatch = patch(
    name = "Copy video link",
    description = "Adds options to display buttons in the video player to copy video links.",
    use = false,
) {
    dependsOn(
        LegacyPlayerControls,
        playerOverlayButtonsHook,
        VideoInformationPatch,
    )

    addPlayerOverlayPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_copy_video_link_button", summary = true),
            SwitchPreference("morphe_copy_video_link_with_timestamp_button", summary = true)
        )
    )
    addPlayerBottomButton(CopyVideoLinkButton::initializeButton)

    addLegacyBottomControl(R.layout.morphe_copy_video_url_button)
    initializeLegacyBottomControl(
        ControlInitializer(
            R.id.morphe_copy_video_url_button,
            CopyVideoLinkButton::initializeLegacyButton
        )
    )
}