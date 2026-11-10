package io.github.nexfusion.nexfusion.gboard

import android.content.Context
import android.util.Log
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XposedBridge

/**
 * Bypasses Gboard internal Google Play services and speech component signature whitelist checks.
 * Ported from Akshay Kadam's PixelBoard (GPL-3.0).
 *
 * Known signature verification classes across Gboard releases:
 * - Gboard 18.5.3 / 18.5.4: `sfr.a(Context, String): boolean`
 * - Gboard 18.4.1: `ryi.a(Context, String): boolean`
 * - Gboard 18.3.1 release: `rvl.a(Context, String): boolean`
 * - Gboard 18.3.1 beta: `ajpz.a(Context, String): boolean`
 * - Gboard 18.0.3: `rpv.a(Context, String): boolean`
 */
object GboardSignatureBypassHook {

    private const val TAG = "NexFusion"

    private val SIGNATURE_CHECK_CLASSES = listOf(
        "sfr",  // 18.5.3 & 18.5.4 release
        "ryi",  // 18.4.1 release
        "rvl",  // 18.3.1 release
        "ajpz", // 18.3.1 beta
        "rpv",  // 18.0.3 release
    )

    fun install(classLoader: ClassLoader) {
        var hooked = 0
        for (className in SIGNATURE_CHECK_CLASSES) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "a" && it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == Context::class.java &&
                    it.parameterTypes[1] == String::class.java &&
                    it.returnType == Boolean::class.javaPrimitiveType
            } ?: continue

            runCatching {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                })
                hooked++
                Log.i(TAG, "NexFusion: Hooked Gboard signature bypass in $className.a(Context, String)")
            }
        }
        if (hooked > 0) {
            Log.i(TAG, "NexFusion: Gboard signature bypass active on $hooked class(es)")
        }
    }
}

