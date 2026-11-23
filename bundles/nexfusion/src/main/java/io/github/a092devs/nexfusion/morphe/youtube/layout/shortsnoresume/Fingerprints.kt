package io.github.a092devs.nexfusion.morphe.youtube.layout.shortsnoresume

import io.github.a092devs.nexfusion.RequireAppVersion
import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.StringComparisonType
import io.github.a092devs.nexfusion.morphe.checkCast
import io.github.a092devs.nexfusion.morphe.literal
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.opcode
import io.github.a092devs.nexfusion.morphe.string

@RequireAppVersion("21.03.00")
internal object UserWasInShortsEvaluateAnchorFingerprint: Fingerprint(
    returnType = "Z",
    filters = listOf(
        literal(1073815471),
        literal(1073815469)
    )
)

/**
 * 21.41+
 */
@RequireAppVersion("21.41.000")
internal object UserWasInShortsEvaluateFingerprint : Fingerprint(
    classFingerprint = UserWasInShortsEvaluateAnchorFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf(
        "Ljava/util/function/Supplier;", "L", "Ljava/lang/String;"
    ),
    filters = listOf(
        methodCall(
            name = "<init>",
            parameters = listOf("L", "Z", "Z", "L", "Z")
        ),
        methodCall(
            name = "<init>",
            parameters = listOf("L", "L", "L", "L", "L", "Ljava/lang/String;"),
            location = InstructionLocation.MatchAfterWithin(50)
        )
    )
)

/**
 * 21.30 - 21.40
 */
@RequireAppVersion(minVersion = "21.30.000", maxVersion = "21.41.000")
internal object UserWasInShortsEvaluateLegacy2130Fingerprint : Fingerprint(
    classFingerprint = UserWasInShortsEvaluateAnchorFingerprint,
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_DIRECT_RANGE,
            name = "<init>",
            parameters = listOf("L", "Z", "Z", "L", "Z")
        ),
        methodCall(
            opcode = Opcode.INVOKE_DIRECT_RANGE,
            name = "<init>",
            parameters = listOf("L", "L", "L", "L", "L", "L", "Ljava/lang/String;"),
            location = InstructionLocation.MatchAfterWithin(50)
        )
    )
)

/**
 * 21.03 - 21.29
 */
@RequireAppVersion(minVersion="21.03.00", maxVersion = "21.30.000")
internal object UserWasInShortsEvaluateLegacyFingerprint : Fingerprint(
    classFingerprint = UserWasInShortsEvaluateAnchorFingerprint,
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_DIRECT_RANGE,
            name = "<init>",
            parameters = listOf("L", "Z", "Z", "L", "Z")
        ),
        methodCall(
            opcode = Opcode.INVOKE_DIRECT_RANGE,
            name = "<init>",
            parameters = listOf("L", "L", "L", "L", "L", "I"),
            location = InstructionLocation.MatchAfterWithin(50)
        )
    )
)

/**
 * 20.02+
 */
@RequireAppVersion("20.02.00", "21.03.00")
internal object UserWasInShortsListenerFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        checkCast("Ljava/lang/Boolean;"),
        methodCall(smali = "Ljava/lang/Boolean;->booleanValue()Z", location = InstructionLocation.MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately()),
        string("ShortsStartup SetUserWasInShortsListener", StringComparisonType.CONTAINS, InstructionLocation.MatchAfterWithin(30))
    )
)
