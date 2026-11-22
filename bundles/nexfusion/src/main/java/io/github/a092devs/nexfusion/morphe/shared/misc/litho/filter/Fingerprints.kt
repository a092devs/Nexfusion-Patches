package io.github.a092devs.nexfusion.morphe.shared.misc.litho.filter

import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.OpcodesFilter
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.findClassDirect
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.morphe.fingerprint
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.opcode
import io.github.a092devs.nexfusion.morphe.string

//val componentContextParserFingerprint = fingerprint {
//    strings("Number of bits must be positive")
//}

internal object AccessibilityIdFingerprint : Fingerprint(
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            parameters = listOf(),
            returnType = "Ljava/lang/String;"
        ),
        string("primary_image", location = MatchAfterWithin(5)),
    )
)

val AccessibilityIdMethod = findMethodDirect {
    AccessibilityIdFingerprint.instructionMatches.first().instruction.methodRef!!
}

val accessibilityTextMethod = findMethodDirect {
    Fingerprint(
        returnType = "V",
        filters = listOf(
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                parameters = listOf(),
                returnType = "Ljava/lang/String;"
            ),
            methodCall(
                reference = AccessibilityIdMethod(),
                location = MatchAfterWithin(5)
            )
        ),
        custom = {
        }
    ).instructionMatches.first().instruction.methodRef!!
}

val buttonViewModelReceiver = findMethodDirect {
    ComponentCreateFingerprint.instructionMatches[2].instruction.methodRef!!
}

object ComponentCreateFingerprint : Fingerprint(
    returnType = "L",
    filters = listOf(
        opcode(Opcode.IF_EQZ),
        opcode(
            Opcode.CHECK_CAST,
            location = MatchAfterWithin(5)
        ),
        opcode(Opcode.INVOKE_STATIC, MatchAfterImmediately()),
        opcode(Opcode.RETURN_OBJECT),
        string("Element missing correct type extension"),
        string("Element missing type")
    )
)

internal object ProtobufBufferEncodeFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "[B",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
//            definingClass = "this",
            type = "Lcom/google/android/libraries/elements/adl/UpbMessage;"
        ),
        methodCall(
            definingClass = "Lcom/google/android/libraries/elements/adl/UpbMessage;",
            name = "jniEncode"
        )
    )
)

object ProtobufBufferReferenceFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("I", "Ljava/nio/ByteBuffer;"),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.IPUT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT,
        Opcode.SUB_INT_2ADDR,
    )
)

val emptyComponentFingerprint = fingerprint {
    accessFlags(AccessFlags.PRIVATE, AccessFlags.CONSTRUCTOR)
    parameters()
    strings("EmptyComponent")
}

val lithoThreadExecutorFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
    parameters("I", "I", "I")
    classMatcher {
        superClass {
            descriptor = "Ljava/util/concurrent/ThreadPoolExecutor;"
        }
    }
    literal { 1L }
}

val emptyComponentClass = findClassDirect {
    emptyComponentFingerprint().declaredClass!!
}
