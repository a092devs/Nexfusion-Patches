/*
 * Copyright 2026 NexFusion contributors
 */

package io.github.nexfusion.morphe.music.layout.captions

import io.github.nexfusion.morphe.AccessFlags
import io.github.nexfusion.morphe.Fingerprint
import io.github.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexfusion.morphe.InstructionLocation.MatchFirst
import io.github.nexfusion.morphe.Opcode
import io.github.nexfusion.morphe.fieldAccess
import io.github.nexfusion.morphe.methodCall
import io.github.nexfusion.morphe.opcode
import io.github.nexfusion.morphe.string

private object SubtitleManagerClassFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.STATIC),
    returnType = "Z",
    parameters = listOf("L", "Landroid/view/accessibility/CaptioningManager;"),
    filters = listOf(
        fieldAccess("Ljava/util/concurrent/TimeUnit;->SECONDS:Ljava/util/concurrent/TimeUnit;"),
        methodCall("Landroid/view/accessibility/CaptioningManager;->isEnabled()Z"),
    )
)

/**
 * Sets the caption track of a caption track event in YouTube Music.
 */
internal object SetCaptionTrackFingerprint : Fingerprint(
    classFingerprint = SubtitleManagerClassFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        // Reason the track is set.
        fieldAccess(
            opcode = Opcode.IGET,
            type = "I",
            location = MatchFirst()
        ),
        string(""),
        // If the track is the option to disable captions.
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf(),
            returnType = "Z"
        ),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        // If the caption tracks of the video have a default track.
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            location = MatchAfterWithin(10)
        ),
    )
)
