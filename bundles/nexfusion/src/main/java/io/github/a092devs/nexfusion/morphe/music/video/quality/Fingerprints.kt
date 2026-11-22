/*
 * Copyright 2026 NexFusion contributors
 */

package io.github.a092devs.nexfusion.morphe.music.video.quality

import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.string

/**
 * Matches the method called with the player response model when a new track loads.
 */
internal object MusicVideoIdFingerprint : Fingerprint(
    filters = listOf(
        string("Null initialPlayabilityStatus")
    )
)
