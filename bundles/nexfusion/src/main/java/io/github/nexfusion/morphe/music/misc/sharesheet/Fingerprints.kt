/*
 * Copyright 2026 NexFusion contributors
 */

package io.github.nexfusion.morphe.music.misc.sharesheet

import io.github.nexfusion.morphe.Fingerprint
import io.github.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexfusion.morphe.Opcode
import io.github.nexfusion.morphe.fieldAccess
import io.github.nexfusion.morphe.methodCall
import io.github.nexfusion.morphe.opcode

/**
 * Command resolver of the share entity service endpoint in YouTube Music.
 * Matches endpoint cast, network check, and read of serialized share entity.
 */
internal object ShareEntityServiceEndpointCommandFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "Ljava/util/Map;"),
    strings = listOf(
        "music.youtube.com",
        "navigation_endpoint",
        "UnifiedSharePanelFragment"
    ),
    filters = listOf(
        opcode(Opcode.CHECK_CAST),
        methodCall(
            returnType = "Z",
            parameters = listOf(),
            location = MatchAfterImmediately()
        ),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Ljava/lang/String;",
            location = MatchAfterWithin(5)
        )
    )
)
