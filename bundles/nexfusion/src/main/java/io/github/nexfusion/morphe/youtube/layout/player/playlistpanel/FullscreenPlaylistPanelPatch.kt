/*
 * Copyright 2026 NexFusion contributors
 */

package io.github.nexfusion.morphe.youtube.layout.player.playlistpanel

import app.morphe.extension.youtube.patches.FullscreenPlaylistPanelPatch as ExtensionFullscreenPlaylistPanelPatch
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.youtube.misc.engagement.EngagementPanelHook
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch

val FullscreenPlaylistPanel = patch(
    name = "Fullscreen playlist panel",
    description = "Adds an option to show the playlist panel over the fullscreen player on tablets, instead of exiting fullscreen.",
) {
    dependsOn(
        EngagementPanelHook,
    )

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_fullscreen_playlist_panel", summary = true)
    )

    runCatching {
        PanelExitsFullscreenFingerprint.memberOrNull?.let { member ->
            XposedBridge.hookMethod(member, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (ExtensionFullscreenPlaylistPanelPatch.keepFullscreen()) {
                        param.result = false
                    }
                }
            })
        }
    }.onFailure { PatcherLog.info(it) { "NexFusion YouTube: Fullscreen playlist panel hook skipped" } }
}
