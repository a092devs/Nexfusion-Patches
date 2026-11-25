package io.github.a092devs.nexfusion.morphe.youtube.ad

import io.github.a092devs.nexfusion.SkipTest
import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.OpcodesFilter
import io.github.a092devs.nexfusion.morphe.ResourceType
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.findClassDirect
import io.github.a092devs.nexfusion.morphe.findFieldDirect
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.opcode
import io.github.a092devs.nexfusion.morphe.resourceLiteral
import io.github.a092devs.nexfusion.morphe.string

private val ADD_METHOD_CALL = methodCall(
    opcode = Opcode.INVOKE_VIRTUAL,
    name = "add",
    parameters = listOf("Ljava/lang/Object;"),
    returnType = "Z",
)

@SkipTest /*unused*/
internal object FullScreenEngagementAdContainerFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        resourceLiteral(ResourceType.ID, "fullscreen_engagement_ad_container"),
        opcode(Opcode.IGET_BOOLEAN),
        ADD_METHOD_CALL,
        ADD_METHOD_CALL,
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            name = "size",
            parameters = listOf(),
            returnType = "I"
        )
    )
)

internal object GetPremiumViewFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/app/red/presenter/CompactYpcOfferModuleView;",
    name = "onMeasure",
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("I", "I"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            definingClass = "this",
            name = "setMeasuredDimension",
            parameters = listOf("I", "I"),
            returnType = "V"
        ),
        opcode(Opcode.RETURN_VOID, MatchAfterImmediately())
    )
)

internal object PlayerOverlayTimelyShelfFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        opcode(Opcode.CHECK_CAST),
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Ljava/lang/String;", location = MatchAfterWithin(5)),
        // above nexfusion custom filters
        string("player_overlay_timely_shelf", location = MatchAfterImmediately()),
        methodCall(smali = "Ljava/lang/String;->equals(Ljava/lang/Object;)Z", location = MatchAfterWithin(5)),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately())
    )
)

val PlayerOverlayEventType = findClassDirect {
    PlayerOverlayTimelyShelfFingerprint.instructionMatches[0].instruction.classRef!!
}

val PlayerOverlayIdField = findFieldDirect {
    PlayerOverlayTimelyShelfFingerprint.instructionMatches[1].instruction.fieldRef!!
}

internal object LoadVideoAdsFingerprint : Fingerprint(
    strings = listOf(
        "TriggerBundle doesn't have the required metadata specified by the trigger ",
        "Ping migration no associated ping bindings for activated trigger: ",
    )
)

internal object PlayerBytesAdLayoutFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L"),
    strings = listOf(
        "Bootstrapped layout construction resulted in non PlayerBytesLayout. PlayerAds count: ",
    )
)

internal object MiniplayerPaidPromotionLabelFingerprint : Fingerprint(
    filters = listOf(
        resourceLiteral(ResourceType.ID, "modern_miniplayer_subtitle_text"),
        opcode(Opcode.INVOKE_VIRTUAL, MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately())
    )
)
