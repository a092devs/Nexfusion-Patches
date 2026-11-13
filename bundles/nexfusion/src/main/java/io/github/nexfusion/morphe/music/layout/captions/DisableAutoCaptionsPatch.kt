/*
 * Copyright 2026 NexFusion contributors
 */

package io.github.nexfusion.morphe.music.layout.captions

import app.morphe.extension.music.settings.Settings
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch

val DisableAutoCaptions = patch(
    name = "Disable auto captions",
    description = "Adds an option to disable captions from being automatically enabled.",
) {
    runCatching {
        SetCaptionTrackFingerprint.memberOrNull?.let { member ->
            XposedBridge.hookMethod(member, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val event = param.args.firstOrNull() ?: return
                    val intFields = event.javaClass.declaredFields.filter {
                        it.type == Int::class.javaPrimitiveType || it.type == java.lang.Integer::class.java
                    }
                    for (field in intFields) {
                        field.isAccessible = true
                        val reason = field.getInt(event)
                        if (reason == 2 && Settings.DISABLE_AUTO_CAPTIONS.get()) {
                            // Suppress automatic caption selection track event
                            param.result = null
                            return
                        }
                    }
                }
            })
        }
    }.onFailure { PatcherLog.info(it) { "NexFusion YT Music: Disable auto captions hook skipped" } }
}
