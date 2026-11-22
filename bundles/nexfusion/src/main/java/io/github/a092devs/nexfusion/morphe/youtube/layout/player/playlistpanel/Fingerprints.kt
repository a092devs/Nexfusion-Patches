/*
 * Copyright 2026 NexFusion contributors
 */

package io.github.a092devs.nexfusion.morphe.youtube.layout.player.playlistpanel

import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.string

/**
 * YouTube's rule whether a panel opened in fullscreen exits fullscreen (true on any tablet).
 */
internal object PanelExitsFullscreenFingerprint : Fingerprint(
    returnType = "Z",
    filters = listOf(
        string("[EngagementPanel] Cannot cannot determine if shouldExitFullscreen because EngagementPanelController has not been initialized.")
    )
)
