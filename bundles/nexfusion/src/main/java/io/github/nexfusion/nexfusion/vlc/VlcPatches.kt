package io.github.nexfusion.nexfusion.vlc

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import java.util.ArrayList

val VlcAmoledBlackTheme = patch(
    name = "AMOLED black theme",
    description = "Repaints every dark surface — background, cards, dialogs, popups, header and the navigation bar — to true black.",
) {
    runCatching {
        val activityClass = classLoader.loadClass("android.app.Activity")
        XposedBridge.hookAllMethods(
            activityClass,
            "onPostCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    if (activity.packageName != "org.videolan.vlc") return
                    val decor = activity.window?.decorView ?: return
                    val nightMode = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    if (nightMode) {
                        runCatching {
                            activity.window.navigationBarColor = Color.BLACK
                            activity.window.statusBarColor = Color.BLACK
                            decor.setBackgroundColor(Color.BLACK)
                        }
                    }
                }
            }
        )
    }.onFailure { PatcherLog.error(it) { "Failed to hook AmoledBlackTheme" } }
}

val VlcSkipWhatsNew = patch(
    name = "Skip \"What's New\" dialog",
    description = "Suppresses the What's New changelog dialog shown after app updates.",
) {
    runCatching {
        val clazz = classLoader.loadClass("org.videolan.vlc.util.WhatsNewManager")
        XposedBridge.hookAllMethods(
            clazz,
            "launchIfNeeded",
            XC_MethodReplacement.returnConstant(null)
        )
    }.onFailure { PatcherLog.error(it) { "Failed to hook WhatsNewManager.launchIfNeeded" } }
}

val VlcUnlockFeatureFlags = patch(
    name = "Unlock experimental features",
    description = "Enables every experimental feature flag regardless of its stored value.",
    use = false,
) {
    runCatching {
        val clazz = classLoader.loadClass("org.videolan.vlc.util.FeatureFlagManager")
        XposedBridge.hookAllMethods(
            clazz,
            "isEnabled",
            XC_MethodReplacement.returnConstant(true)
        )
    }.onFailure { PatcherLog.error(it) { "Failed to hook FeatureFlagManager.isEnabled" } }
}

val VlcForcePassthrough = patch(
    name = "Force audio passthrough",
    description = "Enables bitstream audio passthrough (digital output) to an external DAC or receiver.",
    use = false,
) {
    runCatching {
        val clazz = classLoader.loadClass("org.videolan.resources.VLCOptions")
        XposedBridge.hookAllMethods(
            clazz,
            "isAudioDigitalOutputEnabled",
            XC_MethodReplacement.returnConstant(true)
        )
    }.onFailure { PatcherLog.error(it) { "Failed to hook VLCOptions.isAudioDigitalOutputEnabled" } }
}

val VlcForceAaudio = patch(
    name = "Force AAudio output",
    description = "Locks the audio output to AAudio (low latency), overriding the device preference.",
    use = false,
) {
    runCatching {
        val clazz = classLoader.loadClass("org.videolan.resources.VLCOptions")
        XposedBridge.hookAllMethods(
            clazz,
            "getAout",
            XC_MethodReplacement.returnConstant(null)
        )
    }.onFailure { PatcherLog.error(it) { "Failed to hook VLCOptions.getAout" } }
}

val VlcRaiseNetworkCache = patch(
    name = "Raise network cache",
    description = "Buffers 3 seconds of stream ahead to reduce stalls on flaky networks.",
    use = false,
) {
    runCatching {
        val clazz = classLoader.loadClass("org.videolan.resources.VLCOptions")
        XposedBridge.hookAllMethods(
            clazz,
            "getLibOptions",
            object : XC_MethodHook() {
                @Suppress("UNCHECKED_CAST")
                override fun afterHookedMethod(param: MethodHookParam) {
                    val list = param.result as? ArrayList<String> ?: return
                    list.add("--network-caching=3000")
                }
            }
        )
    }.onFailure { PatcherLog.error(it) { "Failed to hook raise network cache" } }
}

val VlcDisableTimeStretch = patch(
    name = "Disable audio time-stretch",
    description = "Forces time-stretching off for artifact-free playback at 1x speed.",
    use = false,
) {
    runCatching {
        val clazz = classLoader.loadClass("org.videolan.resources.VLCOptions")
        XposedBridge.hookAllMethods(
            clazz,
            "getLibOptions",
            object : XC_MethodHook() {
                @Suppress("UNCHECKED_CAST")
                override fun afterHookedMethod(param: MethodHookParam) {
                    val list = param.result as? ArrayList<String> ?: return
                    list.add("--no-audio-time-stretch")
                }
            }
        )
    }.onFailure { PatcherLog.error(it) { "Failed to hook disable time stretch" } }
}

val VlcForceReplayGain = patch(
    name = "Force ReplayGain",
    description = "Normalizes loudness across tracks using track mode with peak protection.",
    use = false,
) {
    runCatching {
        val clazz = classLoader.loadClass("org.videolan.resources.VLCOptions")
        XposedBridge.hookAllMethods(
            clazz,
            "getLibOptions",
            object : XC_MethodHook() {
                @Suppress("UNCHECKED_CAST")
                override fun afterHookedMethod(param: MethodHookParam) {
                    val list = param.result as? ArrayList<String> ?: return
                    list.add("--audio-replay-gain-mode=track")
                    list.add("--audio-replay-gain-preamp=0.0")
                    list.add("--audio-replay-gain-default=-7.0")
                    list.add("--audio-replay-gain-peak-protection")
                }
            }
        )
    }.onFailure { PatcherLog.error(it) { "Failed to hook force replay gain" } }
}

val VlcVerboseLogging = patch(
    name = "Verbose libVLC logging",
    description = "Sets libVLC to maximum verbosity (-vvv) for troubleshooting playback.",
    use = false,
) {
    runCatching {
        val clazz = classLoader.loadClass("org.videolan.resources.VLCOptions")
        XposedBridge.hookAllMethods(
            clazz,
            "getLibOptions",
            object : XC_MethodHook() {
                @Suppress("UNCHECKED_CAST")
                override fun afterHookedMethod(param: MethodHookParam) {
                    val list = param.result as? ArrayList<String> ?: return
                    list.add("-vvv")
                }
            }
        )
    }.onFailure { PatcherLog.error(it) { "Failed to hook verbose logging" } }
}

val VlcPatches = arrayOf(
    VlcAmoledBlackTheme,
    VlcSkipWhatsNew,
    VlcUnlockFeatureFlags,
    VlcForcePassthrough,
    VlcForceAaudio,
    VlcRaiseNetworkCache,
    VlcDisableTimeStretch,
    VlcForceReplayGain,
    VlcVerboseLogging,
)
