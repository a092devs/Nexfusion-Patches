package io.github.a092devs.nexfusion.morphe.music.interaction.playlisttracktap

import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.a092devs.nexfusion.patch

val PlaylistTrackTap = patch(
    name = "Playlist track tap action",
    description = "Adds an option to play only the tapped track of a playlist or album, " +
        "or to add it to the queue, instead of replacing the queue with the whole playlist.",
    use = false,
) {
    dependsOn(SettingsHook)

    PreferenceScreen.PLAYER.addPreferences(
        ListPreference("morphe_music_playlist_track_tap_action")
    )
}
