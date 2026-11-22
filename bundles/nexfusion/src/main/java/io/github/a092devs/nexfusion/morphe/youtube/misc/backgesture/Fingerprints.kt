package io.github.a092devs.nexfusion.morphe.youtube.misc.backgesture

import io.github.a092devs.nexfusion.RequireAppVersion
import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.literal
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.opcode
import io.github.a092devs.nexfusion.morphe.youtube.shared.YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE


internal object YouTubeMainActivityOnBackPressedFingerprint : Fingerprint(
    definingClass = YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE,
    name = "onBackPressed",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_SUPER,
            name = "onBackPressed"
        ),
        opcode(Opcode.RETURN_VOID)
    )
)

@RequireAppVersion(minVersion = "20.40.00")
internal object PredictiveGesturesOnBackInvokedFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        name = "onBackCancelled",
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
        returnType = "V",
        parameters = listOf(),
        filters = listOf(
            literal(0),
            opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
            fieldAccess(
                opcode = Opcode.IGET_OBJECT,
                type = "Ljava/lang/Object;",
                location = MatchAfterWithin(5)
            ),
            literal(-1, location = MatchAfterWithin(10)),
        )
    ),
    name = "onBackInvoked"
)
