package io.github.a092devs.nexfusion.morphe.shared.ad

import app.morphe.extension.shared.patches.HideFullscreenAdsPatch
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.BasePreferenceScreen
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.patch

fun HideFullscreenAds(preferenceScreen: BasePreferenceScreen.Screen) = patch(
    description = "Adds an option to hide fullscreen premium popup ads."
) {
    preferenceScreen.addPreferences(
        SwitchPreference("morphe_hide_fullscreen_ads")
    )


    // Hide fullscreen ad
    LithoDialogBuilderFingerprint.hookMethod {
        val dialogField = ::LithoDialogField.field
        after {
            val buffer = it.args[0] as ByteArray?
            val dialog = dialogField.get(it.thisObject)
            HideFullscreenAdsPatch.closeFullscreenAd(dialog, buffer)
        }
    }

    HideFullscreenAdsPatch::class.java.getDeclaredMethod(
        "closeDialog", Any::class.java
    ).hookMethod {
        val backPressed = CustomDialogOnBackPressedFingerprint.method
        before {
            backPressed(it.args[0])
        }
    }
}