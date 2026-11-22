package io.github.a092devs.nexfusion.morphe.youtube.interaction.doubletap

import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch

val disableChapterSkipDoubleTapPatch = patch(
    name = "Disable chapter skip double tap",
    description = "Adds an option to disable chapter skipping when double-tapping with two fingers.",
) {
    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_disable_chapter_skip_double_tap", summary = true),
    )
}
