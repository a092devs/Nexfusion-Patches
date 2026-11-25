package io.github.a092devs.nexfusion.morphe.youtube.interaction.onthego

import app.morphe.extension.youtube.patches.onthego.OnTheGoFilter
import app.morphe.extension.youtube.patches.onthego.OnTheGoPatch
import io.github.a092devs.nexfusion.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.a092devs.nexfusion.morphe.shared.misc.proto.hookElement
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch

val onTheGoPatch = patch(
    name = "On-the-go player",
    description = "Adds an option to open a panel with large playback controls from the On-the-go item of the video player menu.",
    use = false,
) {
    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_on_the_go", summary = true),
    )

    hookElement(OnTheGoPatch::onElementLoaded)
    addLithoFilter(OnTheGoFilter())
    io.github.a092devs.nexfusion.morphe.setExtensionIsPatchIncluded(OnTheGoPatch::class.java)
}
