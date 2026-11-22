package io.github.a092devs.nexfusion.morphe.youtube.layout.hide.ambientmode

import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.anyInstruction
import io.github.a092devs.nexfusion.morphe.checkCast
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.literal
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.opcode
import io.github.a092devs.nexfusion.morphe.string

/**
 * The (non-obfuscated) YouTube player view whose onLayout applies the fullscreen
 * Ambient mode dimming.
 */
internal object SetFullScreenBackgroundColorFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/app/player/YouTubePlayerViewNotForReflection;",
    name = "onLayout",
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Z", "I", "I", "I", "I"),
)

/**
 * CinematicContainerRenderer, the ambient mode proto of the /next response
 * (upstream commit 0eda16c28). The literal is its extension field number.
 */
internal object CinematicContainerRendererFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    filters = listOf(literal(455752576L)),
)

/**
 * Reads the presentation style (field 3) of a CinematicContainerRenderer, if it has one.
 */
internal fun cinematicContainerPresentationStyleFingerprint(rendererType: String) = Fingerprint(
    returnType = "L",
    parameters = listOf(rendererType),
    filters = listOf(
        // Has-bits field.
        fieldAccess(opcode = Opcode.IGET, definingClass = rendererType, type = "I"),
        opcode(Opcode.AND_INT_LIT8, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        // Presentation style field.
        fieldAccess(
            opcode = Opcode.IGET,
            definingClass = rendererType,
            type = "I",
            location = MatchAfterImmediately()
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("I"),
            location = MatchAfterImmediately()
        ),
    )
)

/**
 * Reads the config (field 4) of a CinematicContainerRenderer.
 */
internal fun cinematicContainerConfigFingerprint(rendererType: String) = Fingerprint(
    filters = listOf(
        methodCall(name = "get", parameters = listOf(), returnType = "Ljava/lang/Object;"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        checkCast(rendererType, location = MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = rendererType,
            location = MatchAfterImmediately()
        )
    )
)

/**
 * Reads the 'ambient mode on by default' value (field 11) of the CinematicContainerRenderer
 * config, used if the flyout menu toggle was never changed.
 */
internal fun cinematicContainerEnabledByDefaultFingerprint(configType: String) = Fingerprint(
    filters = listOf(
        // Has-bits field.
        fieldAccess(opcode = Opcode.IGET, definingClass = configType, type = "I"),
        anyInstruction(
            opcode(Opcode.AND_INT_LIT16), // 20.51 - 21.20
            opcode(Opcode.AND_INT_2ADDR), // 20.21, 21.21+
            location = MatchAfterImmediately()
        ),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            definingClass = configType,
            type = "Z",
            location = MatchAfterImmediately()
        ),
        methodCall(
            smali = "Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;",
            location = MatchAfterImmediately()
        )
    )
)

/**
 * Holds the CinematicContainerRenderer of the current video and its loading state.
 */
internal object CinematicContainerRendererWithStateFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(string("RendererWithState{rendererLoadedState=")),
)

/**
 * Loading state enum of [CinematicContainerRendererWithStateFingerprint]. Other enums have the
 * same constant names, so the type is required.
 */
internal fun cinematicContainerRendererLoadedStateFingerprint(stateType: String) = Fingerprint(
    definingClass = stateType,
    name = "<clinit>",
    returnType = "V",
    filters = listOf(
        string("WAITING"),
        fieldAccess(definingClass = stateType, type = stateType, opcode = Opcode.SPUT_OBJECT),
        string("LOADED"),
        fieldAccess(definingClass = stateType, type = stateType, opcode = Opcode.SPUT_OBJECT),
    )
)
