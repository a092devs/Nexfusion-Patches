package io.github.a092devs.nexfusion.morphe.music.interaction.dialog

import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.patches.RemoveViewerDiscretionDialogPatch
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.opcode
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.string
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.scopedHook

internal object AdultContentRunnableFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        opcode(Opcode.IGET_OBJECT),
        string("allowControversialContent"),
        methodCall(parameters = listOf(), returnType = "Z", location = MatchAfterWithin(1)),
        string("allowAdultContent")
    )
)

internal object SkipDialogFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, smali = "Ljava/lang/Boolean;->booleanValue()Z"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterWithin(4)),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>",
            location = MatchAfterWithin(5)
        )
    )
)

val RemoveViewerDiscretionDialog = patch(
    name = "Remove viewer discretion dialog",
    description = "Adds an option to remove the dialog that appears when opening a video that has been age-restricted " +
        "by accepting it automatically. This does not bypass the age restriction.",
    use = false,
) {
    dependsOn(SettingsHook)

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_remove_viewer_discretion_dialog"),
    )

    // Note: Do NOT hook java.lang.Boolean.booleanValue() via Xposed/scopedHook because
    // LSPosed's own hook dispatcher unboxes Booleans and causes infinite recursion.
    runCatching {
        SkipDialogFingerprint.hookMethod {
            before {
                RemoveViewerDiscretionDialogPatch.hideViewDiscretionDialog(false)
            }
        }
    }.onFailure {
        Logger.printException({ "RemoveViewerDiscretionDialog: SkipDialog hook failed" }, it)
    }
}
