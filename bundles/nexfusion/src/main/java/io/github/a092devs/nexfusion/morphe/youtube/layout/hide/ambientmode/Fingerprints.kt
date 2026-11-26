package io.github.a092devs.nexfusion.morphe.youtube.layout.hide.ambientmode

import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.anyInstruction
import io.github.a092devs.nexfusion.morphe.checkCast
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.literal
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.opcode
import io.github.a092devs.nexfusion.morphe.string

/**
 * Litho rounded-rect background drawable onBoundsChange (21.30+).
 */
internal object LithoOnBoundsChangeFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
        returnType = "V",
        parameters = listOf(),
        filters = listOf(
            methodCall(smali = "Landroid/graphics/Path;->addRoundRect(Landroid/graphics/RectF;[FLandroid/graphics/Path\$Direction;)V"),
            fieldAccess(
                opcode = Opcode.IPUT_OBJECT,
                definingClass = "this",
                type = "Landroid/graphics/Path;"
            )
        )
    ),
    name = "onBoundsChange",
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/graphics/Rect;"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Landroid/graphics/Paint;",
            location = MatchAfterWithin(10)
        ),
        methodCall(
            smali = "Landroid/graphics/Paint;->setColor(I)V",
            location = MatchAfterImmediately()
        )
    )
)

/**
 * Litho rounded-rect background drawable onBoundsChange (< 21.30).
 */
internal object LithoOnBoundsChangeLegacyFingerprint : Fingerprint(
    name = "onBoundsChange",
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/graphics/Rect;"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Landroid/graphics/Path;"
        ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Landroid/graphics/Paint;",
            location = MatchAfterWithin(10)
        ),
        methodCall(
            smali = "Landroid/graphics/Paint;->setColor(I)V",
            location = MatchAfterImmediately()
        )
    )
)

/**
 * Draws the background of a view, such as a row of the playlist, and the touch feedback over it.
 */
internal object TouchFeedbackDrawableDrawFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/libraries/youtube/rendering/ui/spec/motion/TouchFeedbackDrawable;",
    name = "draw",
    returnType = "V",
    parameters = listOf("Landroid/graphics/Canvas;"),
    filters = listOf(
        methodCall(smali = "Landroid/graphics/drawable/Drawable;->draw(Landroid/graphics/Canvas;)V")
    )
)

/**
 * Receives the Ambient mode toggle of the player flyout menu when the app resumes and whenever
 * it changes, and saves it as the state of the menu item.
 */
internal object AmbientModeToggleFingerprint : Fingerprint(
    filters = listOf(
        checkCast("Ljava/lang/Boolean;"),
        string("menu_item_cinematic_lighting", location = MatchAfterWithin(8)),
        methodCall(
            parameters = listOf("Ljava/lang/String;", "Ljava/lang/Boolean;"),
            returnType = "V",
            location = MatchAfterImmediately()
        ),
    )
)

internal val ambientModeToggleSetterMethod = findMethodDirect {
    AmbientModeToggleFingerprint.match().instructionMatches.last().instruction.methodRef!!
}

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
