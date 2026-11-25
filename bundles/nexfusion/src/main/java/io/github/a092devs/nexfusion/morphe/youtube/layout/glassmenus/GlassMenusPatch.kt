package io.github.a092devs.nexfusion.morphe.youtube.layout.glassmenus

import android.app.Dialog
import app.morphe.extension.shared.patches.glassmenus.GlassMenusPatch as GlassMenusExt
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.ResourceType
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.resourceLiteral
import io.github.a092devs.nexfusion.morphe.setExtensionIsPatchIncluded
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.VersionCheck
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch

internal object BottomSheetDialogClassFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(resourceLiteral(ResourceType.LAYOUT, "design_bottom_sheet_dialog"))
)

internal object BottomSheetDialogOnAttachedToWindowFingerprint : Fingerprint(
    classFingerprint = BottomSheetDialogClassFingerprint,
    name = "onAttachedToWindow",
    returnType = "V",
    parameters = listOf()
)

internal object DialogFragmentOnStartFingerprint : Fingerprint(
    name = "onStart",
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(definingClass = "Landroid/app/Dialog;", name = "show"),
        methodCall(definingClass = "Landroid/app/Dialog;", name = "getWindow", location = MatchAfterWithin(5)),
        methodCall(definingClass = "Landroid/view/Window;", name = "getDecorView", location = MatchAfterWithin(5))
    )
)

val GlassMenus = patch(
    name = "Glass menus",
    description = "Adds an option to show menus as frosted glass with blurred backgrounds on Android 12+.",
    use = false,
) {
    dependsOn(VersionCheck)

    PreferenceScreen.GENERAL.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_glass_menus_screen",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_glass_menus", summary = true),
                NonInteractivePreference(
                    key = "morphe_glass_menus_opacity",
                    tag = app.morphe.extension.shared.settings.preference.SeekBarPreference::class.java
                ),
                NonInteractivePreference(
                    key = "morphe_glass_menus_blur",
                    tag = app.morphe.extension.shared.settings.preference.SeekBarPreference::class.java
                ),
                NonInteractivePreference(
                    key = "morphe_glass_menus_dim",
                    tag = app.morphe.extension.shared.settings.preference.SeekBarPreference::class.java
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

    runCatching {
        val touchFeedbackDrawableClass = classLoader.loadClass(
            "com.google.android.libraries.youtube.rendering.ui.spec.motion.TouchFeedbackDrawable"
        )
        val ctor = touchFeedbackDrawableClass.getDeclaredConstructor(android.graphics.drawable.Drawable::class.java)
        io.github.a092devs.nexfusion.compat.XposedBridge.hookMethod(
            ctor,
            object : io.github.a092devs.nexfusion.compat.XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val wrapper = param.thisObject as? android.graphics.drawable.Drawable ?: return
                    val wrapped = param.args.firstOrNull() as? android.graphics.drawable.Drawable ?: return
                    GlassMenusExt.onWrappedDrawable(wrapper, wrapped)
                }
            }
        )
    }
}
