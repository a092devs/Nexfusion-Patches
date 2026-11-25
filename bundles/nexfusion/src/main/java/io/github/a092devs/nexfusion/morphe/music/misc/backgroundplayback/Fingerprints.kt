package io.github.a092devs.nexfusion.morphe.music.misc.backgroundplayback

import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.OpcodesFilter
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.methodCall

internal object BackgroundPlaybackDisableFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = listOf("L"),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.CONST_4,
        Opcode.IF_EQZ,
        Opcode.IGET,
        Opcode.AND_INT_LIT16,
        Opcode.IF_EQZ,
        Opcode.IGET_OBJECT,
        Opcode.IF_NEZ,
        Opcode.SGET_OBJECT,
        Opcode.IGET,
    )
)

internal object KidsBackgroundPlaybackPolicyControllerFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("I", "L", "Z"),
    filters = listOf(
        methodCall(smali = "Lj$/util/Optional;->get()Ljava/lang/Object;"),
        fieldAccess(
            opcode = Opcode.IPUT,
            definingClass = "this",
            type = "I"
        ),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            location = MatchAfterWithin(5)
        ),
        fieldAccess(
            opcode = Opcode.IPUT_BOOLEAN,
            definingClass = "this",
            type = "Z",
            location = MatchAfterWithin(5)
        )
    )
)
