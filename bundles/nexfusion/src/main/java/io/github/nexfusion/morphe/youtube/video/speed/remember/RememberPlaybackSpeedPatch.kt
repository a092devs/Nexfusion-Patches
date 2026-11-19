package io.github.nexfusion.morphe.youtube.video.speed.remember

import app.morphe.extension.youtube.patches.playback.speed.RememberPlaybackSpeedPatch
import app.morphe.extension.youtube.settings.preference.CustomVideoSpeedListPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexfusion.morphe.youtube.shared.InitializePlaybackSpeedValuesFingerprint
import io.github.nexfusion.morphe.youtube.video.information.PlaybackSpeedMenu
import io.github.nexfusion.morphe.youtube.video.information.VideoInformationPatch
import io.github.nexfusion.morphe.youtube.video.information.onCreateHook
import io.github.nexfusion.morphe.youtube.video.information.userSelectedPlaybackSpeedHook
import io.github.nexfusion.morphe.youtube.video.speed.custom.CustomPlaybackSpeed
import io.github.nexfusion.morphe.youtube.video.speed.settingsMenuVideoSpeedGroup
import io.github.nexfusion.morphe.youtube.video.videoid.VideoId
import io.github.nexfusion.morphe.youtube.video.videoid.hookPlayerResponseVideoId
import io.github.nexfusion.patch

val RememberPlaybackSpeed = patch {
    dependsOn(
        VideoId,
        VideoInformationPatch,
        CustomPlaybackSpeed
    )

    settingsMenuVideoSpeedGroup.addAll(
        listOf(
            ListPreference(
                key = "morphe_playback_speed_default",
                // Entries and values are set by the extension code based on the actual speeds available.
                entriesKey = null,
                entryValuesKey = null,
                tag = CustomVideoSpeedListPreference::class.java
            ),
            ListPreference(
                key = "morphe_playback_audio_pitch_default",
                // List is shared with video speeds.
                entriesKey = null,
                entryValuesKey = null,
                tag = CustomVideoSpeedListPreference::class.java
            ),
            SwitchPreference("morphe_remember_playback_speed_last_selected", summary = true),
            SwitchPreference("morphe_remember_playback_speed_last_selected_toast", summary = true),
            SwitchPreference("morphe_disable_playback_speed_music", summary = true)
        )
    )

    // The Shorts speed belongs with the other Shorts settings.
    PreferenceScreen.SHORTS.addPreferences(
        ListPreference(
            key = "morphe_shorts_playback_speed_default",
            // Entries and values are set by the extension code based on the actual speeds available.
            entriesKey = null,
            entryValuesKey = null,
            tag = CustomVideoSpeedListPreference::class.java
        )
    )

    onCreateHook.add { RememberPlaybackSpeedPatch.newVideoStarted(it) }

    userSelectedPlaybackSpeedHook.add { RememberPlaybackSpeedPatch.userSelectedPlaybackSpeed(it) }

    hookPlayerResponseVideoId(RememberPlaybackSpeedPatch::preloadMusicVideoFetch)

    /*
     * Hook the code that is called when the playback speeds are initialized, and sets the playback speed
     */
    InitializePlaybackSpeedValuesFingerprint.hookMethod {
        before {
            RememberPlaybackSpeedPatch.setDefaultPlaybackSpeed(PlaybackSpeedMenu(it.thisObject))
        }
    }
}
