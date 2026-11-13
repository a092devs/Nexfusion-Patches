package io.github.nexfusion.morphe.youtube.video.speed.button

import app.morphe.extension.youtube.videoplayer.PlaybackSpeedDialogButton
import io.github.nexfusion.patches.R
import io.github.nexfusion.morphe.shared.misc.settings.preference.InputType
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.TextPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.nexfusion.morphe.youtube.layout.buttons.overlay.addPlayerOverlayPreferences
import io.github.nexfusion.morphe.youtube.layout.player.buttons.addPlayerBottomButton
import io.github.nexfusion.morphe.youtube.layout.player.buttons.playerOverlayButtonsHook
import io.github.nexfusion.morphe.youtube.misc.playercontrols.ControlInitializer
import io.github.nexfusion.morphe.youtube.misc.playercontrols.LegacyPlayerControls
import io.github.nexfusion.morphe.youtube.misc.playercontrols.addLegacyBottomControl
import io.github.nexfusion.morphe.youtube.misc.playercontrols.initializeLegacyBottomControl
import io.github.nexfusion.morphe.youtube.video.information.VideoInformationPatch
import io.github.nexfusion.morphe.youtube.video.information.userSelectedPlaybackSpeedHook
import io.github.nexfusion.morphe.youtube.video.information.videoSpeedChangedHook
import io.github.nexfusion.morphe.youtube.video.speed.custom.CustomPlaybackSpeed
import io.github.nexfusion.patch

val PlaybackSpeedButton = patch(
    description = "Adds the option to display playback speed dialog button in the video player.",
) {
    dependsOn(
        CustomPlaybackSpeed,
        LegacyPlayerControls,
        playerOverlayButtonsHook,
        VideoInformationPatch,
    )

    addPlayerOverlayPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_playback_speed_dialog_button", summary = true),
            SwitchPreference("morphe_playback_speed_button_cycle", summary = true),
            TextPreference(
                "morphe_playback_speed_button_cycle_speeds",
                inputType = InputType.TEXT_MULTI_LINE
            ),
        )
    )

    addPlayerBottomButton(PlaybackSpeedDialogButton::initializeButton)

    addLegacyBottomControl(R.layout.morphe_playback_speed_dialog_button)
    initializeLegacyBottomControl(
        ControlInitializer(
            R.id.morphe_playback_speed_dialog_button_container,
            PlaybackSpeedDialogButton::initializeLegacyButton
        )
    )

    videoSpeedChangedHook.add { PlaybackSpeedDialogButton.videoSpeedChanged(it) }
    userSelectedPlaybackSpeedHook.add { PlaybackSpeedDialogButton.videoSpeedChanged(it) }
}

