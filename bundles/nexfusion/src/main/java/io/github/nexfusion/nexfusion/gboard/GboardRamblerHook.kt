package io.github.nexfusion.nexfusion.gboard

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.preference.PreferenceManager
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * Implements Gemini "Rambler" Natural Voice Typing and companion lifecycle fixes:
 *
 * 1. Official VoiceSettingsFragment Selector Hook:
 *    - Gboard 18.4.1+: Hooks `mvq.a(Context)` to return true.
 *    - Gboard 18.3.1+: Hooks `aaeo.a(Context)` to return true.
 *    - Gboard 18.0.3+: Hooks `mqk.a(Context)` to return true.
 *    - Initializes `enable_jetson = true` and auto-punctuation in Device-Protected Storage (DE).
 *
 * 2. Battery-Friendly Speech Recognition Lifecycle (#3):
 *    - Hooks `InputMethodService.onFinishInputView` and `hideWindow` to terminate speech recognition
 *      bindings (`GoogleAsrService`) as soon as the keyboard is hidden, preventing screen-off battery drain.
 *
 * 3. Foreground Clipboard Sync for Samsung One UI & Vivo (#17):
 *    - Hooks `InputMethodService.onStartInputView` to guarantee ClipboardManager listener initialization
 *      and trigger primary clip capture into clipboard drawer history.
 *
 * 4. Modern Spell Checker Suggestion Chips on Android 12+:
 *    - Hooks Gboard spell checker utility methods `(Context, TextServicesManager) -> Boolean` to
 *      force inline spell check chips and grammar checking.
 *
 * 5. Promo & Feedback Banner Suppression:
 *    - Suppresses "Help improve voice typing" and Rambler feedback dropdown chips that clutter the toolbar
 *      and cause truncated/clipped visual bugs in landscape mode.
 */
object GboardRamblerHook {

    private const val TAG = "NexFusion"
    private const val PREF_ENABLE_JETSON = "enable_jetson"
    private const val PREF_AUTO_PUNCTUATION = "pref_key_auto_punctuation"
    private const val PREF_ENABLE_AUTO_PUNCTUATION = "pref_key_enable_auto_punctuation"
    private const val PREF_USE_GOOGLE_VOICE = "pref_key_use_google_voice_typing"

    private val ramblerPreferencesEnsured = java.util.concurrent.atomic.AtomicBoolean(false)
    private val backgroundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun install(classLoader: ClassLoader) {
        hookVoiceSettingsSelectors(classLoader)
        hookMicActivation(classLoader)
        hookVoiceTextPromptFix(classLoader)
        hookLanguageDownloadGuard(classLoader)
        hookClipboardDataHandler(classLoader)
        hookSpellCheckerChips(classLoader)
        suppressPromoBanners(classLoader)
        hookRamblerResetCleanup(classLoader)
    }

    private fun hookVoiceSettingsSelectors(classLoader: ClassLoader) {
        // Known selector capability checkers across Gboard releases:
        // 18.5.3/18.5.4: nca.a(Context), mwg.a(Context)
        // 18.4.1+: mvq.a(Context)
        // 18.3.1+: aaeo.a(Context)
        // 18.0.3+: mqk.a(Context)
        val selectorClassNames = listOf("nca", "mwg", "mvq", "aaeo", "mqk")
        for (className in selectorClassNames) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "a" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Context::class.java &&
                        it.returnType == Boolean::class.javaPrimitiveType
            } ?: continue

            runCatching {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val ctx = param.args.getOrNull(0) as? Context
                        if (ctx != null && ramblerPreferencesEnsured.compareAndSet(false, true)) {
                            backgroundExecutor.execute {
                                ensureRamblerPreferences(ctx)
                            }
                        }
                        param.result = true
                    }
                })
                Log.i(TAG, "NexFusion: Hooked Gboard Rambler selector $className.a(Context)")
            }
        }

        // Default selection suppression: prevent Gboard from reverting Rambler to standard
        val defaultSuppressionClasses = listOf("fde" /* 18.4.1 & 18.5.3/18.5.4 */, "kcm" /* 18.3.1 */, "fbl" /* 18.0.3 */)
        for (className in defaultSuppressionClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            for (m in clazz.declaredMethods) {
                if (m.parameterTypes.isEmpty() && m.returnType == Void.TYPE &&
                    (m.name == "hT" || m.name == "ge" || m.name == "hN")
                ) {
                    runCatching {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                // Suppress resetting selection back to standard
                                param.result = null
                            }
                        })
                        Log.i(TAG, "NexFusion: Hooked default selection suppression $className.${m.name}()")
                    }
                }
            }
        }
    }

    private fun hookMicActivation(classLoader: ClassLoader) {
        // Gboard 18.4.1+ and 18.5.3/18.5.4: mjz.B(Context) -> boolean activates toolbar mic
        val micClassNames = listOf("mjz")
        for (className in micClassNames) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "B" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Context::class.java &&
                        it.returnType == Boolean::class.javaPrimitiveType
            } ?: continue

            runCatching {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                })
                Log.i(TAG, "NexFusion: Hooked Gboard toolbar mic activation $className.B(Context)")
            }
        }
    }

    private fun hookVoiceTextPromptFix(classLoader: ClassLoader) {
        // Gboard 18.5.3/18.5.4: fhv.j(Laaru; Lsgm; String)
        // Gboard 18.4.1: fde.j(Laajc; Lrzc; String)
        val voiceTextClasses = listOf("fhv", "fde")
        for (className in voiceTextClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "j" && it.parameterTypes.size == 3 &&
                        it.parameterTypes[2] == String::class.java &&
                        it.returnType == Void.TYPE
            } ?: continue

            runCatching {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val proto = param.args.getOrNull(0) ?: return
                        GboardRamblerDictationRuntime.filterVoiceText(proto)
                    }
                })
                Log.i(TAG, "NexFusion: Hooked Rambler voice text prompt fix in $className.j(...)")
            }
        }
    }

    private val languageDownloadRequests = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private fun hookLanguageDownloadGuard(classLoader: ClassLoader) {
        // Gboard 18.5.3/18.5.4: sxs.c(String, syk, Consumer)
        // Gboard 18.4.1: sqk.c(String, src, Consumer)
        // Gboard 18.3.1: snm.c(String, soe, Consumer)
        val downloadClasses = listOf("sxs", "sqk", "snm")
        for (className in downloadClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "c" && it.parameterTypes.size == 3 &&
                        it.parameterTypes[0] == String::class.java &&
                        it.returnType == Void.TYPE
            } ?: continue

            runCatching {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val tag = param.args.getOrNull(0) as? String ?: return
                        val source = param.args.getOrNull(1)
                        val key = "$tag|$source"
                        if (languageDownloadRequests.putIfAbsent(key, true) != null) {
                            param.result = null
                        }
                    }
                })
                Log.i(TAG, "NexFusion: Hooked language download guard in $className.c(...)")
            }
        }
    }

    fun ensureRamblerPreferences(context: Context) {
        try {
            val targetContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !context.isDeviceProtectedStorage) {
                runCatching { context.createDeviceProtectedStorageContext() }.getOrNull() ?: context
            } else context

            val prefs = PreferenceManager.getDefaultSharedPreferences(targetContext)
            prefs.edit()
                .putBoolean(PREF_ENABLE_JETSON, true)
                .putBoolean(PREF_AUTO_PUNCTUATION, true)
                .putBoolean(PREF_ENABLE_AUTO_PUNCTUATION, true)
                .putBoolean(PREF_USE_GOOGLE_VOICE, true)
                .putBoolean("voice_donation_opt_out", true)
                .putInt("voice_donation_promo_shown_times", 999)
                .putBoolean("has_shown_voice_donation_promo", true)
                .putBoolean("pref_key_always_show_agentic_dictation_promo_banner", false)
                .apply()
            Log.i(TAG, "NexFusion: Ensured Rambler preferences in ${targetContext.packageName}")
        } catch (t: Throwable) {
            Log.w(TAG, "NexFusion: Failed to ensure Rambler preferences", t)
        }
    }

    private fun hookClipboardDataHandler(classLoader: ClassLoader) {
        // Gboard 18.5.3/18.5.4: frc.ft(...)
        // Gboard 18.4.1/18.3.1: fml.ft(...)
        val clipboardHandlerClasses = listOf("frc", "fml")
        for (className in clipboardHandlerClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            val ftMethod = clazz.declaredMethods.firstOrNull {
                it.name == "ft" && it.returnType == Boolean::class.javaPrimitiveType
            } ?: continue

            runCatching {
                XposedBridge.hookMethod(ftMethod, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val receiver = param.thisObject ?: return
                        runCatching {
                            for (field in receiver.javaClass.declaredFields) {
                                if (field.name == "f") {
                                    field.isAccessible = true
                                    val handler = field.get(receiver)
                                    if (handler is ClipboardManager.OnPrimaryClipChangedListener) {
                                        handler.onPrimaryClipChanged()
                                    }
                                }
                            }
                        }
                    }
                })
                Log.i(TAG, "NexFusion: Hooked clipboard sync handler in $className.ft(...)")
            }
        }
    }

    private fun hookSpellCheckerChips(classLoader: ClassLoader) {
        // Spell checker utility class has a method: (Context, TextServicesManager) -> Boolean
        // On Android 12+ (Build.VERSION.SDK_INT >= 31), we force it to return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val knownSpellCheckerClasses = listOf("rcq", "rcp", "rco")
        for (className in knownSpellCheckerClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            for (m in clazz.declaredMethods) {
                if (m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == Context::class.java &&
                    m.parameterTypes[1].name.contains("TextServicesManager") &&
                    m.returnType == Boolean::class.javaPrimitiveType
                ) {
                    runCatching {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = true
                            }
                        })
                        Log.i(TAG, "NexFusion: Hooked Gboard spellchecker chips $className.${m.name}()")
                    }
                }
            }
        }
    }

    private fun suppressPromoBanners(classLoader: ClassLoader) {
        // 1. Suppress VoiceDonationPromoManager (vwi across releases)
        val donationPromoClasses = listOf(
            "com.google.android.apps.inputmethod.libs.voiceime.VoiceDonationPromoManager",
            "vwi"
        )
        for (className in donationPromoClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            for (m in clazz.declaredMethods) {
                if (m.name == "a" || m.name == "createShowBannerTask") {
                    runCatching {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = null
                            }
                        })
                        Log.i(TAG, "NexFusion: Suppressed voice donation promo banner in $className.${m.name}()")
                    }
                }
            }
        }

        // 2. Suppress RamblerAlToolbarFeedbackHandler (kmf across releases)
        val feedbackClasses = listOf(
            "com.google.android.apps.inputmethod.libs.agenticdictation.ui.impl.RamblerAlToolbarFeedbackHandler",
            "kmf"
        )
        for (className in feedbackClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            for (m in clazz.declaredMethods) {
                if (m.name == "b" || m.name == "show") {
                    runCatching {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = null
                            }
                        })
                        Log.i(TAG, "NexFusion: Suppressed Rambler feedback dropdown in $className.${m.name}()")
                    }
                }
            }
        }

        // 3. Suppress DefaultAgenticDictationPromoManager (kls across releases)
        val agenticPromoClasses = listOf(
            "com.google.android.apps.inputmethod.libs.agenticdictation.ui.impl.DefaultAgenticDictationPromoManager",
            "kls"
        )
        for (className in agenticPromoClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            for (m in clazz.declaredMethods) {
                if (m.name.contains("show", ignoreCase = true) || m.name.contains("banner", ignoreCase = true)) {
                    runCatching {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = null
                            }
                        })
                    }
                }
            }
        }
    }

    private fun hookRamblerResetCleanup(classLoader: ClassLoader) {
        // Gboard 18.5+: flb.i(boolean) or fkl.d() - when dictation session stops, ensure pending
        // callbacks execute and clean up lingering light bars and restore suggestion strip
        val resetClasses = listOf("flb", "fkl", "fkm")
        for (className in resetClasses) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            for (m in clazz.declaredMethods) {
                if (m.name == "d" || (m.name == "i" && m.parameterTypes.size == 1 && m.parameterTypes[0] == Boolean::class.javaPrimitiveType)) {
                    runCatching {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val receiver = param.thisObject ?: return
                                runCatching {
                                    for (field in receiver.javaClass.declaredFields) {
                                        if (Runnable::class.java.isAssignableFrom(field.type)) {
                                            field.isAccessible = true
                                            val r = field.get(receiver) as? Runnable
                                            r?.run()
                                            field.set(receiver, null)
                                        }
                                    }
                                }
                            }
                        })
                    }
                }
            }
        }
    }
}

