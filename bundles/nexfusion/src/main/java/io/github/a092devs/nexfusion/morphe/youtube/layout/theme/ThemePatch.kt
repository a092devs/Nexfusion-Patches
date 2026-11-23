package io.github.a092devs.nexfusion.morphe.youtube.layout.theme

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.Utils
import app.morphe.extension.shared.settings.SharedYouTubeSettings
import app.morphe.extension.youtube.patches.theme.ThemePatch as ExtThemePatch
import io.github.a092devs.nexfusion.compat.XC_MethodHook
import io.github.a092devs.nexfusion.compat.XposedBridge
import io.github.a092devs.nexfusion.compat.XposedHelpers
import io.github.a092devs.nexfusion.morphe.setExtensionIsPatchIncluded
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.InputType
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.TextPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.VersionCheck
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch

private val darkBackgroundResourceNames = setOf(
    "yt_black1",
    "yt_black2",
    "yt_black3",
    "yt_black4",
    "yt_material_black_pure",
    "yt_sys_color_baseline_dark_base_background",
    "yt_sys_color_baseline_dark_raised_background",
    "yt_sys_color_baseline_dark_menu_background",
    "yt_sys_color_baseline_dark_default_menu_background",
    "yt_sys_color_baseline_dark_static_black",
    "yt_ref_color_constants_default_baseline_black_black0",
    "yt_ref_color_constants_default_baseline_black_black2",
    "yt_ref_color_constants_default_baseline_black_black3",
    "yt_ref_color_constants_default_baseline_black_black4",
    "yt_ref_color_constants_cooler_baseline_black_black3",
    "yt_ref_color_constants_default_grey5",
    "yt_ref_color_constants_colors_mainstage_grey5",
    "yt_ref_color_constants_colors_mainstage_grey20",
    "yt_ref_color_constants_colors_mainstage_grey25",
    "morphe_splash_background_color"
)

private val darkColorValues = setOf(
    0xFF282828.toInt(),
    0xFF212121.toInt(),
    0xFF181818.toInt(),
    0xFF0F0F0F.toInt(),
    0xFA212121.toInt(),
    0xFF222225.toInt(),
    0xFF0E0E10.toInt(),
    0xFF09090A.toInt(),
    0xFF121212.toInt(),
    0xFF1F1F1F.toInt(),
    0xFF161616.toInt()
)

private fun isDarkGray(color: Int): Boolean {
    val a = Color.alpha(color)
    if (a < 50) return false
    val r = Color.red(color)
    val g = Color.green(color)
    val b = Color.blue(color)
    return (color in darkColorValues) ||
        (r in 8..55 && g in 8..55 && b in 8..55 && Math.abs(r - g) <= 8 && Math.abs(g - b) <= 8)
}

private fun cleanDarkViews(v: View) {
    val bg = v.background
    if (bg is ColorDrawable && isDarkGray(bg.color)) {
        v.setBackgroundColor(Color.BLACK)
    }
    val className = v.javaClass.simpleName
    if (className.contains("Toolbar") || className.contains("ActionBar") || className.contains("AppBar")) {
        v.setBackgroundColor(Color.BLACK)
    }
    if (v is android.view.ViewGroup) {
        for (i in 0 until v.childCount) {
            cleanDarkViews(v.getChildAt(i))
        }
    }
}

val Theme = patch(
    name = "AMOLED dark theme",
    description = "Applies a pure AMOLED pitch black theme (#000000) throughout YouTube interface elements, feed, cards, watch view, and menus.",
    use = false,
) {
    dependsOn(VersionCheck)

    runCatching {
        setExtensionIsPatchIncluded(ExtThemePatch::class.java)
    }

    PreferenceScreen.GENERAL.addPreferences(
        noTitleUnsortedPreferenceCategory(
            ListPreference(
                "morphe_theme_color_dark",
                tag = app.morphe.extension.shared.theme.ThemeColorListPreference::class.java
            ),
            TextPreference(
                "morphe_theme_color_dark_custom",
                tag = app.morphe.extension.shared.settings.preference.ColorPickerPreference::class.java,
                inputType = InputType.TEXT_CAP_CHARACTERS
            ),
            ListPreference(
                "morphe_theme_color_light",
                tag = app.morphe.extension.shared.theme.ThemeColorListPreference::class.java
            ),
            TextPreference(
                "morphe_theme_color_light_custom",
                tag = app.morphe.extension.shared.settings.preference.ColorPickerPreference::class.java,
                inputType = InputType.TEXT_CAP_CHARACTERS
            ),
            SwitchPreference("morphe_theme_color_change_foreground", summary = true)
        ),
        SwitchPreference("morphe_gradient_loading_screen", summary = true)
    )

    PreferenceScreen.SEEKBAR.addPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_seekbar_custom_color"),
            TextPreference(
                "morphe_seekbar_custom_color_primary",
                tag = app.morphe.extension.shared.settings.preference.ColorPickerPreference::class.java,
                inputType = InputType.TEXT_CAP_CHARACTERS
            ),
            TextPreference(
                "morphe_seekbar_custom_color_accent",
                tag = app.morphe.extension.shared.settings.preference.ColorPickerPreference::class.java,
                inputType = InputType.TEXT_CAP_CHARACTERS
            )
        )
    )

    val blackColorIds = java.util.Collections.synchronizedSet(mutableSetOf<Int>())
    val nonBlackColorIds = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    // 1. Hook resource color resolution for pure AMOLED pitch black background
    runCatching {
        val resClass = Resources::class.java
        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val res = param.thisObject as? Resources ?: return
                val isDark = Utils.isDarkModeEnabled() ||
                    (res.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                if (!isDark) return

                val id = param.args.firstOrNull() as? Int ?: return
                if (blackColorIds.contains(id)) {
                    param.result = Color.BLACK
                    return
                }
                if (nonBlackColorIds.contains(id)) {
                    return
                }

                val current = (param.result as? Number)?.toInt()
                if (current != null && isDarkGray(current)) {
                    blackColorIds.add(id)
                    param.result = Color.BLACK
                    return
                }

                val entryName = runCatching { res.getResourceEntryName(id) }.getOrNull()
                if (entryName != null && entryName in darkBackgroundResourceNames) {
                    blackColorIds.add(id)
                    param.result = Color.BLACK
                } else {
                    nonBlackColorIds.add(id)
                }
            }
        }

        XposedBridge.hookAllMethods(resClass, "getColor", hook)
    }

    // 2. Hook TypedArray color resolution for layout/theme attributes
    runCatching {
        val typedArrayClass = android.content.res.TypedArray::class.java
        XposedBridge.hookAllMethods(
            typedArrayClass,
            "getColor",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val isDark = Utils.isDarkModeEnabled()
                    if (!isDark) return
                    val color = (param.result as? Number)?.toInt() ?: return
                    if (isDarkGray(color)) {
                        param.result = Color.BLACK
                    }
                }
            }
        )
    }

    // 3. Hook View backgrounds so dark surfaces (toolbars, headers, cards) match AMOLED pure black
    runCatching {
        val viewClass = View::class.java
        XposedBridge.hookAllMethods(
            viewClass,
            "setBackgroundColor",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val isDark = Utils.isDarkModeEnabled()
                    if (!isDark) return
                    val color = (param.args.firstOrNull() as? Number)?.toInt() ?: return
                    if (isDarkGray(color)) {
                        param.args[0] = Color.BLACK
                    }
                }
            }
        )
        XposedBridge.hookAllMethods(
            viewClass,
            "setBackground",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val isDark = Utils.isDarkModeEnabled()
                    if (!isDark) return
                    val d = param.args.firstOrNull() as? ColorDrawable ?: return
                    if (isDarkGray(d.color)) {
                        param.args[0] = ColorDrawable(Color.BLACK)
                    }
                }
            }
        )
    }

    // 4. Hook Activity decor and window background, and recursively clean toolbars
    runCatching {
        val hookActivity = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                val isDark = Utils.isDarkModeEnabled() ||
                    (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                if (isDark) {
                    activity.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
                    activity.window?.decorView?.let { decor ->
                        decor.setBackgroundColor(Color.BLACK)
                        cleanDarkViews(decor)
                        decor.post { cleanDarkViews(decor) }
                    }
                }
            }
        }
        XposedBridge.hookAllMethods(Activity::class.java, "onPostCreate", hookActivity)
        XposedBridge.hookAllMethods(Activity::class.java, "onResume", hookActivity)
    }

    // 3. Hook Litho component color processor in extension
    runCatching {
        val themePatchClass = ExtThemePatch::class.java
        XposedBridge.hookAllMethods(
            themePatchClass,
            "getValue",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val orig = (param.args.firstOrNull() as? Number)?.toInt() ?: return
                    if (orig in darkColorValues) {
                        param.result = Color.BLACK
                    }
                }
            }
        )
    }
}
