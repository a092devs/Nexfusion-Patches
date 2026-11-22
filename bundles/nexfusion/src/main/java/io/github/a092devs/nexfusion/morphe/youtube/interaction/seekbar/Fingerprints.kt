/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * Original hard forked code:
 * https://github.com/ReVanced/revanced-patches/commit/724e6d61b2ecd868c1a9a37d465a688e83a74799
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package io.github.a092devs.nexfusion.morphe.youtube.interaction.seekbar

import io.github.a092devs.nexfusion.RequireAppVersion
import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.OpcodesFilter
import io.github.a092devs.nexfusion.morphe.StrictQuery
import io.github.a092devs.nexfusion.morphe.checkCast
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.findFieldDirect
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.morphe.findMethodListDirect
import io.github.a092devs.nexfusion.morphe.literal
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.newInstance
import io.github.a092devs.nexfusion.morphe.opcode
import org.luckypray.dexkit.query.enums.MatchType

private object SwipingUpGestureParentFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        literal(45379021) // Swipe up fullscreen feature flag
    )
)

internal object ShowSwipingUpGuideFingerprint : Fingerprint(
    classFingerprint = SwipingUpGestureParentFingerprint,
    accessFlags = listOf(AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        literal(1)
    ),
    custom = { accessFlags(AccessFlags.FINAL.modifier, MatchType.Equals) }
)

internal object AllowSwipingUpGestureFingerprint : Fingerprint(
    classFingerprint = SwipingUpGestureParentFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L"),
    custom = { protoShorty("VL") }
)

@RequireAppVersion(minVersion = "21.40.000")
internal object DisableFastForwardGestureFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/app/watch/nextgenwatch/ui/NextGenWatchLayout;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.IF_EQZ,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT,
    ),
    custom = {
        opCodesMatcher!!.size(31..Int.MAX_VALUE)
    }
)

@RequireAppVersion(maxVersion = "21.40.000")
internal object DisableFastForwardGestureFingerprintLegacy : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/app/watch/nextgenwatch/ui/NextGenWatchLayout;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(definingClass = "this", opcode = Opcode.IGET_OBJECT),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        methodCall(
            parameters = listOf(),
            returnType = "Ljava/lang/Object;",
            opcode = Opcode.INVOKE_VIRTUAL,
            location = MatchAfterImmediately()
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        checkCast("Ljava/lang/Boolean;", location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        methodCall("Ljava/lang/Boolean;->booleanValue()Z", location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
    ),
    custom = {
        opCodesMatcher!!.size(31..Int.MAX_VALUE)
    }
)

internal object OnTouchEventHandlerFingerprint : Fingerprint(
    name = "onTouchEvent",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.PUBLIC),
    returnType = "Z",
    parameters = listOf("L"),
    filters = listOf(
        opcode(Opcode.INVOKE_VIRTUAL), // nMethodReference
        // Skip over a return (21.41 and lower) or goto (21.42+).
        opcode(Opcode.IGET_OBJECT, location = MatchAfterWithin(1)),
        opcode(Opcode.IGET_BOOLEAN, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterImmediately()),
        opcode(Opcode.INT_TO_FLOAT, location = MatchAfterWithin(1)),
        opcode(Opcode.INT_TO_FLOAT, location = MatchAfterImmediately()),
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterImmediately()),
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterImmediately()), // oMethodReference
    )
)

@StrictQuery
internal object TapToSeekFingerprint : Fingerprint(
    name = "onTouchEvent",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Landroid/view/MotionEvent;"),
    filters = listOf(
        literal(Int.MAX_VALUE),

        newInstance("Landroid/graphics/Point;"),
        methodCall(
            smali = "Landroid/graphics/Point;-><init>(II)V",
            location = MatchAfterImmediately()
        ),
        methodCall(
            smali = "Lj$/util/Optional;->of(Ljava/lang/Object;)Lj$/util/Optional;",
            location = MatchAfterImmediately()
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            type = "Lj$/util/Optional;",
            location = MatchAfterImmediately()
        ),

        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterWithin(10))
    )
)

@StrictQuery
internal object SlideToSeekFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "F"),
    filters = listOf(
        methodCall(
            returnType = "Z",
            parameters = listOf(),
            location = MatchAfterWithin(10) // Match close to start of method.
        ),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        literal(67108864)
    )
)

val tapSeekStartMethod = findMethodDirect {
    OnTouchEventHandlerFingerprint.instructionMatches.first().instruction.methodRef!!
}

val tapSeekUpdateMethod = findMethodDirect {
    OnTouchEventHandlerFingerprint.instructionMatches.last().instruction.methodRef!!
}

val tapSeekTouchDownMethod = findMethodDirect {
    TapToSeekFingerprint.instructionMatches.last().instruction.methodRef!!
}

val tapSeekPointField = findFieldDirect {
    TapToSeekFingerprint.instructionMatches[5].instruction.fieldRef!!
}

val slideToSeekCheckMethod = findMethodDirect {
    SlideToSeekFingerprint.instructionMatches.first().instruction.methodRef!!
}

@get:RequireAppVersion(minVersion = "21.40.000")
val fastForwardGestureCheckMethod = findMethodDirect {
    DisableFastForwardGestureFingerprint.instructionMatches[1].instruction.methodRef!!
}

@get:RequireAppVersion(maxVersion = "21.40.000")
val fastForwardGestureCheckMethodLegacy = findMethodDirect {
    DisableFastForwardGestureFingerprintLegacy.instructionMatches[2].instruction.methodRef!!
}

@get:RequireAppVersion(maxVersion = "21.40.000")
val fastForwardGestureControllerFieldLegacy = findFieldDirect {
    DisableFastForwardGestureFingerprintLegacy.instructionMatches[0].instruction.fieldRef!!
}

@get:RequireAppVersion(maxVersion = "21.40.000")
val fastForwardGestureValueFieldLegacy = findFieldDirect {
    DisableFastForwardGestureFingerprintLegacy.instructionMatches[1].instruction.fieldRef!!
}

val slideToSeekCheckCallers = findMethodListDirect {
    val checkMethod = slideToSeekCheckMethod()
    SlideToSeekFingerprint().declaredClass!!.methods.filter { method ->
        method.invokes.any { it.descriptor == checkMethod.descriptor }
    }.also { require(it.isNotEmpty()) }
}
