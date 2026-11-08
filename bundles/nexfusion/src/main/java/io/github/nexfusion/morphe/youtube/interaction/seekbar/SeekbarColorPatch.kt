package io.github.nexfusion.morphe.youtube.interaction.seekbar

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import app.morphe.extension.shared.settings.preference.ColorPickerPreference
import app.morphe.extension.youtube.patches.theme.SeekbarColorPatch
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.nexfusion.hookMethod
import io.github.nexfusion.morphe.findMethodDirect
import io.github.nexfusion.morphe.resourceMappings
import io.github.nexfusion.morphe.shared.misc.settings.preference.InputType
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.TextPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexfusion.morphe.youtube.shared.seekbarOnDrawFingerprint
import io.github.nexfusion.patch
import java.lang.reflect.Modifier

val playerLinearGradientMethod = findMethodDirect {
    findMethod {
        matcher {
            modifiers = Modifier.PUBLIC or Modifier.STATIC
            returnType = "android.graphics.LinearGradient"
            paramTypes("int", "int", "int", "int", "android.content.Context", "int")
        }
    }.single()
}

val lithoLinearGradientMethod = findMethodDirect {
    findMethod {
        matcher {
            modifiers = Modifier.STATIC
            returnType = "android.graphics.LinearGradient"
            paramTypes("float", "float", "float", "float", "int[]", "float[]")
        }
    }.single()
}

val seekbarColorPatch = patch(
    name = "Seekbar color",
    description = "Hide or set a custom seekbar color.",
) {
    PreferenceScreen.SEEKBAR.addPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_seekbar_custom_color"),
            TextPreference(
                "morphe_seekbar_custom_color_primary",
                tag = ColorPickerPreference::class.java,
                inputType = InputType.TEXT_CAP_CHARACTERS,
            ),
            TextPreference(
                "morphe_seekbar_custom_color_accent",
                tag = ColorPickerPreference::class.java,
                inputType = InputType.TEXT_CAP_CHARACTERS,
            ),
        )
    )

    // Hook video player seekbar linear gradient (19.49+)
    ::playerLinearGradientMethod.hookMethod {
        after { param ->
            val x0 = param.args[0] as Int
            val y0 = param.args[1] as Int
            val x1 = param.args[2] as Int
            val y1 = param.args[3] as Int
            val dummy = IntArray(0)
            val colors = SeekbarColorPatch.getPlayerLinearGradient(dummy, x0, y1)
            if (colors !== dummy && (x0 != x1 || y0 != y1)) {
                param.result = LinearGradient(
                    x0.toFloat(),
                    y0.toFloat(),
                    x1.toFloat(),
                    y1.toFloat(),
                    colors,
                    floatArrayOf(0.8f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
        }
    }

    // Hook Litho seekbar linear gradient (feeds / thumbnails)
    ::lithoLinearGradientMethod.hookMethod {
        before { param ->
            val colors = param.args[4] as? IntArray ?: return@before
            val positions = param.args[5] as? FloatArray
            val newColors = SeekbarColorPatch.getLithoLinearGradient(colors, positions)
            if (newColors !== colors) {
                param.args[4] = newColors
            }
        }
    }

    // Seekbar view Paint color update on draw
    ::seekbarOnDrawFingerprint.hookMethod {
        before { param ->
            if (!SeekbarColorPatch.isCustomSeekbarColorEnabled()) return@before
            val customColor = SeekbarColorPatch.getSeekbarColor()

            val view = param.thisObject as? View ?: return@before

            // Check direct Paint fields on view (e.g. field 'q' for the scrubber thumb circle)
            for (field in view.javaClass.declaredFields) {
                if (Paint::class.java.isAssignableFrom(field.type)) {
                    runCatching {
                        field.isAccessible = true
                        val paint = field.get(view) as? Paint ?: return@runCatching
                        if (paint.shader == null) {
                            paint.color = customColor
                        }
                    }
                }
            }

            // Check field 'c' or any child holder containing seekbar Paints (Ljsj)
            runCatching {
                val cField = view.javaClass.getDeclaredField("c").apply { isAccessible = true }
                val paintHolder = cField.get(view) ?: return@runCatching
                for (field in paintHolder.javaClass.declaredFields) {
                    if (Paint::class.java.isAssignableFrom(field.type)) {
                        field.isAccessible = true
                        val paint = field.get(paintHolder) as? Paint ?: continue
                        if (field.name in listOf("f", "h", "i")) {
                            paint.color = customColor
                        }
                    }
                }
            }
        }
    }

    // Hook Resources.getColor and Context.getColor for seekbar color resource IDs
    val seekbarColorIds by lazy {
        val ids = mutableSetOf(0x7f060636, 0x7f060634, 0x7f060637, 0x7f060635, 0x7f060b84)
        listOf(
            "inline_time_bar_played_not_highlighted_color",
            "inline_time_bar_colorized_bar_played_color_dark",
            "inline_time_bar_progress_color",
            "inline_time_bar_live_seekable_range",
            "reel_time_bar_played_color"
        ).forEach { name ->
            runCatching { ids.add(resourceMappings["color", name]) }
        }
        ids
    }

    val resColorHook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            if (!SeekbarColorPatch.isCustomSeekbarColorEnabled()) return
            val id = param.args[0] as? Int ?: return
            if (id in seekbarColorIds) {
                val originalColor = param.result as? Int ?: return
                param.result = SeekbarColorPatch.getVideoPlayerSeekbarColor(originalColor)
            }
        }
    }

    runCatching {
        XposedBridge.hookMethod(
            Resources::class.java.getMethod("getColor", Int::class.javaPrimitiveType),
            resColorHook
        )
    }
    runCatching {
        XposedBridge.hookMethod(
            Resources::class.java.getMethod("getColor", Int::class.javaPrimitiveType, Resources.Theme::class.java),
            resColorHook
        )
    }
    runCatching {
        XposedBridge.hookMethod(
            Context::class.java.getMethod("getColor", Int::class.javaPrimitiveType),
            resColorHook
        )
    }
}
