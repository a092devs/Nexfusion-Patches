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
    use = true,
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
        "DISPLAY" to "QP1A.191005.007.A3",
        "TAGS" to "release-keys",
        "TYPE" to "user",
        "FINGERPRINT" to "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys"
    )

    val buildClazz = Build::class.java
    for ((k, v) in buildInfo) {
        runCatching { XposedHelpers.setStaticObjectField(buildClazz, k, v) }
    }

    val featuresToEnable = setOf(
        "com.google.android.feature.PIXEL_EXPERIENCE",
        "com.google.android.feature.GOOGLE_BUILD",
        "com.google.android.feature.GOOGLE_EXPERIENCE",
        "com.google.android.apps.photos.NEXUS_PRELOAD",
        "com.google.android.apps.photos.nexus_preload",
        "com.google.android.apps.photos.PIXEL_PRELOAD",
        "com.google.android.apps.photos.PIXEL_2016_PRELOAD",
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
