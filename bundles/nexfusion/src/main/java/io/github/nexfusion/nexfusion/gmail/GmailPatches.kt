package io.github.nexfusion.nexfusion.gmail

import android.view.View
import android.view.ViewGroup
import io.github.nexfusion.compat.XposedHelpers
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.hookMethod
import io.github.nexfusion.patch
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Constructor

// Ported from MrxSiN/GmailHideAds (GPL-3.0) and gnadgnaoh/SexAlloy (GPL-3.0).
// Removes sponsored conversation rows rendered in Gmail's Promotions and Social tabs.

val HideAds = patch(
    name = "Hide ads",
    description = "Removes the sponsored rows Gmail renders between conversations in Promotions and Social tabs.",
) {
    val constructors = ::adViewConstructors.dexMethodList
    check(constructors.isNotEmpty()) { "No ad view constructor found in $AD_PACKAGE" }

    val hooked = constructors.mapNotNull { dexMethod ->
        val constructor = dexMethod.toConstructorOrNull(classLoader) ?: return@mapNotNull null
        if (!View::class.java.isAssignableFrom(constructor.declaringClass)) {
            return@mapNotNull null
        }

        constructor.isAccessible = true
        constructor.hookMethod {
            after { param -> (param.thisObject as? View)?.hideAsAd() }
        }
        constructor.declaringClass.simpleName
    }

    check(hooked.isNotEmpty()) { "No ad view among ${constructors.size} classes in $AD_PACKAGE" }
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


