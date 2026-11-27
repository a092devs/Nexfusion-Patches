package io.github.a092devs.nexfusion.nexfusion.gmail

import android.view.View
import android.view.ViewGroup
import io.github.a092devs.nexfusion.compat.XposedHelpers
import io.github.a092devs.nexfusion.PatcherLog
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.patch
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Constructor

// Ported from MrxSiN/GmailHideAds (GPL-3.0) and gnadgnaoh/SexAlloy (GPL-3.0).
// Removes sponsored conversation rows rendered in Gmail's Promotions and Social tabs.

val HideAds = patch(
    name = "Hide ads",
    description = "Removes the sponsored rows Gmail renders between conversations in Promotions and Social tabs.",
    use = true,
) {
    val constructors = ::adViewConstructors.dexMethodList
    if (constructors.isEmpty()) {
        PatcherLog.info { "No ad view constructor found in $AD_PACKAGE" }
        return@patch
    }

    val hooked = constructors.mapNotNull { dexMethod ->
        val constructor = dexMethod.toConstructorOrNull(classLoader) ?: return@mapNotNull null
        val cls = constructor.declaringClass
        if (!View::class.java.isAssignableFrom(cls)) {
            return@mapNotNull null
        }

        constructor.isAccessible = true
        constructor.hookMethod {
            after { param -> (param.thisObject as? View)?.hideAsAd() }
        }
        runCatching {
            val onFinishInflate = cls.getDeclaredMethod("onFinishInflate")
            onFinishInflate.isAccessible = true
            onFinishInflate.hookMethod {
                after { param -> (param.thisObject as? View)?.collapse() }
            }
        }
        cls.simpleName
    }

    if (hooked.isEmpty()) {
        PatcherLog.info { "No ad view among ${constructors.size} classes in $AD_PACKAGE" }
        return@patch
    }
    PatcherLog.info { "NexFusion: Gmail ad views hooked: ${hooked.joinToString()}" }
}

val GmailPatches = arrayOf(
    HideAds,
)

private fun DexMethod.toConstructorOrNull(classLoader: ClassLoader): Constructor<*>? {
    val clz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: return null
    val paramClasses = runCatching {
        paramTypeNames.map { name ->
            when (name) {
                "boolean" -> java.lang.Boolean.TYPE
                "byte" -> java.lang.Byte.TYPE
                "short" -> java.lang.Short.TYPE
                "int" -> java.lang.Integer.TYPE
                "long" -> java.lang.Long.TYPE
                "float" -> java.lang.Float.TYPE
                "double" -> java.lang.Double.TYPE
                "char" -> java.lang.Character.TYPE
                else -> classLoader.loadClass(name)
            }
        }.toTypedArray()
    }.getOrNull() ?: return null
    return runCatching {
        XposedHelpers.findConstructorExactIfExists(clz, *paramClasses)
    }.getOrNull()
}

private fun View.hideAsAd() {
    collapse()
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = v.collapse()
        override fun onViewDetachedFromWindow(v: View) = Unit
    })
}

private fun View.collapse() {
    if (visibility != View.GONE) visibility = View.GONE

    val lp = layoutParams ?: return
    if (lp.height != 0) {
        lp.height = 0
        if (lp is ViewGroup.MarginLayoutParams) {
            lp.topMargin = 0
            lp.bottomMargin = 0
        }
        layoutParams = lp
    }
}


