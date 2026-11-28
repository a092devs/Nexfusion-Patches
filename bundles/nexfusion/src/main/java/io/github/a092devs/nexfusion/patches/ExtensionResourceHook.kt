package io.github.a092devs.nexfusion.patches

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.webkit.WebView
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.ResourceType
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.shared.Utils
import io.github.a092devs.nexfusion.addModuleAssets
import io.github.a092devs.nexfusion.callMethod
import io.github.a092devs.nexfusion.compat.XC_MethodHook
import io.github.a092devs.nexfusion.compat.XC_MethodReplacement
import io.github.a092devs.nexfusion.compat.XposedBridge
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.patches.BuildConfig

/** Gives the Morphe extension code the app context. Must run before any other patch of this bundle. */
val ExtensionContextHook = patch {
    Utils.setContext(appContext)
}

/** Makes the bundle's resources available to the Morphe extension code in every activity. */
private var resourceHookInitialized = false

val ExtensionResourceHook = patch {
    if (resourceHookInitialized) return@patch
    resourceHookInitialized = true
    appContext.addModuleAssets()

    runCatching {
        val getIdMethod = ResourceUtils::class.java.getDeclaredMethod(
            "getIdentifier",
            Context::class.java,
            ResourceType::class.java,
            String::class.java,
        )
        XposedBridge.hookMethod(
            getIdMethod,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val ctx = param.args[0] as? Context ?: return
                    val type = param.args[1] as? ResourceType
                    val name = param.args[2] as? String ?: return
                    val result = runCatching {
                        ctx.resources.getIdentifier(name, type?.type, BuildConfig.APPLICATION_ID)
                    }.getOrDefault(0)
                    if (result != 0) {
                        param.result = result
                    }
                }
            },
        )
    }

    runCatching {
        val versionMethod = Utils::class.java.getDeclaredMethod("getPatchesReleaseVersion")
        XposedBridge.hookMethod(versionMethod, XC_MethodReplacement.returnConstant(BuildConfig.PATCH_VERSION))
    }

    appContext.callMethod(
        "registerActivityLifecycleCallbacks", object : Application.ActivityLifecycleCallbacks {
            var handleWebView: Boolean = false

            override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
                Logger.printDebug { "onActivityCreated $activity" }
                Utils.setActivity(activity)
                if (!handleWebView) {
                    WebView(activity).destroy()
                    appContext.addModuleAssets()
                    handleWebView = true
                }

                activity.addModuleAssets()
            }

            override fun onActivityDestroyed(activity: Activity) {
                Logger.printDebug { "onActivityDestroyed $activity" }
            }

            override fun onActivityPaused(activity: Activity) {
                Logger.printDebug { "onActivityPaused $activity" }
            }

            override fun onActivityResumed(activity: Activity) {
                Logger.printDebug { "onActivityResumed $activity" }
                Utils.setActivity(activity)
                activity.addModuleAssets()
            }

            override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
                Logger.printDebug { "onActivitySaveInstanceState $activity" }
            }

            override fun onActivityStarted(activity: Activity) {
                Logger.printDebug { "onActivityStarted $activity" }
            }

            override fun onActivityStopped(activity: Activity) {
                Logger.printDebug { "onActivityStopped $activity" }
            }
        })
}
