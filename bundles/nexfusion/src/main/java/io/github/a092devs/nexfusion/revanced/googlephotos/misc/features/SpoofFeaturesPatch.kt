package io.github.a092devs.nexfusion.revanced.googlephotos.misc.features

import android.content.pm.FeatureInfo
import android.os.Build
import io.github.a092devs.nexfusion.compat.XC_MethodHook
import io.github.a092devs.nexfusion.compat.XposedBridge
import io.github.a092devs.nexfusion.compat.XposedHelpers
import io.github.a092devs.nexfusion.patch
import org.luckypray.dexkit.wrap.DexMethod

val SpoofFeaturesPatch = patch(
    name = "Spoof features",
    description = "Spoofs the device to enable Google Pixel exclusive features, including unlimited storage.",
    use = false,
) {
    val buildInfo = mapOf(
        "BRAND" to "google",
        "MANUFACTURER" to "Google",
        "DEVICE" to "marlin",
        "PRODUCT" to "marlin",
        "BOARD" to "marlin",
        "HARDWARE" to "marlin",
        "MODEL" to "Pixel XL",
        "ID" to "QP1A.191005.007.A3",
        "TAGS" to "release-keys",
        "TYPE" to "user",
        "FINGERPRINT" to "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys"
    )

    val buildClazz = Build::class.java
    for ((k, v) in buildInfo) {
        runCatching { XposedHelpers.setStaticObjectField(buildClazz, k, v) }
    }

    val propSpoofs = mapOf(
        "ro.product.brand" to "google",
        "ro.product.manufacturer" to "Google",
        "ro.product.device" to "marlin",
        "ro.product.name" to "marlin",
        "ro.product.model" to "Pixel XL",
        "ro.product.board" to "marlin",
        "ro.product.system.brand" to "google",
        "ro.product.system.manufacturer" to "Google",
        "ro.product.system.device" to "marlin",
        "ro.product.system.name" to "marlin",
        "ro.product.system.model" to "Pixel XL",
        "ro.product.vendor.brand" to "google",
        "ro.product.vendor.manufacturer" to "Google",
        "ro.product.vendor.device" to "marlin",
        "ro.product.vendor.name" to "marlin",
        "ro.product.vendor.model" to "Pixel XL",
        "ro.product.odm.brand" to "google",
        "ro.product.odm.manufacturer" to "Google",
        "ro.product.odm.device" to "marlin",
        "ro.product.odm.name" to "marlin",
        "ro.product.odm.model" to "Pixel XL",
        "ro.build.fingerprint" to "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys",
        "ro.build.id" to "QP1A.191005.007.A3",
        "ro.build.tags" to "release-keys",
        "ro.build.type" to "user",
    )

    runCatching {
        val sysProps = classLoader.loadClass("android.os.SystemProperties")
        XposedBridge.hookAllMethods(sysProps, "get", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val key = param.args.firstOrNull() as? String ?: return
                val spoofed = propSpoofs[key] ?: return
                param.result = spoofed
            }
        })
    }

    val featuresToEnable = setOf(
        "com.google.android.apps.photos.NEXUS_PRELOAD",
        "com.google.android.apps.photos.nexus_preload",
    )

    val featuresToDisable = setOf(
        "com.google.android.apps.photos.PIXEL_2017_PRELOAD",
        "com.google.android.apps.photos.PIXEL_2018_PRELOAD",
        "com.google.android.apps.photos.PIXEL_2019_MIDYEAR_PRELOAD",
        "com.google.android.apps.photos.PIXEL_2019_PRELOAD",
        "com.google.android.feature.PIXEL_2020_MIDYEAR_EXPERIENCE",
        "com.google.android.feature.PIXEL_2020_EXPERIENCE",
        "com.google.android.feature.PIXEL_2021_MIDYEAR_EXPERIENCE",
        "com.google.android.feature.PIXEL_2021_EXPERIENCE",
        "com.google.android.feature.PIXEL_2022_MIDYEAR_EXPERIENCE",
        "com.google.android.feature.PIXEL_2022_EXPERIENCE",
        "com.google.android.feature.PIXEL_2023_MIDYEAR_EXPERIENCE",
        "com.google.android.feature.PIXEL_2023_EXPERIENCE",
        "com.google.android.feature.PIXEL_2024_MIDYEAR_EXPERIENCE",
        "com.google.android.feature.PIXEL_2024_EXPERIENCE",
        "com.google.android.feature.PIXEL_2025_MIDYEAR_EXPERIENCE",
        "com.google.android.feature.PIXEL_2025_EXPERIENCE",
        "com.google.android.feature.PIXEL_2026_MIDYEAR_EXPERIENCE",
        "com.google.android.feature.PIXEL_2026_EXPERIENCE",
        "com.google.android.feature.PIXEL_2027_MIDYEAR_EXPERIENCE",
        "com.google.android.feature.PIXEL_2027_EXPERIENCE",
    )

    val hook = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            val feature = param.args[0] as? String ?: return
            param.result = when (feature) {
                in featuresToEnable -> true
                in featuresToDisable -> false
                else -> return
            }
        }
    }

    runCatching {
        DexMethod("Landroid/app/ApplicationPackageManager;->hasSystemFeature(Ljava/lang/String;)Z")
            .hookMethod(hook)
    }
    runCatching {
        DexMethod("Landroid/app/ApplicationPackageManager;->hasSystemFeature(Ljava/lang/String;I)Z")
            .hookMethod(hook)
    }
    runCatching {
        val apm = classLoader.loadClass("android.app.ApplicationPackageManager")
        XposedBridge.hookAllMethods(apm, "getSystemAvailableFeatures", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val orig = param.result as? Array<*> ?: return
                val filtered = orig.filterIsInstance<FeatureInfo>()
                    .filter { it.name !in featuresToDisable }
                    .toMutableList()
                val existingNames = filtered.mapNotNullTo(HashSet()) { it.name }
                for (featName in featuresToEnable) {
                    if (featName !in existingNames) {
                        filtered.add(FeatureInfo().apply { name = featName })
                    }
                }
                param.result = filtered.toTypedArray()
            }
        })
    }
}
