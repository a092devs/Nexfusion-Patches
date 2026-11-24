package io.github.a092devs.nexfusion.morphe.youtube.layout.theme

import android.app.Activity
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
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
    "yt_black0",
    "yt_black1",
    "yt_black2",
    "yt_black3",
    "yt_black4",
    "yt_black1_opacity95",
    "yt_black1_opacity98",
    "yt_material_black_pure",
    "yt_status_bar_background_dark",
    "material_grey_850",
    "cardview_dark_background",
    "background_material_dark",
    "background_floating_material_dark",
    "yt_sys_color_baseline_dark_base_background",
    "yt_sys_color_baseline_dark_raised_background",
    "yt_sys_color_baseline_dark_menu_background",
    "yt_sys_color_baseline_dark_default_menu_background",
    "yt_sys_color_baseline_dark_static_black",
    "yt_sys_color_baseline_base_background",
    "yt_sys_color_baseline_menu_background",
    "yt_sys_color_baseline_raised_background",
    "yt_sys_color_baseline_mobile_dark_default_default_base_background",
    "yt_sys_color_baseline_mobile_dark_default_raised_background",
    "yt_sys_color_baseline_mobile_dark_default_solid_wash",
    "yt_sys_color_baseline_mobile_dark_default_tonal_wash",
    "yt_sys_color_baseline_mobile_dark_default_outline_rim",
    "yt_sys_color_baseline_mobile_dark_default_solid_rim",
    "yt_ref_color_constants_default_baseline_black_black0",
    "yt_ref_color_constants_default_baseline_black_black1",
    "yt_ref_color_constants_default_baseline_black_black2",
    "yt_ref_color_constants_default_baseline_black_black3",
    "yt_ref_color_constants_default_baseline_black_black4",
    "yt_ref_color_constants_cooler_baseline_black_black3",
    "yt_ref_color_constants_default_grey5",
    "yt_ref_color_constants_default_grey20",
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
    0xFF161616.toInt(),
    0xFF242424.toInt(),
    0xFF2B2B2B.toInt(),
    0xFF1A1A1A.toInt(),
    0xFF1C1C1E.toInt(),
    0xFF2C2C2E.toInt(),
    0xFF303030.toInt(),
    0xFF383838.toInt(),
)

private fun isDarkGray(color: Int): Boolean {
    val a = Color.alpha(color)
    if (a < 50) return false
    val r = Color.red(color)
    val g = Color.green(color)
    val b = Color.blue(color)
    return (color in darkColorValues) ||
        (r in 1..58 && g in 1..58 && b in 1..58 && Math.abs(r - g) <= 12 && Math.abs(g - b) <= 12 && Math.abs(r - b) <= 12)
}

private fun isDarkThemeOrSurface(contextOrRes: Any? = null, color: Int? = null): Boolean {
    if (color != null && isDarkGray(color)) return true
    if (Utils.isDarkModeEnabled()) return true
    val config = when (contextOrRes) {
        is Resources -> contextOrRes.configuration
        is android.content.Context -> contextOrRes.resources.configuration
        is View -> runCatching { contextOrRes.resources.configuration }.getOrNull()
        else -> null
    }
    if (config != null && (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
        return true
    }
    return (Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

private fun cleanDarkViews(v: View) {
    val bg = v.background
    if (bg is ColorDrawable && isDarkGray(bg.color)) {
        v.setBackgroundColor(Color.BLACK)
    } else if (bg is GradientDrawable) {
        val csl = bg.color
        if (csl != null && isDarkGray(csl.defaultColor)) {
            bg.setColor(Color.BLACK)
        }
    }
    val className = v.javaClass.simpleName
    if (className.contains("Toolbar") || className.contains("ActionBar") || className.contains("AppBar") || className.contains("PivotBar") || className.contains("BottomNav")) {
        v.setBackgroundColor(Color.BLACK)
    }
    if (v is ViewGroup) {
        for (i in 0 until v.childCount) {
            cleanDarkViews(v.getChildAt(i))
        }
    }
}

val Theme = patch(
    name = "AMOLED dark theme",
    description = "Applies a pure AMOLED pitch black theme (#000000) throughout YouTube interface elements, feed, cards, watch view, and menus. Ambient mode must be disabled in YouTube's video player settings (or via the Ambient mode patch) for true pitch black during video playback.",
    use = false,
) {
    dependsOn(VersionCheck)

    runCatching {
        setExtensionIsPatchIncluded(ExtThemePatch::class.java)
        SharedYouTubeSettings.THEME_COLOR_DARK.save(app.morphe.extension.shared.theme.ThemeColorPatch.ThemeColorDark.PURE_BLACK)
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
        XposedBridge.hookAllMethods(
            resClass,
            "getColor",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val res = param.thisObject as? Resources ?: return
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
                        Utils.setIsDarkModeEnabled(true)
                        param.result = Color.BLACK
                        return
                    }

                    val entryName = runCatching { res.getResourceEntryName(id) }.getOrNull()
                    if (entryName != null && entryName in darkBackgroundResourceNames) {
                        blackColorIds.add(id)
                        Utils.setIsDarkModeEnabled(true)
                        param.result = Color.BLACK
                    } else {
                        nonBlackColorIds.add(id)
                    }
                }
            }
        )

        XposedBridge.hookAllMethods(
            resClass,
            "getColorStateList",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val csl = param.result as? ColorStateList ?: return
                    val id = param.args.firstOrNull() as? Int
                    if (id != null && blackColorIds.contains(id)) {
                        param.result = ColorStateList.valueOf(Color.BLACK)
                        return
                    }
                    if (isDarkGray(csl.defaultColor)) {
                        if (id != null) blackColorIds.add(id)
                        Utils.setIsDarkModeEnabled(true)
                        param.result = ColorStateList.valueOf(Color.BLACK)
                    }
                }
            }
        )
    }

    // 2. Hook lower-level ResourcesImpl for direct framework queries
    runCatching {
        val implClass = Class.forName("android.content.res.ResourcesImpl")
        XposedBridge.hookAllMethods(
            implClass,
            "getColor",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val color = (param.result as? Number)?.toInt() ?: return
                    if (isDarkGray(color)) {
                        param.result = Color.BLACK
                    }
                }
            }
        )
        XposedBridge.hookAllMethods(
            implClass,
            "loadColorStateList",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val csl = param.result as? ColorStateList ?: return
                    if (isDarkGray(csl.defaultColor)) {
                        param.result = ColorStateList.valueOf(Color.BLACK)
                    }
                }
            }
        )
    }

    // 3. Hook TypedArray color resolution for layout/theme attributes
    runCatching {
        val typedArrayClass = android.content.res.TypedArray::class.java
        XposedBridge.hookAllMethods(
            typedArrayClass,
            "getColor",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val color = (param.result as? Number)?.toInt() ?: return
                    if (isDarkGray(color)) {
                        Utils.setIsDarkModeEnabled(true)
                        param.result = Color.BLACK
                    }
                }
            }
        )
        XposedBridge.hookAllMethods(
            typedArrayClass,
            "getColorStateList",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val csl = param.result as? ColorStateList ?: return
                    if (isDarkGray(csl.defaultColor)) {
                        Utils.setIsDarkModeEnabled(true)
                        param.result = ColorStateList.valueOf(Color.BLACK)
                    }
                }
            }
        )
    }

    // 4. Hook Theme.resolveAttribute for direct theme attribute queries
    runCatching {
        val themeClass = Resources.Theme::class.java
        XposedBridge.hookAllMethods(
            themeClass,
            "resolveAttribute",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val resolved = param.result as? Boolean ?: return
                    if (!resolved) return
                    val outValue = param.args.getOrNull(1) as? TypedValue ?: return
                    if (outValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && outValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                        if (isDarkGray(outValue.data)) {
                            outValue.data = Color.BLACK
                        }
                    }
                }
            }
        )
    }

    // 5. Hook View backgrounds so dark surfaces (toolbars, headers, cards) match AMOLED pure black
    runCatching {
        val viewClass = View::class.java
        XposedBridge.hookAllMethods(
            viewClass,
            "setBackgroundColor",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val color = (param.args.firstOrNull() as? Number)?.toInt() ?: return
                    if (isDarkGray(color)) {
                        param.args[0] = Color.BLACK
                    }
                }
            }
        )
        val bgHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val d = param.args.firstOrNull() as? Drawable ?: return
                if (d is ColorDrawable && isDarkGray(d.color)) {
                    param.args[0] = ColorDrawable(Color.BLACK)
                } else if (d is GradientDrawable) {
                    val csl = d.color
                    if (csl != null && isDarkGray(csl.defaultColor)) {
                        d.setColor(Color.BLACK)
                    }
                }
            }
        }
        XposedBridge.hookAllMethods(viewClass, "setBackground", bgHook)
        XposedBridge.hookAllMethods(viewClass, "setBackgroundDrawable", bgHook)
        XposedBridge.hookAllMethods(
            viewClass,
            "setBackgroundTintList",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val csl = param.args.firstOrNull() as? ColorStateList ?: return
                    if (isDarkGray(csl.defaultColor)) {
                        param.args[0] = ColorStateList.valueOf(Color.BLACK)
                    }
                }
            }
        )
        XposedBridge.hookAllMethods(
            viewClass,
            "onAttachedToWindow",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val v = param.thisObject as? View ?: return
                    val bg = v.background
                    if (bg is ColorDrawable && isDarkGray(bg.color)) {
                        v.setBackgroundColor(Color.BLACK)
                    } else if (bg is GradientDrawable) {
                        val csl = bg.color
                        if (csl != null && isDarkGray(csl.defaultColor)) {
                            bg.setColor(Color.BLACK)
                        }
                    }
                    val className = v.javaClass.simpleName
                    if (isDarkThemeOrSurface(v) && (className.contains("Toolbar") || className.contains("ActionBar") || className.contains("AppBar") || className.contains("PivotBar") || className.contains("BottomNav"))) {
                        v.setBackgroundColor(Color.BLACK)
                    }
                }
            }
        )
    }

    // 6. Hook Activity decor and window background, and recursively clean toolbars
    runCatching {
        val hookActivity = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                if (isDarkThemeOrSurface(activity)) {
                    activity.window?.let { win ->
                        win.setBackgroundDrawable(ColorDrawable(Color.BLACK))
                        win.navigationBarColor = Color.BLACK
                        win.statusBarColor = Color.BLACK
                    }
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

    // 7. Hook Litho component color processor in extension
    runCatching {
        val themePatchClass = ExtThemePatch::class.java
        XposedBridge.hookAllMethods(
            themePatchClass,
            "getValue",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val orig = (param.args.firstOrNull() as? Number)?.toInt() ?: return
                    if (isDarkGray(orig)) {
                        param.result = Color.BLACK
                    }
                }
            }
        )
    }
}

