/*
 * Copyright 2026 NexFusion contributors
 */

package io.github.nexfusion.morphe.youtube.interaction.playlistpanel

import android.widget.ImageView
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.youtube.videoplayer.PlaylistPanelButton as ExtensionPlaylistPanelButton
import io.github.nexfusion.patches.R
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.youtube.layout.buttons.overlay.addPlayerOverlayPreferences
import io.github.nexfusion.morphe.youtube.misc.commandrouter.CommandRouterHook
import io.github.nexfusion.morphe.youtube.misc.playercontrols.ControlInitializer
import io.github.nexfusion.morphe.youtube.misc.playercontrols.LegacyPlayerControls
import io.github.nexfusion.morphe.youtube.misc.playercontrols.addTopControl
import io.github.nexfusion.morphe.youtube.misc.playercontrols.initializeTopControl
import io.github.nexfusion.morphe.youtube.video.information.VideoInformationPatch
import io.github.nexfusion.patch

val PlaylistPanelButton = patch(
    name = "Playlist button",
    description = "Adds an option to display a button in the video player that opens the playlist panel.",
) {
    dependsOn(
        LegacyPlayerControls,
        VideoInformationPatch,
        CommandRouterHook,
    )

    addPlayerOverlayPreferences(
        SwitchPreference("morphe_playlist_panel_button", summary = true),
    )

    addTopControl(
        R.layout.morphe_playlist_panel_button,
        R.id.morphe_playlist_panel_button,
        R.id.morphe_playlist_panel_button
    )

    initializeTopControl(
        ControlInitializer(
            R.id.morphe_playlist_panel_button,
        ) { root ->
            val button = root.findViewById<ImageView>(R.id.morphe_playlist_panel_button)
            val drawableId = ResourceUtils.getDrawableIdentifier("yt_outline_experimental_playlist_vd_theme_24")
            if (drawableId != 0 && button != null) {
                button.setImageResource(drawableId)
            }
            ExtensionPlaylistPanelButton.initializeLegacyButton(root)
        }
    )
}
