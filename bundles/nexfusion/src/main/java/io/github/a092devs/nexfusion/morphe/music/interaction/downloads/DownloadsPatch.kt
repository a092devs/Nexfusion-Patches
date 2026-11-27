package io.github.a092devs.nexfusion.morphe.music.interaction.downloads

import app.morphe.extension.music.patches.DownloadsPatch
import app.morphe.extension.music.patches.components.PlaylistPageFilter
import app.morphe.extension.music.patches.components.SongMenuFilter
import app.morphe.extension.shared.settings.preference.ExportFolderPreference
import app.morphe.extension.shared.settings.preference.ExternalDownloaderPreference
import io.github.a092devs.nexfusion.morphe.music.misc.litho.filter.LithoFilter
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.music.video.information.MusicVideoInformation
import io.github.a092devs.nexfusion.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.TextPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.textcomponent.hookSpannableStringOverride
import io.github.a092devs.nexfusion.morphe.shared.misc.textcomponent.textComponentPatch
import io.github.a092devs.nexfusion.patch

val Downloads = patch(
    name = "Downloads",
    description = "Adds support to download songs, albums, and playlists using the in-app " +
        "download button, either inside YouTube Music or with an external downloader app, " +
        "and exports songs and videos saved by the app to the device.",
    use = false,
) {
    dependsOn(
        SettingsHook,
        textComponentPatch,
        LithoFilter,
        MusicVideoInformation,
    )

    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_external_downloader_screen",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                ListPreference("morphe_download_mode"),
                SwitchPreference("morphe_download_force_4k", summary = true),
                NonInteractivePreference(
                    key = "morphe_export_download_folder",
                    summaryKey = null,
                    tag = ExportFolderPreference::class.java,
                    selectable = true
                ),
                SwitchPreference("morphe_external_downloader_menu", summary = true),
                TextPreference(
                    "morphe_external_downloader_name",
                    tag = ExternalDownloaderPreference::class.java
                )
            )
        )
    )

    hookSpannableStringOverride { ctx, text ->
        DownloadsPatch.onLithoTextLoaded(ctx, text)
    }

    addLithoFilter(SongMenuFilter())
    addLithoFilter(PlaylistPageFilter())
}
