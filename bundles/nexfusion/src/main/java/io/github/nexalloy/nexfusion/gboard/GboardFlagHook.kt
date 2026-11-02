package io.github.nexalloy.nexfusion.gboard

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.nexalloy.PatcherLog
import io.github.nexalloy.PatchExecutor
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

// Ported from jasonwu1994/Gboard-patches (GPL-3.0) and Akshayykadam/PixelBoard (GPL-3.0):
// - GboardFlagFamilyComposer and *Policy.java handle Phenotype flag overrides.
// - PixelBoard provides Belka rounded keycap and AI writing assistant flag mappings and
//   SharedPreferences persistence.

/** What an override does with a flag's stock value. */
class FlagOverride private constructor(
    private val accepts: (Any?) -> Boolean,
    private val replacement: Any,
) {
    fun applyTo(stock: Any?): Any? = if (accepts(stock)) replacement else stock

    companion object {
        /** Flips a stock false to true, like upstream's policies that check `Boolean.FALSE.equals`. */
        fun promote(): FlagOverride = FlagOverride({ it == false }, true)

        /** Replaces any non-null boolean (upstream checks only the type). */
        fun forceBoolean(): FlagOverride = FlagOverride({ it is Boolean }, true)

        /** Forces a boolean true, replacing null, false, or true. */
        fun forceTrue(): FlagOverride = FlagOverride({ it == null || it is Boolean }, true)

        /** Forces a boolean false, replacing null, false, or true. */
        fun forceFalse(): FlagOverride = FlagOverride({ it == null || it is Boolean }, false)

        /** Replaces any boxed long, like the count and character-limit policies. */
        fun forceLong(value: Long): FlagOverride = FlagOverride({ it is Long }, value)

        /** Replaces any floating-point double, like candidate threshold policies. */
        fun forceDouble(value: Double): FlagOverride = FlagOverride({ it is Double || it is Float }, value)

        /** Replaces any string, like the "all languages" allowlist policies. */
        fun forceString(value: String): FlagOverride = FlagOverride({ it == null || it is String }, value)
    }
}

/**
 * Known obfuscated class names that implement the Phenotype flag interface across Gboard releases.
 * `actb`, `actq`, and `actf` are the plain flag, metadata, and flag interface on Gboard 18.4.1.
 */
private val KNOWN_FLAG_CLASS_NAMES = listOf(
    "oki", "okj", "okl",                   // Gboard 18.5.3 & 18.5.4 release
    "actb", "actq", "acug", "acul",         // Gboard 18.4.1 beta
    "odx", "ody",                           // Gboard 18.4.1 release
    "acps", "obw", "ntk", "nuw", "lhf", "lhg", "kgj", "acta", "actc", "jzp", "jzq"
)

private val SCREENSHOT_PREF_KEYS = setOf(
    "enable_screenshot_in_clipboard",
    "clipboard_screenshot_enabled_at_least_once",
    "should_auto_enable_enable_screenshot_in_clipboard",
    "enable_clipboard_image_suggestion",
    "enable_screenshot_chip",
    "enable_clipboard_recent_screenshot",
    "pref_key_enable_clipboard_suggestion",
    "enable_auto_paste_chips"
)

private val overrides = ConcurrentHashMap<String, FlagOverride>()
private val nameFields = ConcurrentHashMap<Class<*>, Field>()
private val nameMethods = ConcurrentHashMap<Class<*>, java.lang.reflect.Method>()
private val receiverNameCache = java.util.concurrent.ConcurrentHashMap<Int, String>()
private val dynamicBooleanPrefOverrides = ConcurrentHashMap<String, Boolean>()
private val dynamicStringPrefOverrides = ConcurrentHashMap<String, String>()
private var installed = false
private var prefsHooked = false

fun PatchExecutor.enableGboardPreferences(
    booleans: Map<String, Boolean> = emptyMap(),
    strings: Map<String, String> = emptyMap()
) {
    dynamicBooleanPrefOverrides.putAll(booleans)
    dynamicStringPrefOverrides.putAll(strings)

    fun seed(ctx: android.content.Context) {
        runCatching {
            val latinPrefs = ctx.getSharedPreferences(
                "${ctx.packageName}_preferences",
                android.content.Context.MODE_PRIVATE
            )
            val editor = latinPrefs.edit()
            var modified = false
            booleans.forEach { (k, v) ->
                if (!latinPrefs.contains(k)) {
                    editor.putBoolean(k, v)
                    modified = true
                }
            }
            strings.forEach { (k, v) ->
                if (!latinPrefs.contains(k)) {
                    editor.putString(k, v)
                    modified = true
                }
            }
            if (modified) editor.apply()
        }
        runCatching {
            val defaultPrefs = android.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
            val editor = defaultPrefs.edit()
            var modified = false
            booleans.forEach { (k, v) ->
                if (!defaultPrefs.contains(k)) {
                    editor.putBoolean(k, v)
                    modified = true
                }
            }
            strings.forEach { (k, v) ->
                if (!defaultPrefs.contains(k)) {
                    editor.putString(k, v)
                    modified = true
                }
            }
            if (modified) editor.apply()
        }
    }

    java.util.concurrent.Executors.newSingleThreadExecutor().execute {
        seed(appContext)
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            runCatching {
                val dps = appContext.createDeviceProtectedStorageContext()
                if (dps != null) seed(dps)
            }
        }
    }

    if (!prefsHooked) {
        prefsHooked = true
        runCatching {
            val sharedPrefsImpl = runCatching { classLoader.loadClass("android.app.SharedPreferencesImpl") }.getOrNull()
                ?: Class.forName("android.app.SharedPreferencesImpl")
            XposedHelpers.findAndHookMethod(
                sharedPrefsImpl,
                "getBoolean",
                String::class.java,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        val override = dynamicBooleanPrefOverrides[key] ?: return
                        param.result = override
                    }
                }
            )
            XposedHelpers.findAndHookMethod(
                sharedPrefsImpl,
                "getString",
                String::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        val override = dynamicStringPrefOverrides[key] ?: return
                        param.result = override
                    }
                }
            )
        }
    }
}

/**
 * Ensures Gboard's screenshot suggestions and clipboard image preview preferences are enabled
 * in SharedPreferences storage and runtime lookups.
 */
fun PatchExecutor.enableGboardScreenshotPreferences() {
    val prefs = mapOf(
        "enable_screenshot_in_clipboard" to true,
        "clipboard_screenshot_enabled_at_least_once" to true,
        "should_auto_enable_enable_screenshot_in_clipboard" to true,
        "enable_clipboard_image_suggestion" to true,
        "enable_screenshot_chip" to true,
        "enable_clipboard_recent_screenshot" to true,
        "pref_key_enable_clipboard_suggestion" to true,
        "enable_auto_paste_chips" to true,
    )
    enableGboardPreferences(booleans = prefs)
    // Force to true on disk directly so Gboard's provider and process see it immediately
    runCatching {
        appContext.getSharedPreferences("${appContext.packageName}_preferences", android.content.Context.MODE_PRIVATE)
            .edit()
            .apply { prefs.forEach { (k, v) -> putBoolean(k, v) } }
            .apply()
    }
}

/**
 * Ensures Gboard's rounded keys (Belka UI) preference is enabled in SharedPreferences storage
 * and runtime lookups.
 */
fun PatchExecutor.enableGboardRoundedKeysPreference() {
    enableGboardPreferences(
        booleans = mapOf("enable_rounded_keys" to true)
    )
}

/**
 * Ensures Gboard's AI Writing Tools and Jarvis consent preferences are enabled in SharedPreferences
 * storage and runtime lookups without clobbering user-customized access points or suggestion strip order.
 */
fun PatchExecutor.enableGboardWritingToolsPreference() {
    enableGboardPreferences(
        booleans = mapOf(
            "pref_key_writing_tools_opt_in" to true,
            "pref_key_writing_tools_screen_context_opt_in" to true,
            "pref_key_writing_tools_screen_context_opt_in_v1" to true,
            "pref_key_writing_tools_enable_pi_override" to true,
            "pref_key_stop_dictation_after_sending_writing_tools_v2_prompts" to false,
            "jarvis_consent_history" to true,
            "enable_jarvis" to true,
            "jarvis_enabled" to true,
        ),
        strings = mapOf(
            "jarvis_consent_history" to "Granted (Form)",
        )
    )
}

/**
 * Registers [entries] and installs the shared value-getter hook on first use. Called from each
 * Gboard patch while it runs, so a disabled patch never contributes its flags.
 */
fun PatchExecutor.gboardFlagOverrides(vararg entries: Pair<String, FlagOverride>) {
    for ((name, override) in entries) overrides.putIfAbsent(name, override)
    if (installed) return

    val hook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val receiver = param.thisObject ?: return
            val name = flagName(receiver) ?: return
            val override = overrides[name] ?: return
            val oldVal = param.result
            val newVal = override.applyTo(oldVal)
            param.result = newVal
        }
    }

    var hooked = 0
    for (className in KNOWN_FLAG_CLASS_NAMES) {
        val flagClass = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
        if (flagClass.isInterface || java.lang.reflect.Modifier.isAbstract(flagClass.modifiers)) continue
        val hasGetter = flagClass.declaredMethods.any {
            it.name == "g" && it.parameterTypes.isEmpty() && it.returnType == Any::class.java &&
                !java.lang.reflect.Modifier.isAbstract(it.modifiers)
        }
        if (!hasGetter) continue
        runCatching {
            XposedBridge.hookAllMethods(flagClass, "g", hook)
            hooked++
        }
    }

    if (hooked == 0) {
        PatcherLog.info { "NexFusion: Gboard flag getter not found among known classes in this build" }
    } else {
        installed = true
        PatcherLog.info { "NexFusion: Gboard flag hook installed on $hooked class(es)" }
    }
}

/** The flag's name, read from method h() or the name field of its class; null when the receiver is unexpected. */
private fun flagName(receiver: Any): String? {
    val id = System.identityHashCode(receiver)
    val cached = receiverNameCache[id]
    if (cached != null) {
        return if (cached.isEmpty()) null else cached
    }
    val receiverClass = receiver.javaClass

    // 1. Check for public method h() returning String (present in Gboard 18.4.1 actb, actq, etc.)
    val method = nameMethods[receiverClass] ?: run {
        val m = runCatching { receiverClass.getMethod("h") }.getOrNull()
            ?: runCatching {
                receiverClass.declaredMethods.firstOrNull {
                    it.parameterTypes.isEmpty() && it.returnType == String::class.java
                }?.apply { isAccessible = true }
            }.getOrNull()
        if (m != null) nameMethods.putIfAbsent(receiverClass, m) ?: m else null
    }
    if (method != null) {
        val name = runCatching { method.invoke(receiver) as? String }.getOrNull()
        if (!name.isNullOrEmpty()) {
            receiverNameCache[id] = name
            return name
        }
    }

    // 2. Fall back to field 'a' or first String field
    val field = nameFields[receiverClass] ?: run {
        var resolved: Field? = null
        var c: Class<*>? = receiverClass
        while (c != null && c != Any::class.java) {
            resolved = runCatching { c.getDeclaredField("a").apply { isAccessible = true } }.getOrNull()
                ?: runCatching { c.declaredFields.firstOrNull { it.type == String::class.java }?.apply { isAccessible = true } }.getOrNull()
            if (resolved != null) break
            c = c.superclass
        }
        if (resolved == null) return null
        nameFields.putIfAbsent(receiverClass, resolved) ?: resolved
    }
    val name = runCatching { field.get(receiver) as? String }.getOrNull()
    if (!name.isNullOrEmpty()) {
        receiverNameCache[id] = name
        return name
    }
    receiverNameCache[id] = ""
    return null
}

