/*
 * Copyright 2026 NexFusion contributors
 */

package io.github.nexfusion.morphe.music.misc.sharesheet

import app.morphe.extension.music.patches.OpenSystemShareSheetPatch
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch

val OpenSystemShareSheet = patch(
    name = "Open system share sheet",
    description = "Opens the Android system share sheet instead of the in-app share sheet when sharing songs, playlists, or artists.",
    use = false,
) {
    runCatching {
        ShareEntityServiceEndpointCommandFingerprint.memberOrNull?.let { member ->
            XposedBridge.hookMethod(member, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val endpoint = param.args.firstOrNull() ?: return
                    OpenSystemShareSheetPatch.useInAppShareSheet(true, endpoint)
                }
            })
        }
    }.onFailure { PatcherLog.info(it) { "NexFusion YT Music: Open system share sheet hook skipped" } }
}
