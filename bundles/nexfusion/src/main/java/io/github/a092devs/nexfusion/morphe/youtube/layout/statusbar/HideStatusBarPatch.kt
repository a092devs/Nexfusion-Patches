package io.github.a092devs.nexfusion.morphe.youtube.layout.statusbar

import app.morphe.extension.youtube.patches.HideStatusBarPatch
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.youtube.insertLiteralOverride
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch

val HideStatusBar = patch(
    name = "Hide status bar",
    description = "Adds an option to hide the system status bar. Swipe down from the top edge to show it for a moment.",
    use = false,
) {
    PreferenceScreen.GENERAL.addPreferences(
        SwitchPreference("morphe_hide_status_bar", summary = true),
        SwitchPreference("morphe_fix_status_bar_layout", summary = true),
    )

    io.github.a092devs.nexfusion.morphe.setExtensionIsPatchIncluded(HideStatusBarPatch::class.java)
    insertLiteralOverride(45400535L, HideStatusBarPatch::useEdgeToEdgeTopBar)
    insertLiteralOverride(45830993L, HideStatusBarPatch::useStatusBarScrollColor)
}

