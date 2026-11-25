package io.github.a092devs.nexfusion.morphe.youtube.layout.miniplayer

import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.findFieldDirect
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.newInstance
import io.github.a092devs.nexfusion.morphe.opcode

/**
 * Draggable miniplayer rect getter, used as the class holder for the horizontal drag hooks.
 */
internal object MiniplayerRectDragFieldsNameFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.IF_GEZ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Landroid/graphics/Rect;",
            location = MatchAfterImmediately(),
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            name = "width",
            returnType = "I",
            location = MatchAfterImmediately(),
        ),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.NEG_INT, location = MatchAfterImmediately()),
        opcode(Opcode.GOTO, location = MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IGET,
            type = "I",
            location = MatchAfterImmediately(),
        ),
    ),
)

/**
 * The method that repositions the miniplayer with the dragged rect.
 */
internal val MiniplayerHorizontalRepositionMethod = findMethodDirect {
    val targetClass = MiniplayerRectDragFieldsNameFingerprint.instructionMatches[1].instruction.fieldRef?.className
        ?: error("Miniplayer rect defining class not found")
    findClass {
        matcher {
            className = targetClass
        }
    }.single().methods.single { method ->
        method.modifiers and (java.lang.reflect.Modifier.PUBLIC or java.lang.reflect.Modifier.FINAL) ==
            (java.lang.reflect.Modifier.PUBLIC or java.lang.reflect.Modifier.FINAL) &&
            method.returnTypeName == "void" &&
            method.paramTypeNames == listOf("android.graphics.Rect") &&
            method.usingFields.any {
                it.field.declaredClassName == "android.graphics.Rect" && it.field.name == "left"
            }
    }
}

/**
 * Offscreen miniplayer bounds handler.
 */
internal val MiniplayerOffscreenHandlerMethod = findMethodDirect {
    val targetClass = MiniplayerRectDragFieldsNameFingerprint.instructionMatches[1].instruction.fieldRef?.className
        ?: error("Miniplayer rect defining class not found")
    findClass {
        matcher {
            className = targetClass
        }
    }.single().methods.single { method ->
        method.modifiers and (java.lang.reflect.Modifier.PUBLIC or java.lang.reflect.Modifier.FINAL) ==
            (java.lang.reflect.Modifier.PUBLIC or java.lang.reflect.Modifier.FINAL) &&
            method.returnTypeName == "void" &&
            method.paramTypeNames == listOf("int", "int", "int", "int") &&
            method.usingFields.any { it.field.typeName == "android.graphics.Rect" }
    }
}

/**
 * Offscreen rect validator used when dragging the miniplayer.
 */
internal object MiniplayerOffscreenRectValidatorFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("I", "I", "I"),
    filters = listOf(
        opcode(Opcode.IGET_OBJECT),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Landroid/graphics/Rect;",
            location = MatchAfterImmediately(),
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            name = "centerX",
            returnType = "I",
            location = MatchAfterImmediately(),
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            name = "centerY",
            returnType = "I",
            location = MatchAfterWithin(3),
        ),
        newInstance(type = "Landroid/graphics/Point;", location = MatchAfterWithin(3)),
    ),
)

/**
 * The watch layout that intercepts touch events to detect the offscreen miniplayer button press.
 */
internal object NextGenWatchLayoutOnInterceptTouchEventFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/app/watch/nextgenwatch/ui/NextGenWatchLayout;",
    name = "onInterceptTouchEvent",
    parameters = listOf("Landroid/view/MotionEvent;"),
)

/**
 * The previous drag rect field of [MiniplayerRectDragFieldsNameFingerprint], read to block
 * offscreen repositioning.
 */
val miniplayerPreviousRectField = findFieldDirect {
    val match = MiniplayerRectDragFieldsNameFingerprint.instructionMatches[1]
    match.instruction.fieldRef ?: error("Miniplayer previous rect field not found")
}

