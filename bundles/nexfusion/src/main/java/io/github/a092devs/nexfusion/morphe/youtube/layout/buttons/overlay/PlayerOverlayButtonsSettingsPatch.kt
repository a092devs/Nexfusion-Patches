package io.github.a092devs.nexfusion.morphe.youtube.layout.buttons.overlay

import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.BasePreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch

// Use initially null field so an exception is thrown if this patch was not included.
private var playerOverlayPreferences : MutableSet<BasePreference>? = mutableSetOf()

internal fun addPlayerOverlayPreferences(vararg preference: BasePreference) {
    playerOverlayPreferences!!.addAll(preference)
}

val PlayerOverlayButtonsSettings = patch {
    if (playerOverlayPreferences!!.isNotEmpty()) {
        PreferenceScreen.PLAYER.addPreferences(
            PreferenceScreenPreference(
                key = "morphe_overlay_buttons_screen",
                preferences = playerOverlayPreferences!!
            )
        )
    }
}