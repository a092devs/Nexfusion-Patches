package io.github.a092devs.nexfusion.morphe.music.interaction.remember.repeatstate

import app.morphe.extension.music.patches.RememberRepeatStatePatch
import app.morphe.extension.shared.Logger
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.OpcodesFilter
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.scopedHook

internal object RepeatTrackFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "L"),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.IGET_OBJECT,
        Opcode.SGET_OBJECT,
        Opcode.CHECK_CAST,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT,
        Opcode.IF_NEZ
    ),
    strings = listOf("w_st")
)

internal object RepeatTrackLegacyFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "L"),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.IGET_OBJECT,
        Opcode.IGET_OBJECT,
        Opcode.SGET_OBJECT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT,
        Opcode.IF_NEZ
    ),
    strings = listOf("w_st")
)

private val repeatCheckMethod = findMethodDirect {
    RepeatTrackFingerprint.instructionMatches[3].instruction.methodRef!!
}

private val repeatCheckLegacyMethod = findMethodDirect {
    RepeatTrackLegacyFingerprint.instructionMatches[3].instruction.methodRef!!
}

val RememberRepeatState = patch(
    name = "Remember repeat state",
    description = "Adds an option to remember the repeat state when playing a new track or playlist.",
    use = false,
) {
    dependsOn(SettingsHook)

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_remember_repeat_state"),
    )

    try {
        val (fp, checkMethod) = try {
            RepeatTrackFingerprint to ::repeatCheckMethod.method
        } catch (_: Throwable) {
            RepeatTrackLegacyFingerprint to ::repeatCheckLegacyMethod.method
        }
        // Never hook java.lang.Boolean.booleanValue() via Xposed/scopedHook — LSPosed unboxes Booleans internally.
        if (!checkMethod.declaringClass.name.startsWith("java.")) {
            fp.hookMethod(
                scopedHook(checkMethod) {
                    after { param ->
                        if (innerDepth != 0) return@after
                        val orig = param.result as? Boolean ?: return@after
                        if (!orig) {
                            param.result = RememberRepeatStatePatch.rememberRepeatState()
                        }
                    }
                }
            )
        }
    } catch (t: Throwable) {
        Logger.printException({ "RememberRepeatState: RepeatTrack hook failed" }, t)
    }
}
