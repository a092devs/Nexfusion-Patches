package io.github.nexfusion.morphe.youtube.interaction.downloads

import app.morphe.extension.shared.settings.preference.ExternalDownloaderPreference
import app.morphe.extension.youtube.patches.DownloadsPatch
import app.morphe.extension.youtube.videoplayer.ExternalDownloadButton
import io.github.nexfusion.patches.R
import io.github.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.TextPreference
import io.github.nexfusion.morphe.youtube.misc.playercontrols.ControlInitializer
import io.github.nexfusion.morphe.youtube.misc.playercontrols.LegacyPlayerControls
import io.github.nexfusion.morphe.youtube.misc.playercontrols.addTopControl
import io.github.nexfusion.morphe.youtube.misc.playercontrols.initializeTopControl
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexfusion.morphe.youtube.video.information.VideoInformationPatch
import io.github.nexfusion.patch

val Downloads = patch(
    name = "Downloads",
    description = "Adds support to download videos with an external downloader app " +
            "using the in-app download button or a video player action button.",
) {
    dependsOn(
        LegacyPlayerControls,
        VideoInformationPatch,
    )

    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_external_downloader_screen",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                ListPreference("morphe_download_mode"),
                SwitchPreference("morphe_external_downloader_overlay_button", summary = true),
                TextPreference(
                    "morphe_external_downloader_name",
                    tag = ExternalDownloaderPreference::class.java
                ),
            ),
        ),
    )

    addTopControl(
        R.layout.morphe_external_download_button,
        R.id.morphe_external_download_button,
        R.id.morphe_external_download_button
    )

    initializeTopControl(
        ControlInitializer(
            R.id.morphe_external_download_button,
            ExternalDownloadButton::initializeLegacyButton
        )
    )

    OfflineVideoEndpointFingerprint.hookMethod {
        before {
            if (DownloadsPatch.inAppDownloadButtonOnClick(it.args[2] as String)) it.result = null
        }
    }
}