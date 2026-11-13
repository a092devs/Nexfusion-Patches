/*
 * Copyright 2026 NexFusion contributors
 */

package io.github.nexfusion.morphe.youtube.misc.commandrouter

import android.app.Activity
import app.morphe.extension.shared.Logger
import app.morphe.extension.youtube.patches.CommandRouterPatch
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import java.lang.reflect.Method

val CommandRouterHook = patch(
    name = "CommandRouterHook",
    description = "Hooks the command router of each activity so the extension can run commands.",
) {
    runCatching {
        CommandRouterConstructorFingerprint.memberOrNull?.let { ctor ->
            XposedBridge.hookMethod(ctor, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.args.firstOrNull() as? Activity ?: return
                    val routerInstance = param.thisObject ?: return
                    val executeMethod = CommandRouterExecuteCommandFingerprint.memberOrNull as? Method ?: return
                    val commandClass = executeMethod.parameterTypes.firstOrNull() ?: return
                    val parseFromWithRegistry = runCatching {
                        commandClass.getMethod(
                            "parseFrom",
                            ByteArray::class.java,
                            Class.forName("com.google.protobuf.ExtensionRegistryLite", false, commandClass.classLoader)
                        )
                    }.getOrNull()
                    val parseFromSimple = runCatching {
                        commandClass.getMethod("parseFrom", ByteArray::class.java)
                    }.getOrNull()

                    val registry = runCatching {
                        val regClass = Class.forName("com.google.protobuf.ExtensionRegistryLite", false, commandClass.classLoader)
                        regClass.getMethod("getGeneratedRegistry").invoke(null)
                    }.getOrNull() ?: runCatching {
                        com.google.protobuf.ExtensionRegistryLite.getEmptyRegistry()
                    }.getOrNull()

                    val router = CommandRouterPatch.CommandRouter { commandBytes, context ->
                        try {
                            val cmd = if (parseFromWithRegistry != null && registry != null) {
                                parseFromWithRegistry.invoke(null, commandBytes, registry)
                            } else {
                                parseFromSimple?.invoke(null, commandBytes)
                            } ?: return@CommandRouter
                            executeMethod.invoke(routerInstance, cmd, context)
                        } catch (e: Exception) {
                            Logger.printException({ "CommandRouter executeCommand failed" }, e)
                        }
                    }
                    CommandRouterPatch.setCommandRouter(router, activity)
                }
            })
        }
    }.onFailure { PatcherLog.info(it) { "NexFusion YouTube: CommandRouterHook setup skipped" } }
}
