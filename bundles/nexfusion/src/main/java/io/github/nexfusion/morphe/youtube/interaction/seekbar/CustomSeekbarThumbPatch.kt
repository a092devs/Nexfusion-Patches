package io.github.nexfusion.morphe.youtube.interaction.seekbar

import android.graphics.Canvas
import android.os.Build
import android.view.View
import app.morphe.extension.youtube.patches.CustomSeekbarThumbPatch
import app.morphe.extension.youtube.settings.preference.CustomSeekbarThumbPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexfusion.morphe.youtube.shared.seekbarOnDrawFingerprint
import io.github.nexfusion.patch
import io.github.nexfusion.scopedHook
import org.luckypray.dexkit.wrap.DexMethod

val customSeekbarThumbPatch = patch(
    name = "Custom seekbar thumb",
    description = "Adds an option to customize the seekbar thumb with a custom image or animated GIF.",
) {
    PreferenceScreen.SEEKBAR.addPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_custom_seekbar_thumb", summary = true),
            NonInteractivePreference(
                key = "morphe_custom_seekbar_thumb_file",
                tag = CustomSeekbarThumbPreference::class.java,
                selectable = true,
            ),
            ListPreference("morphe_custom_seekbar_thumb_scale_type"),
        ),
    )

    // Set seekbar view reference at start of onDraw
    ::seekbarOnDrawFingerprint.hookMethod {
        before { param ->
            (param.thisObject as? View)?.let { CustomSeekbarThumbPatch.setSeekbarView(it) }
        }
    }

    val drawCircle =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            "Landroid/view/DisplayListCanvas;->drawCircle(FFFLandroid/graphics/Paint;)V"
        else
            "Landroid/graphics/RecordingCanvas;->drawCircle(FFFLandroid/graphics/Paint;)V"

    ::seekbarOnDrawFingerprint.hookMethod(
        scopedHook(
            DexMethod(drawCircle).toMethod() to {
                before { param ->
                    val canvas = param.thisObject as? Canvas ?: return@before
                    val cx = param.args[0] as Float
                    val cy = param.args[1] as Float
                    val radius = param.args[2] as Float
                    if (CustomSeekbarThumbPatch.drawCustomThumb(canvas, cx, cy, radius)) {
                        param.result = null
                    }
                }
            }
        )
    )
}
