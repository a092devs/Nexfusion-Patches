@file:Suppress("DEPRECATION")

package io.github.nexfusion

import android.app.Fragment
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.InflateException
import android.view.LayoutInflater
import android.view.View
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.compat.XposedHelpers
import java.lang.reflect.Constructor
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolve module classes at framework inflation entry points. Changing the host's parent
 * ClassLoader would make ART fall back to Java class lookup for the entire application.
 */
fun hookExtensionClassLoading(moduleLoader: ClassLoader) {
    val viewConstructors = ConcurrentHashMap<String, Constructor<out View>>()
    val hasViewContext = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val createView = if (hasViewContext) {
        LayoutInflater::class.java.getDeclaredMethod(
            "createView", Context::class.java, String::class.java, String::class.java,
            AttributeSet::class.java
        )
    } else {
        LayoutInflater::class.java.getDeclaredMethod(
            "createView", String::class.java, String::class.java, AttributeSet::class.java
        )
    }

    XposedBridge.hookMethod(createView, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            val offset = if (hasViewContext) 1 else 0
            val prefix = param.args[offset + 1] as String?
            val name = prefix.orEmpty() + (param.args[offset] as String)
            if (!name.startsWith("app.morphe.extension.")) return
            val inflater = param.thisObject as LayoutInflater
            val context = if (offset == 1) param.args[0] as Context else {
                (XposedHelpers.getObjectField(inflater, "mConstructorArgs") as? Array<*>)?.get(0) as? Context
                    ?: inflater.context
            }
            val attrs = param.args[offset + 2] as AttributeSet?
            param.inflateExtension(name) {
                val constructor = viewConstructors.getOrPut(name) {
                    moduleLoader.loadClass(name).asSubclass(View::class.java)
                        .getConstructor(Context::class.java, AttributeSet::class.java)
                }
                if (inflater.filter?.onLoadClass(constructor.declaringClass) == false) {
                    throw InflateException("Class not allowed to be inflated: $name")
                }
                constructor.newInstance(context, attrs)
            }
        }
    })

    val drawableInflaterClass = runCatching {
        XposedHelpers.findClass("android.graphics.drawable.DrawableInflater", null)
    }.getOrNull()

    if (drawableInflaterClass != null) {
        val inflateFromClass = runCatching {
            XposedHelpers.findMethodExact(drawableInflaterClass, "inflateFromClass", String::class.java)
        }.getOrNull()

        if (inflateFromClass != null) {
            XposedBridge.hookMethod(inflateFromClass, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val name = param.args[0] as String
                    if (!name.startsWith("app.morphe.extension.")) return
                    param.inflateExtension(name) {
                        moduleLoader.loadClass(name).asSubclass(Drawable::class.java)
                            .getConstructor().newInstance()
                    }
                }
            })
        }
    }

    // FragmentManager restores saved module fragments by name through the host context.
    val instantiateMethod = runCatching {
        Fragment::class.java.getDeclaredMethod(
            "instantiate", Context::class.java, String::class.java, Bundle::class.java
        )
    }.getOrNull()

    if (instantiateMethod != null) {
        XposedBridge.hookMethod(instantiateMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val name = param.args[1] as String
                if (!name.startsWith("app.morphe.extension.")) return
                try {
                    val fragment = moduleLoader.loadClass(name).asSubclass(Fragment::class.java)
                        .getConstructor().newInstance()
                    (param.args[2] as? Bundle)?.let { args ->
                        args.classLoader = fragment.javaClass.classLoader
                        fragment.arguments = args
                    }
                    param.result = fragment
                } catch (e: Exception) {
                    param.throwable = Fragment.InstantiationException("Unable to instantiate fragment $name", e)
                }
            }
        })
    }
}

private inline fun MethodHookParam.inflateExtension(name: String, create: () -> Any) {
    try {
        result = create()
    } catch (e: Exception) {
        throwable = InflateException("Error inflating module class $name", e)
    }
}

