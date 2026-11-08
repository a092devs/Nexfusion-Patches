package io.github.nexfusion.morphe.youtube.interaction.onthego

import app.morphe.extension.youtube.patches.onthego.OnTheGoFilter
import app.morphe.extension.youtube.patches.onthego.OnTheGoPatch
import io.github.nexfusion.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.nexfusion.morphe.shared.misc.proto.hookElement
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexfusion.patch

val onTheGoPatch = patch(
    name = "On-the-go player",
    description = "Adds an option to open a panel with large playback controls from the On-the-go item of the video player menu.",
) {
    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_on_the_go", summary = true),
    )

    hookElement(OnTheGoPatch::onElementLoaded)
    addLithoFilter(OnTheGoFilter())
}
