package io.github.nexfusion.nexfusion.crunchyroll

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.view.View
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XC_MethodReplacement
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.hookMethod
import io.github.nexfusion.patch

val EnablePictureInPicture = patch(
    name = "Enable Picture-in-Picture",
    description = "Enables Crunchyroll's native Picture-in-Picture with contextual skip intro and skip credits controls.",
) {
    // 1. Enable Crunchyroll's native PiP provider configuration
    runCatching {
        PictureInPictureProviderFingerprint.hookMethod {
            before { param ->
                val provider = param.thisObject ?: return@before
                var hooked = 0
                for (m in provider.javaClass.declaredMethods) {
                    if (m.returnType == java.lang.Boolean.TYPE && m.parameterCount == 0) {
                        runCatching {
                            XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(true))
                            hooked++
                        }
                    }
                }
                for (f in provider.javaClass.declaredFields) {
                    val fieldClass = f.type
                    if (fieldClass != Any::class.java && !fieldClass.isPrimitive && !fieldClass.name.startsWith("android.")) {
                        for (m in fieldClass.declaredMethods) {
                            if (m.returnType == java.lang.Boolean.TYPE && m.parameterCount == 0) {
                                runCatching {
                                    XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(true))
                                    hooked++
                                }
                            }
                        }
                    }
                }
                PatcherLog.info { "Crunchyroll: PiP provider config hooked ($hooked accessors)" }
            }
        }
    }.onFailure { PatcherLog.info(it) { "Crunchyroll: PiP provider config hook failed" } }

    // 2. Picture-in-Picture auto-enter & action manager registration
    runCatching {
        PictureInPictureAutoEnterFingerprint.hookMethod {
            before { param ->
                PictureInPicture.setManager(param.thisObject)
            }
        }
    }.onFailure { PatcherLog.info(it) { "Crunchyroll: PiP auto enter hook failed" } }

    // 3. Contextual Skip Segment in PiP forward button
    runCatching {
        PictureInPictureSkipForwardFingerprint.hookMethod {
            before { param ->
                if (PictureInPicture.skipSegmentIfAvailable()) {
                    param.result = null
                }
            }
        }
    }.onFailure { PatcherLog.info(it) { "Crunchyroll: PiP skip forward hook failed" } }

    // 4. Update skip state when skip intro / credits button is shown
    runCatching {
        SkipSegmentButtonFingerprint.hookMethod {
            before { param ->
                param.args.firstOrNull()?.let { PictureInPicture.updateSkipState(it) }
            }
        }
    }.onFailure { PatcherLog.info(it) { "Crunchyroll: Skip segment button hook failed" } }

    // 5. WatchScreenActivity onUserLeaveHint seamless PiP entry
    runCatching {
        WatchScreenUserLeaveHintFingerprint.hookMethod {
            before { param ->
                val activity = param.thisObject as? Activity ?: return@before
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching {
                        val playerView = runCatching {
                            activity.javaClass.getMethod("getPlayerView").invoke(activity) as? View
                        }.getOrNull()
                        val isPlaying = playerView?.let { pv ->
                            runCatching { pv.javaClass.getMethod("isPlaying").invoke(pv) as? Boolean }.getOrNull()
                        } ?: true

                        if (isPlaying && !activity.isInPictureInPictureMode) {
                            activity.enterPictureInPictureMode(
                                PictureInPictureParams.Builder().build()
                            )
                        }
                    }
                }
            }
        }
    }.onFailure { PatcherLog.info(it) { "Crunchyroll: WatchScreenUserLeaveHint hook failed" } }

    // Also guard offline and adaptive activities
    runCatching {
        val extraActivities = listOf(
            "com.crunchyroll.watchscreen.screen.offline.OfflineWatchScreenActivity",
            "com.crunchyroll.watchscreen.screen.xr.AdaptiveWatchScreenActivity",
        )
        for (activityName in extraActivities) {
            val actClass = runCatching { classLoader.loadClass(activityName) }.getOrNull() ?: continue
            val leaveHintMethod = runCatching { actClass.getDeclaredMethod("onUserLeaveHint") }.getOrNull() ?: continue
            XposedBridge.hookMethod(leaveHintMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        runCatching {
                            if (!activity.isInPictureInPictureMode) {
                                activity.enterPictureInPictureMode(
                                    PictureInPictureParams.Builder().build()
                                )
                            }
                        }
                    }
                }
            })
        }
    }.onFailure { PatcherLog.info(it) { "Crunchyroll: extra playback activities hook failed" } }
}

val RemoveFromContinueWatching = patch(
    name = "Remove from Continue Watching",
    description = "Adds an option in the overflow menu to remove titles from Continue Watching.",
) {
    // Initialize preferences cache early if appContext is ready
    runCatching {
        appContext?.let { RemoveContinue.initialize(it) }
    }

    // Application onCreate hook to guarantee context initialization
    runCatching {
        CrunchyrollApplicationOnCreateFingerprint.hookMethod {
            after { param ->
                (param.thisObject as? Context)?.let { RemoveContinue.initialize(it) }
            }
        }
    }.onFailure { PatcherLog.info(it) { "Crunchyroll: Application onCreate hook failed" } }

    // Hook Continue Watching item overflow menu to add "Remove"
    runCatching {
        ContinueWatchingOverflowMenuFingerprint.hookMethod {
            before { param ->
                RemoveContinue.captureData(param.args.getOrNull(0))
            }
            after { param ->
                val original = param.result as? List<*> ?: return@after
                param.result = RemoveContinue.addRemoveContinue(original, param.thisObject)
            }
        }
    }.onFailure { PatcherLog.info(it) { "Crunchyroll: Continue Watching overflow menu hook failed" } }

    // Filter hidden continue watching items from feed mapping
    runCatching {
        ContinueWatchingFeedMapperFingerprint.hookMethod {
            before { param ->
                RemoveContinue.filterFeed(param.thisObject)
            }
        }
    }.onFailure { PatcherLog.info(it) { "Crunchyroll: Continue Watching feed mapper hook failed" } }
}

val CrunchyrollPatches = arrayOf(
    EnablePictureInPicture,
    RemoveFromContinueWatching,
)

