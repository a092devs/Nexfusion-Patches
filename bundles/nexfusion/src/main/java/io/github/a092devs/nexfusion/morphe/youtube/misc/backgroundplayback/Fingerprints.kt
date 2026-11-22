package io.github.a092devs.nexfusion.morphe.youtube.misc.backgroundplayback

import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.OpcodesFilter
import io.github.a092devs.nexfusion.morphe.ResourceType
import io.github.a092devs.nexfusion.morphe.accessFlags
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.morphe.findMethodListDirect
import io.github.a092devs.nexfusion.morphe.literal
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.parameters
import io.github.a092devs.nexfusion.morphe.resourceLiteral
import io.github.a092devs.nexfusion.morphe.returns
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.is_21_23_or_greater

object BackgroundPlaybackManagerFingerprint : Fingerprint(
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
        Opcode.CONST,
        Opcode.IF_NE,
        Opcode.IGET_OBJECT,
        Opcode.IF_NEZ,
        Opcode.SGET_OBJECT,
        Opcode.IGET,
        Opcode.IF_NE,
        Opcode.IGET_OBJECT,
        Opcode.CHECK_CAST,
        Opcode.GOTO,
        Opcode.SGET_OBJECT,
        Opcode.GOTO,
        Opcode.CONST_4,
        Opcode.IF_EQZ,
        Opcode.IGET_BOOLEAN,
        Opcode.IF_EQZ,
    )
)

/**
 * 21.23+
 */
object BackgroundPlaybackSettingsFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        // Background playback allowed.
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, returnType = "Z", parameters = listOf()),
        methodCall(
            smali = "Ljava/lang/Boolean;->booleanValue()Z",
            location = MatchAfterWithin(2)
        ),
        resourceLiteral(
            ResourceType.STRING,
            "pref_background_and_offline_category",
            location = MatchAfterWithin(10)
        ),
        resourceLiteral(ResourceType.STRING, "pref_background_category")
    )
)

/**
 * 21.22 and lower.
 */
object BackgroundPlaybackSettingsLegacyFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        methodCall(returnType = "Z"),
        // Background playback allowed.
        methodCall(returnType = "Z"),
        resourceLiteral(ResourceType.STRING, "pref_background_and_offline_category"),
        resourceLiteral(ResourceType.STRING, "pref_background_category")
    )
)

val backgroundPlaybackSettingsSubFingerprint = findMethodDirect {
    val backgroundPlaybackAllowed = if (is_21_23_or_greater) {
        BackgroundPlaybackSettingsFingerprint.instructionMatches.first()
    } else {
        BackgroundPlaybackSettingsLegacyFingerprint.instructionMatches[1]
    }

    backgroundPlaybackAllowed.instruction.methodRef!!
}

object KidsBackgroundPlaybackPolicyControllerFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("I", "L", "L"),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.CONST_4,
        Opcode.IF_NE,
        Opcode.SGET_OBJECT,
        Opcode.IF_NE,
        Opcode.IGET,
        Opcode.CONST_4,
        Opcode.IF_NE,
        Opcode.IGET_OBJECT,
    )
)

val backgroundPlaybackManagerShortsFingerprint = findMethodListDirect {
    /*
    * two matches in versions 21.02.32
    * It doesn't seem to be an A/B test;
    * it seems to be a different method that checks an additional property to determine the result.
    * */
    findMethod {
        matcher {
            accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
            returns("Z")
            parameters("L")
            literal { 151635310 }
        }
    }
}
