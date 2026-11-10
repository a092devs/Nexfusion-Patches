package io.github.nexfusion.nexfusion.discord

import android.content.res.AssetManager
import android.graphics.Typeface
import android.os.Build
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.compat.XposedHelpers
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

// Ported from VenusIsJaded/Venus-Patches (GPL-3.0),
// extensions/font/src/main/kotlin/app/venus/font/SystemFont.kt (Discord 348.10 - Stable),
// and patches/src/main/kotlin/app/venus/patches/DiscordPatches.kt (`useSystemFont`).
//
// Venus injects a guarded call to SystemFont.typeface into ReactFontManager.getTypeface and reads
// its own saved setting; this bundle installs the same decision as a runtime hook, with the
// manager's patch toggle playing the role of the Venus switch. Only Discord's own families are
// replaced: display-name fonts (8Bit, Sakura, ...) and every other lookup keep their typeface.

val UsePhoneFont = patch(
    name = "Use your phone's font",
    description = "Makes Discord's own fonts (gg sans, Noto Sans, Ginto, gg mono) draw with your phone's system font, keeping weight and italic.",
    use = false,
) {
    val fontManager = runCatching {
        Class.forName("com.facebook.react.common.assets.ReactFontManager", false, classLoader)
    }.getOrNull()
    val styleClass = runCatching {
        Class.forName("com.facebook.react.common.assets.ReactFontManager\$TypefaceStyle", false, classLoader)
    }.getOrNull()
    val getTypeface = if (fontManager == null || styleClass == null) null else runCatching {
        fontManager.getDeclaredMethod("getTypeface", String::class.java, styleClass, AssetManager::class.java)
    }.getOrNull()
    if (getTypeface == null) {
        PatcherLog.info { "NexFusion: ReactFontManager.getTypeface not found, phone font patch skipped" }
        return@patch
    }
    XposedBridge.hookMethod(getTypeface, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            val family = param.args.getOrNull(0) as? String ?: return
            val style = param.args.getOrNull(1) ?: return
            val nearestStyle = runCatching { XposedHelpers.callMethod(style, "getNearestStyle") as? Int }
                .getOrNull() ?: return
            PhoneFont.typeface(family, nearestStyle)?.let { param.result = it }
        }
    })
}

/** The decision half of Venus's SystemFont, without its saved-setting plumbing. */
private object PhoneFont {
    private val discordFamily = Pattern.compile("^\\s*(ggsans|gg sans|NotoSans|ABCGinto[A-Za-z]*|ggmono|SourceCodePro)([^,]*)")
    private val monospace = Pattern.compile("^(ggmono|SourceCodePro)$")
    private val extraBold = Pattern.compile("ExtraBold")
    private val black = Pattern.compile("Black")
    private val semibold = Pattern.compile("Semi[Bb]old")
    private val bold = Pattern.compile("Bold")
    private val medium = Pattern.compile("Medium")
    private val italicName = Pattern.compile("Italic")
    private val skip = Any()
    private val cache = ConcurrentHashMap<String, Any>()

    /** The system typeface for one of Discord's families, or null to let Discord load its own. */
    fun typeface(family: String, nearestStyle: Int): Typeface? {
        if (nearestStyle < 0 || nearestStyle > 3) return null
        @Suppress("UNCHECKED_CAST")
        var slots = cache[family] as Array<Any?>?
        if (slots == null) {
            val made = arrayOfNulls<Any>(4)
            @Suppress("UNCHECKED_CAST")
            slots = (if (cache.size < 256) cache.putIfAbsent(family, made) as Array<Any?>? else null) ?: made
        }
        val cached = slots[nearestStyle]
        if (cached != null) return if (cached === skip) null else cached as Typeface
        val made = make(family, nearestStyle)
        slots[nearestStyle] = made ?: skip
        return made
    }

    private fun make(family: String, nearestStyle: Int): Typeface? {
        val match = discordFamily.matcher(family)
        if (!match.lookingAt()) return null
        val suffix = match.group(2) ?: ""
        var weight = when {
            extraBold.matcher(suffix).find() -> 800
            black.matcher(suffix).find() -> 900
            semibold.matcher(suffix).find() -> 600
            bold.matcher(suffix).find() -> 700
            medium.matcher(suffix).find() -> 500
            else -> 400
        }
        val family0 = match.group(1) ?: ""
        // Discord names the weight in the family; a bold/italic style on top of it still applies.
        // ReactFontManager's nearest style: 1 bold, 2 italic, 3 bold italic.
        if ((nearestStyle and 1) != 0 && weight < 700) weight = 700
        val italic = italicName.matcher(suffix).find() || (nearestStyle and 2) != 0
        val base = if (monospace.matcher(family0).matches()) Typeface.MONOSPACE else Typeface.SANS_SERIF
        if (Build.VERSION.SDK_INT < 28) return null
        var made = Typeface.create(base, weight, italic)
        // Discord's chat font loader throws "Cannot find typeface" for anything equal to
        // Typeface.DEFAULT, and on many phones plain sans-serif is exactly that. One weight unit
        // more is the same font when drawn, but its own typeface, so the loader accepts it.
        if (made == Typeface.DEFAULT) made = Typeface.create(base, weight + 1, italic)
        return if (made == Typeface.DEFAULT) null else made
    }
}

