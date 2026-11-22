package io.github.a092devs.nexfusion.morphe.youtube.layout.hide.general

import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.ResourceType
import io.github.a092devs.nexfusion.morphe.StringComparisonType
import io.github.a092devs.nexfusion.morphe.checkCast
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.findFieldDirect
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.morphe.findMethodListDirect
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.opcode
import io.github.a092devs.nexfusion.morphe.resourceLiteral
import io.github.a092devs.nexfusion.morphe.string

internal object ParseElementFromBufferFingerprint : Fingerprint(
    parameters = listOf("L", "L", "[B", "L", "L"),
    filters = listOf(
        string("Failed to parse Element", StringComparisonType.STARTS_WITH),
    ),
)

private object PlayerOverlayFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    strings = listOf("player_overlay_in_video_programming"),
)

internal object ShowWatermarkFingerprint : Fingerprint(
    classFingerprint = PlayerOverlayFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "L"),
)

internal val showWatermarkSubFingerprint = findMethodDirect {
    ShowWatermarkFingerprint.run().invokes.findMethod {
        matcher {
            returnType = "void"
            paramTypes("android.view.View", "boolean")
        }
    }.single()
}

/**
 * The methods that find the live chat scroll to bottom button (upstream commit 19569880c,
 * "Hide live chat scroll to bottom button").
 */
internal object MoreCommentsIconFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Landroid/view/View;",
    parameters = listOf(),
    filters = listOf(
        resourceLiteral(ResourceType.ID, "more_comments_icon"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, name = "findViewById"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
    )
)

internal val moreCommentsIconMethods = findMethodListDirect {
    MoreCommentsIconFingerprint.matchAllOrNull()?.mapNotNull { it.method } ?: emptyList()
}

/*
internal object BottomSheetMenuItemBuilderFingerprint : Fingerprint(
    returnType = "L",
    parameters = listOf("L"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            returnType = "Ljava/lang/CharSequence;",
            parameters = listOf("L")
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        string("Text missing for BottomSheetMenuItem.")
    )
)

val bottomSheetMenuItemTextFingerprint = findMethodDirect {
    BottomSheetMenuItemBuilderFingerprint.instructionMatches[0].instruction.methodRef!!
}

*/

internal object ContextualMenuItemBuilderFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL, AccessFlags.SYNTHETIC),
    returnType = "V",
    parameters = listOf("L", "L"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            returnType = "Ljava/lang/CharSequence;",
            parameters = listOf("L")
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Landroid/view/View;",
            location = MatchAfterImmediately()
        ),
        checkCast("Landroid/widget/TextView;", location = MatchAfterImmediately()),
        methodCall(
            smali = "Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V",
            location = MatchAfterWithin(5)
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            returnType = "I",
            location = MatchAfterWithin(7),
        ),
        resourceLiteral(ResourceType.DIMEN, "poster_art_width_default"),
    )
)

val contextualMenuItemTextViewField = findFieldDirect {
    ContextualMenuItemBuilderFingerprint.instructionMatches[2].instruction.fieldRef!!
}

val contextualMenuItemTextFingerprint = findMethodDirect {
    ContextualMenuItemBuilderFingerprint.instructionMatches[0].instruction.methodRef!!
}

/**
 * A row of an app bar menu.
 */
internal object ListMenuItemViewOnMeasureFingerprint : Fingerprint(
    definingClass = "Landroid/support/v7/view/menu/ListMenuItemView;",
    name = "onMeasure",
    returnType = "V",
    parameters = listOf("I", "I")
)
