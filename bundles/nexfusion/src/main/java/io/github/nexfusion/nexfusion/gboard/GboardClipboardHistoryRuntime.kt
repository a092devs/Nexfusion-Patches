package io.github.nexfusion.nexfusion.gboard

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.compat.XposedHelpers
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Runtime helper for Gboard Clipboard History Duration Slider.
 * Ported from Akshay Kadam's PixelBoard (GPL-3.0).
 *
 * Configures Gboard's native `LabelSummarySliderPreference` in clipboard settings
 * with discrete retention choices:
 * 1. 1 hour ("1")
 * 2. 24 hours (Current maximum) ("24")
 * 3. 7 days ("168")
 * 4. 30 days ("720")
 * 5. Never (Unlimited / Manual deletion only) ("876000", 100 years)
 *
 * Prevents Material Slider `value > valueTo` crash by clamping index to 0..4 before setting max.
 */
object GboardClipboardHistoryRuntime {

    private const val TAG = "NexFusion"

    const val PREF_KEY_HISTORY_DURATION = "clipboard_history_duration"
    const val GBOARD_NATIVE_PREF_KEY = "clipboard_history_retention"

    const val DURATION_1_HOUR = "1"
    const val DURATION_24_HOURS = "24"
    const val DURATION_7_DAYS = "168"
    const val DURATION_30_DAYS = "720"
    const val DURATION_NEVER = "876000"

    const val DEFAULT_DURATION = DURATION_24_HOURS

    val RETENTION_LABELS: Array<CharSequence> = arrayOf(
        "1 hour",
        "24 hours (Current maximum)",
        "7 days",
        "30 days",
        "Never (Unlimited / Manual deletion only)",
    )

    val RETENTION_VALUES: Array<CharSequence> = arrayOf(
        DURATION_1_HOUR,
        DURATION_24_HOURS,
        DURATION_7_DAYS,
        DURATION_30_DAYS,
        DURATION_NEVER,
    )

    fun install(classLoader: ClassLoader) {
        hookSliderPreference(classLoader)
        hookClipboardSettingsFragments(classLoader)
    }

    private fun hookSliderPreference(classLoader: ClassLoader) {
        val sliderClass = runCatching {
            classLoader.loadClass("com.google.android.libraries.inputmethod.preferencewidgets.LabelSummarySliderPreference")
        }.getOrNull() ?: return

        // Hook onBindViewHolder so whenever the slider is attached to UI, discrete values are applied
        val bindMethod = sliderClass.methods.firstOrNull {
            it.name == "onBindViewHolder" || (it.parameterTypes.size == 1 && it.parameterTypes[0].name.contains("PreferenceViewHolder"))
        }
        if (bindMethod != null) {
            runCatching {
                XposedBridge.hookMethod(bindMethod, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pref = param.thisObject ?: return
                        val context = runCatching {
                            val getContextMethod = pref.javaClass.getMethod("getContext")
                            getContextMethod.invoke(pref) as? Context
                        }.getOrNull() ?: return
                        configureClipboardHistorySlider(pref, context)
                    }
                })
                Log.i(TAG, "NexFusion: Hooked LabelSummarySliderPreference.onBindViewHolder")
            }
        }
    }

    private fun hookClipboardSettingsFragments(classLoader: ClassLoader) {
        // Targets: fbr.b(Context, rbq) in 18.5.3/18.5.4/18.4.1, exc.b(Context, quo) in 18.0.3/18.3.1
        val configClassNames = listOf("fbr", "exc")
        for (className in configClassNames) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            val bMethod = clazz.declaredMethods.firstOrNull {
                it.name == "b" && it.parameterTypes.size == 2 && it.parameterTypes[0] == Context::class.java
            } ?: continue

            runCatching {
                XposedBridge.hookMethod(bMethod, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val ctx = param.args.getOrNull(0) as? Context ?: return
                        val prefGroup = param.args.getOrNull(1) ?: return
                        findAndConfigureSlider(prefGroup, ctx)
                    }
                })
                Log.i(TAG, "NexFusion: Hooked clipboard config fragment $className.b(Context, ...)")
            }
        }
    }

    private fun findAndConfigureSlider(preferenceGroup: Any, context: Context) {
        // Try finding preference by key "clipboard_history_retention"
        runCatching {
            val findPrefMethod = preferenceGroup.javaClass.methods.firstOrNull {
                it.parameterTypes.size == 1 &&
                    (it.parameterTypes[0] == CharSequence::class.java || it.parameterTypes[0] == String::class.java) &&
                    it.returnType.name.contains("Preference")
            }
            if (findPrefMethod != null) {
                val pref = findPrefMethod.invoke(preferenceGroup, GBOARD_NATIVE_PREF_KEY)
                if (pref != null) {
                    configureClipboardHistorySlider(pref, context)
                    return
                }
            }
        }

        // Iterate preferences in group if find failed
        runCatching {
            val getCountMethod = preferenceGroup.javaClass.methods.firstOrNull {
                it.parameterTypes.isEmpty() && it.returnType == Int::class.javaPrimitiveType &&
                    (it.name == "getPreferenceCount" || it.name == "k")
            } ?: return
            val getPrefMethod = preferenceGroup.javaClass.methods.firstOrNull {
                it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    it.returnType.name.contains("Preference")
            } ?: return

            val count = getCountMethod.invoke(preferenceGroup) as? Int ?: 0
            for (i in 0 until count) {
                val p = getPrefMethod.invoke(preferenceGroup, i) ?: continue
                if (p.javaClass.name.contains("LabelSummarySliderPreference")) {
                    configureClipboardHistorySlider(p, context)
                }
            }
        }
    }

    /**
     * Configures the native Gboard LabelSummarySliderPreference with discrete duration steps.
     */
    fun configureClipboardHistorySlider(preference: Any?, context: Context?) {
        if (preference == null) return
        try {
            val kField = findField(preference.javaClass, "K")
            if (kField != null) {
                kField.isAccessible = true
                kField.set(preference, RETENTION_LABELS.clone())
            }

            val lField = findField(preference.javaClass, "L")
            if (lField != null) {
                lField.isAccessible = true
                lField.set(preference, RETENTION_VALUES.clone())
            }

            val maxIndex = RETENTION_VALUES.size - 1 // 4

            // Determine target index from currently persisted string or fallback to index 1 ("24" / 24 hours)
            var targetIndex = 1
            try {
                val wMethod = findMethod(preference.javaClass, "w", String::class.java)
                if (wMethod != null) {
                    wMethod.isAccessible = true
                    val savedValue = wMethod.invoke(preference, "") as? String
                    if (!savedValue.isNullOrEmpty()) {
                        for (i in RETENTION_VALUES.indices) {
                            if (RETENTION_VALUES[i] == savedValue) {
                                targetIndex = i
                                break
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
            }

            // Also check SharedPreferences if wMethod was null
            if (context != null && targetIndex == 1) {
                runCatching {
                    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                    val saved = prefs.getString(GBOARD_NATIVE_PREF_KEY, "")
                    if (!saved.isNullOrEmpty()) {
                        for (i in RETENTION_VALUES.indices) {
                            if (RETENTION_VALUES[i] == saved) {
                                targetIndex = i
                                break
                            }
                        }
                    }
                }
            }

            // CRITICAL FIX: Update preference value field 'b' to a valid index <= maxIndex
            // BEFORE setting max, preventing Material Slider's value > valueTo IllegalStateException
            val bField = findField(preference.javaClass, "b")
            if (bField != null) {
                bField.isAccessible = true
                val currentB = bField.getInt(preference)
                if (currentB > maxIndex || currentB < 0) {
                    bField.setInt(preference, targetIndex)
                }
            }

            // If the Material Slider view is already attached, safely clamp its value before valueTo
            val aField = findField(preference.javaClass, "a")
            if (aField != null) {
                aField.isAccessible = true
                val slider = aField.get(preference)
                if (slider != null) {
                    try {
                        var setValMethod = findMethod(slider.javaClass, "H", Float::class.javaPrimitiveType)
                        if (setValMethod == null) {
                            setValMethod = findMethod(slider.javaClass, "setValue", Float::class.javaPrimitiveType)
                        }
                        if (setValMethod != null) {
                            setValMethod.isAccessible = true
                            setValMethod.invoke(slider, targetIndex.toFloat())
                        }

                        var setMaxValMethod = findMethod(slider.javaClass, "w", Float::class.javaPrimitiveType)
                        if (setMaxValMethod == null) {
                            setMaxValMethod = findMethod(slider.javaClass, "setValueTo", Float::class.javaPrimitiveType)
                        }
                        if (setMaxValMethod != null) {
                            setMaxValMethod.isAccessible = true
                            setMaxValMethod.invoke(slider, maxIndex.toFloat())
                        }
                    } catch (t: Throwable) {
                        runCatching { Log.w(TAG, "Failed to update slider view values", t) }
                    }
                }
            }

            // Set max on preference
            val dField = findField(preference.javaClass, "d")
            if (dField != null) {
                dField.isAccessible = true
                dField.setInt(preference, maxIndex)
            }

            val setMaxMethod = findMethod(preference.javaClass, "al", Int::class.javaPrimitiveType)
            if (setMaxMethod != null) {
                setMaxMethod.isAccessible = true
                setMaxMethod.invoke(preference, maxIndex)
            }

            // Clamp progress and persist via ap(targetIndex, false)
            val apMethod = findMethod(preference.javaClass, "ap", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            if (apMethod != null) {
                apMethod.isAccessible = true
                apMethod.invoke(preference, targetIndex, false)
            }

            // Notify preference change
            val notifyChangedMethod = findMethod(preference.javaClass, "d")
            if (notifyChangedMethod != null) {
                notifyChangedMethod.isAccessible = true
                notifyChangedMethod.invoke(preference)
            }

            runCatching {
                Log.i(TAG, "NexFusion: Configured clipboard history retention slider (selected: ${RETENTION_LABELS[targetIndex]})")
            }
        } catch (t: Throwable) {
            runCatching {
                Log.w(TAG, "NexFusion: Failed to configure clipboard history slider", t)
            }
        }
    }

    private fun findField(clazz: Class<*>, fieldName: String): Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                return current.getDeclaredField(fieldName)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun findMethod(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>?): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                return current.getDeclaredMethod(methodName, *parameterTypes)
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }
}

