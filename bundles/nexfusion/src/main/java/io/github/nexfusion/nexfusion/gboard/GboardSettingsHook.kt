package io.github.nexfusion.nexfusion.gboard

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.View
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XposedBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Injects "Advanced settings" into Gboard's main settings menu and removes promotional entries.
 *
 * Gboard obfuscates the preference classes per release. The class `PreferenceHeaderFragment` is kept, and
 * `CommonPreferenceFragment` is still in front of it, so the fragment hierarchy is followed from that name
 * instead of pinning the reshuffled method names. The current build (18.4.1) maps the interesting members
 * as follows, verified against its dex:
 *
 * - `CommonPreferenceFragment.f(Bundle)` — onCreate, after which the preference screen exists.
 * - `PreferenceFragmentCompat.n()` — getPreferenceScreen.
 * - `PreferenceFragmentCompat.aA(Preference)` — onPreferenceTreeClick.
 * - `PreferenceGroup.an(Preference)` — addPreference, `ao` — removePreference, `k()` — count, `o(int)`,
 *   `l(CharSequence)` — findPreference.
 * - `androidx.preference.Preference` — `P(String)` setKey, `V(CharSequence)` setTitle, `Q(int)` setOrder,
 *   `N(Drawable)` setIcon. The backing fields are `r`, `q`, `p` and `c`.
 *
 * The hook is deliberately tolerant: every step is guarded, and injection is idempotent, so a partially
 * changed build degrades to a missing entry instead of crashing Gboard.
 */
object GboardSettingsHook {

    private const val TAG = "NexFusion"
    private const val ADVANCED_SETTINGS_KEY = "gboard_patches_entry"
    private const val ADVANCED_SETTINGS_TITLE = "Advanced settings"
    private const val PREFERENCE_SCREEN_CLASS = "androidx.preference.PreferenceScreen"

    fun install(classLoader: ClassLoader) {
        val headerFragmentClass = runCatching {
            classLoader.loadClass("com.google.android.libraries.inputmethod.preferencewidgets.PreferenceHeaderFragment")
        }.getOrNull()

        val commonFragmentClass = runCatching {
            classLoader.loadClass("com.google.android.libraries.inputmethod.preferencewidgets.CommonPreferenceFragment")
        }.getOrNull()

        val anchor = headerFragmentClass ?: commonFragmentClass
        if (anchor == null) {
            Log.w(TAG, "GboardSettingsHook: neither PreferenceHeaderFragment nor CommonPreferenceFragment found")
            return
        }

        val hierarchy = generateSequence(anchor) { it.superclass }
            .takeWhile { it != Any::class.java }
            .toList()

        // Stop at the class implementing the androidx preference fragment, so framework fragment methods are
        // not hooked for every Gboard screen.
        val preferenceBaseIndex = hierarchy.indexOfFirst { clazz ->
            clazz.declaredMethods.any { method ->
                method.parameterCount == 0 && method.returnType.name == PREFERENCE_SCREEN_CLASS
            }
        }
        val hookClasses = if (preferenceBaseIndex >= 0) {
            hierarchy.take(preferenceBaseIndex + 1)
        } else {
            hierarchy.take(4)
        }

        val injectionHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val fragment = param.thisObject ?: return
                if (!fragment.javaClass.name.contains("PreferenceHeaderFragment")) return

                val context = resolveContext(fragment)
                    ?: (param.args.firstOrNull { it is Context } as? Context)
                    ?: (param.args.firstOrNull { it is View } as? View)?.context
                    ?: return

                val explicitGroup = param.args.firstOrNull { arg ->
                    arg != null && arg.javaClass.name == PREFERENCE_SCREEN_CLASS
                }

                runCatching {
                    injectAdvancedSettingsPreference(fragment, context, classLoader, explicitGroup)
                }.onFailure { error ->
                    Log.w(TAG, "NexFusion: failed to inject the Advanced settings preference", error)
                }
            }
        }

        var injectionHooks = 0
        for (clazz in hookClasses) {
            for (method in clazz.declaredMethods) {
                if (method.returnType != Void.TYPE || Modifier.isAbstract(method.modifiers)) continue
                if (!isLifecycleCandidate(method.parameterTypes)) continue

                runCatching {
                    XposedBridge.hookMethod(method, injectionHook)
                    injectionHooks++
                }.onFailure {
                    Log.w(TAG, "NexFusion: could not hook ${clazz.name}.${method.name}", it)
                }
            }
        }

        val clickHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val preference = param.args.getOrNull(0) ?: return
                val key = getPreferenceKey(preference)
                val title = getPreferenceTitle(preference)
                if (key != ADVANCED_SETTINGS_KEY && !title.contains(ADVANCED_SETTINGS_TITLE, ignoreCase = true)) {
                    return
                }

                Log.i(TAG, "NexFusion: intercepted the Advanced settings click")
                val context = resolveContext(param.thisObject) ?: resolveContext(preference)
                val activity = resolveActivity(context)
                if (activity != null) {
                    activity.runOnUiThread {
                        GboardSettingsDialog.show(activity)
                    }
                } else {
                    Log.w(TAG, "NexFusion: could not resolve an Activity for the Advanced settings dialog")
                }

                if (param.method is Method && (param.method as Method).returnType == java.lang.Boolean.TYPE) {
                    param.result = true
                }
            }
        }

        var clickHooks = 0
        for (clazz in hookClasses) {
            for (method in clazz.declaredMethods) {
                if (method.returnType != java.lang.Boolean.TYPE || method.parameterTypes.size != 1) continue
                if (!method.parameterTypes[0].name.contains("Preference")) continue

                runCatching {
                    XposedBridge.hookMethod(method, clickHook)
                    clickHooks++
                    Log.i(TAG, "NexFusion: hooked ${clazz.name}.${method.name} for preference clicks")
                }.onFailure {
                    Log.w(TAG, "NexFusion: could not hook ${clazz.name}.${method.name}", it)
                }
            }
        }

        Log.i(
            TAG,
            "NexFusion: GboardSettingsHook installed ($injectionHooks injection hooks, $clickHooks click hooks) " +
                "over ${hookClasses.joinToString { it.name }}",
        )
    }

    private fun isLifecycleCandidate(parameterTypes: Array<Class<*>>): Boolean = when {
        parameterTypes.isEmpty() -> true
        parameterTypes.size == 1 && parameterTypes[0] == Bundle::class.java -> true
        parameterTypes.size == 1 && parameterTypes[0].name == PREFERENCE_SCREEN_CLASS -> true
        parameterTypes.size == 2 &&
            parameterTypes[0] == View::class.java &&
            parameterTypes[1] == Bundle::class.java -> true
        else -> false
    }

    private fun injectAdvancedSettingsPreference(
        fragment: Any,
        context: Context,
        classLoader: ClassLoader,
        explicitGroup: Any? = null,
    ) {
        if (!fragment.javaClass.name.contains("PreferenceHeaderFragment")) return

        val screen = explicitGroup ?: getPreferenceScreen(fragment) ?: return
        removeBloatPreferences(screen)

        if (findPreference(screen, ADVANCED_SETTINGS_KEY) != null) return

        val preferenceClass = runCatching {
            classLoader.loadClass("androidx.preference.Preference")
        }.getOrNull() ?: return

        val preference = runCatching {
            preferenceClass.getConstructor(Context::class.java).newInstance(context)
        }.getOrNull() ?: return

        setPreferenceKey(preference, ADVANCED_SETTINGS_KEY)
        setPreferenceTitle(preference, ADVANCED_SETTINGS_TITLE)
        setPreferenceOrder(preference, 9999)

        val isDark = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val sparkleColor = if (isDark) 0xFFD0BCFF.toInt() else 0xFF65558F.toInt()
        setPreferenceIcon(preference, GboardSettingsDialog.SparkleDrawable(sparkleColor))

        addPreferenceToScreen(screen, preference)
        Log.i(TAG, "NexFusion: Gboard 'Advanced settings' injected into ${fragment.javaClass.name}")
    }

    private fun removeBloatPreferences(screen: Any) {
        val count = getPreferenceCount(screen)
        for (index in count - 1 downTo 0) {
            val preference = getPreferenceAtIndex(screen, index) ?: continue
            val title = getPreferenceTitle(preference)
            val key = getPreferenceKey(preference)
            if (title.contains("Rate Gboard", ignoreCase = true) ||
                title.contains("Rate us", ignoreCase = true) ||
                title.contains("Help & feedback", ignoreCase = true) ||
                title.contains("Help and feedback", ignoreCase = true) ||
                (title.contains("Feedback", ignoreCase = true) && !title.contains("haptic", ignoreCase = true)) ||
                key.contains("rate", ignoreCase = true) ||
                key.contains("feedback", ignoreCase = true)
            ) {
                removePreferenceFromScreen(screen, preference)
                Log.i(TAG, "NexFusion: removed bloat preference '$title' ($key)")
            }
        }
    }

    fun resolveContext(obj: Any?): Context? {
        if (obj == null) return null
        if (obj is Context) return obj
        if (obj is View) return obj.context

        runCatching { obj.javaClass.getMethod("getActivity").invoke(obj) as? Context }.getOrNull()?.let { return it }
        runCatching { obj.javaClass.getMethod("getContext").invoke(obj) as? Context }.getOrNull()?.let { return it }
        runCatching { (obj.javaClass.getMethod("getView").invoke(obj) as? View)?.context }.getOrNull()?.let { return it }
        runCatching { obj.javaClass.getMethod("w").invoke(obj) as? Context }.getOrNull()?.let { return it }
        runCatching { obj.javaClass.getMethod("x").invoke(obj) as? Context }.getOrNull()?.let { return it }

        var current: Class<*>? = obj.javaClass
        while (current != null && current != Any::class.java) {
            for (field in current.declaredFields) {
                if (Context::class.java.isAssignableFrom(field.type)) {
                    runCatching {
                        field.isAccessible = true
                        field.get(obj) as? Context
                    }.getOrNull()?.let { return it }
                }
            }
            current = current.superclass
        }
        return null
    }

    fun resolveActivity(context: Context?): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        if (current is Activity) return current

        // Last resort: pick the current resumed Activity from ActivityThread.
        runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
            val activitiesField = activityThreadClass.getDeclaredField("mActivities").apply { isAccessible = true }
            val activities = activitiesField.get(activityThread) as? Map<*, *> ?: return null
            for (record in activities.values) {
                if (record == null) continue
                val activityField = record.javaClass.getDeclaredField("activity").apply { isAccessible = true }
                val activity = activityField.get(record) as? Activity
                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                    return activity
                }
            }
        }
        return null
    }

    private fun getPreferenceScreen(fragment: Any): Any? {
        val method = fragment.javaClass.methods.firstOrNull {
            (it.name == "getPreferenceScreen" || it.name == "n") &&
                it.parameterTypes.isEmpty() &&
                it.returnType.name == PREFERENCE_SCREEN_CLASS
        }
        return method?.let { runCatching { it.invoke(fragment) }.getOrNull() }
    }

    private fun getPreferenceCount(screen: Any): Int {
        val method = screen.javaClass.methods.firstOrNull {
            (it.name == "getPreferenceCount" || it.name == "k") &&
                it.parameterTypes.isEmpty() &&
                it.returnType == Int::class.javaPrimitiveType
        }
        return (method?.let { runCatching { it.invoke(screen) }.getOrNull() } as? Int) ?: 0
    }

    private fun getPreferenceAtIndex(screen: Any, index: Int): Any? {
        val method = screen.javaClass.methods.firstOrNull {
            (it.name == "getPreference" || it.name == "o") &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Int::class.javaPrimitiveType
        }
        return method?.let { runCatching { it.invoke(screen, index) }.getOrNull() }
    }

    private fun findPreference(screen: Any, key: String): Any? {
        val method = screen.javaClass.methods.firstOrNull {
            (it.name == "findPreference" || it.name == "l") &&
                it.parameterTypes.size == 1 &&
                CharSequence::class.java.isAssignableFrom(it.parameterTypes[0])
        }
        return method?.let { runCatching { it.invoke(screen, key) }.getOrNull() }
    }

    private fun addPreferenceToScreen(screen: Any, preference: Any) {
        val method = screen.javaClass.methods.firstOrNull {
            (it.name == "addPreference" || it.name == "an") &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0].name.contains("Preference")
        }
        method?.let { runCatching { it.invoke(screen, preference) } }
    }

    private fun removePreferenceFromScreen(screen: Any, preference: Any) {
        val method = screen.javaClass.methods.firstOrNull {
            (it.name == "removePreference" || it.name == "ao") &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0].name.contains("Preference")
        }
        method?.let { runCatching { it.invoke(screen, preference) } }
    }

    private fun setPreferenceKey(preference: Any, key: String) {
        val method = preference.javaClass.methods.firstOrNull {
            (it.name == "setKey" || it.name == "P") &&
                it.returnType == Void.TYPE &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == String::class.java
        }
        if (method != null && runCatching { method.invoke(preference, key) }.isSuccess) return

        setField(preference, "r", String::class.java, key)
    }

    private fun setPreferenceTitle(preference: Any, title: CharSequence) {
        val method = preference.javaClass.methods.firstOrNull {
            (it.name == "setTitle" || it.name == "V") &&
                it.returnType == Void.TYPE &&
                it.parameterTypes.size == 1 &&
                CharSequence::class.java.isAssignableFrom(it.parameterTypes[0])
        }
        if (method != null && runCatching { method.invoke(preference, title) }.isSuccess) return

        setField(preference, "q", CharSequence::class.java, title)
    }

    private fun setPreferenceOrder(preference: Any, order: Int) {
        val method = preference.javaClass.methods.firstOrNull {
            (it.name == "setOrder" || it.name == "Q") &&
                it.returnType == Void.TYPE &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Int::class.javaPrimitiveType
        }
        if (method != null && runCatching { method.invoke(preference, order) }.isSuccess) return

        setField(preference, "p", Int::class.javaPrimitiveType!!, order)
    }

    private fun setPreferenceIcon(preference: Any, icon: Drawable) {
        val method = preference.javaClass.methods.firstOrNull {
            (it.name == "setIcon" || it.name == "N") &&
                it.returnType == Void.TYPE &&
                it.parameterTypes.size == 1 &&
                Drawable::class.java.isAssignableFrom(it.parameterTypes[0])
        }
        if (method != null && runCatching { method.invoke(preference, icon) }.isSuccess) return

        setField(preference, "c", Drawable::class.java, icon)
    }

    private fun setField(instance: Any, name: String, type: Class<*>, value: Any?): Boolean {
        var current: Class<*>? = instance.javaClass
        while (current != null && current != Any::class.java) {
            val field = current.declaredFields.firstOrNull { it.name == name && it.type == type }
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.set(instance, value)
                }.isSuccess
            }
            current = current.superclass
        }
        return false
    }

    private fun getPreferenceKey(preference: Any): String {
        fieldValue(preference, "r", String::class.java)?.let { return it.toString() }

        return runCatching {
            preference.javaClass.methods.firstOrNull {
                it.name == "getKey" &&
                    it.parameterTypes.isEmpty() &&
                    it.returnType == String::class.java
            }?.invoke(preference)?.toString()
        }.getOrNull().orEmpty()
    }

    private fun getPreferenceTitle(preference: Any): String {
        fieldValue(preference, "q", CharSequence::class.java)?.let { return it.toString() }

        return runCatching {
            preference.javaClass.methods.firstOrNull {
                it.name == "getTitle" &&
                    it.parameterTypes.isEmpty() &&
                    CharSequence::class.java.isAssignableFrom(it.returnType)
            }?.invoke(preference)?.toString()
        }.getOrNull().orEmpty()
    }

    private fun fieldValue(instance: Any, name: String, type: Class<*>): Any? {
        var current: Class<*>? = instance.javaClass
        while (current != null && current != Any::class.java) {
            val field = current.declaredFields.firstOrNull { it.name == name && it.type == type }
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.get(instance)
                }.getOrNull()
            }
            current = current.superclass
        }
        return null
    }
}

