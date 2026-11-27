package io.github.a092devs.nexfusion.morphe.music.interaction.likefilter

import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.music.video.information.MusicVideoInformation
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.InputType
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.TextPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.a092devs.nexfusion.patch

val QueueLikeFilter = patch(
    name = "Queue likes filter",
    description = "Adds an option to remove songs with few likes from auto-generated mixes and radio, " +
        "using the like counts of Return YouTube Dislike. Playlists and albums are left unchanged.",
    use = false,
) {
    dependsOn(
        SettingsHook,
        MusicVideoInformation,
    )

    PreferenceScreen.PLAYER.addPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_music_queue_like_filter", summary = true),
            TextPreference(
                key = "morphe_music_queue_like_filter_minimum_likes",
                inputType = InputType.NUMBER,
            ),
        ),
    )
}
