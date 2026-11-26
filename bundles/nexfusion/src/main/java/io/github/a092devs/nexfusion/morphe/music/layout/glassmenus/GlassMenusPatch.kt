package io.github.a092devs.nexfusion.morphe.music.layout.glassmenus

import android.app.Dialog
import app.morphe.extension.shared.patches.glassmenus.GlassMenusPatch as GlassMenusExt
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.setExtensionIsPatchIncluded
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.youtube.layout.glassmenus.BottomSheetDialogOnAttachedToWindowFingerprint
import io.github.a092devs.nexfusion.morphe.youtube.layout.glassmenus.DialogFragmentOnStartFingerprint
import io.github.a092devs.nexfusion.patch

val GlassMenus = patch(
    name = "Glass menus",
    description = "Adds an option to show menus as frosted glass with blurred backgrounds on Android 12+.",
    use = false,
) {
    PreferenceScreen.GENERAL.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_glass_menus_screen",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_glass_menus", summary = true),
                NonInteractivePreference(
                    key = "morphe_glass_menus_opacity",
                    tag = app.morphe.extension.shared.settings.preference.SeekBarPreference::class.java,
                    selectable = true,
                ),
                NonInteractivePreference(
                    key = "morphe_glass_menus_blur",
                    tag = app.morphe.extension.shared.settings.preference.SeekBarPreference::class.java,
                    selectable = true,
                ),
                NonInteractivePreference(
                    key = "morphe_glass_menus_dim",
                    tag = app.morphe.extension.shared.settings.preference.SeekBarPreference::class.java,
                    selectable = true,
                ),
            ),
        )
    )

    setExtensionIsPatchIncluded(GlassMenusExt::class.java)

    runCatching {
        BottomSheetDialogOnAttachedToWindowFingerprint.hookMethod {
            before { param ->
                val dialog = param.thisObject as? Dialog ?: return@before
                GlassMenusExt.onBottomSheetDialog(dialog)
            }
        }
    }

    runCatching {
        DialogFragmentOnStartFingerprint.hookMethod {
            after { param ->
                val dialog = runCatching {
                    param.thisObject.javaClass.getMethod("getDialog").invoke(param.thisObject) as? Dialog
                }.getOrNull()
                if (dialog != null) {
                    GlassMenusExt.onDialogShown(dialog)
                }
            }
        }
    }
}
