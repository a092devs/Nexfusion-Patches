/*
 * Copyright 2026 NexFusion contributors
 */

package io.github.nexfusion.morphe.music.video.quality

import app.morphe.extension.music.patches.RememberVideoQualityPatch
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch

val RememberVideoQuality = patch(
    name = "Remember video quality",
    description = "Remembers the last selected video quality and applies default quality on Wi-Fi and mobile networks.",
    use = true,
) {
    runCatching {
        MusicVideoIdFingerprint.memberOrNull?.let { member ->
            XposedBridge.hookMethod(member, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val videoId = param.args.firstOrNull { it is String } as? String ?: return
                    RememberVideoQualityPatch.newVideoStarted(videoId)
                }
            })
        }
    }.onFailure { PatcherLog.info(it) { "NexFusion YT Music: Remember video quality hook skipped" } }
}
