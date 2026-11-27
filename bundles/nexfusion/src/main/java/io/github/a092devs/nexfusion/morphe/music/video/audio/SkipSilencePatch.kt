package io.github.a092devs.nexfusion.morphe.music.video.audio

import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.patches.SkipSilencePatch
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.findFieldDirect
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.a092devs.nexfusion.morphe.string
import io.github.a092devs.nexfusion.patch
import java.nio.ByteBuffer

internal object ApplySkipSilenceFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        filters = listOf(
            string("AudioTrackAudioOutput"),
            string("Failed to set playback params")
        )
    ),
    returnType = "V",
    parameters = listOf("J"),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "this", type = "L"),
        fieldAccess(opcode = Opcode.IGET_BOOLEAN, definingClass = "this", location = MatchAfterWithin(2)),
        fieldAccess(definingClass = "this", opcode = Opcode.IPUT_BOOLEAN)
    )
)

internal object SilenceSkippingProcessorConstructorFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        filters = listOf(string("bytesConsumed is not aligned to frame size: %s"))
    ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    parameters = listOf("J", "F", "J", "I", "S")
)

internal object SilenceSkippingProcessorQueueInputFingerprint : Fingerprint(
    classFingerprint = SilenceSkippingProcessorConstructorFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/nio/ByteBuffer;")
)

internal object SilenceSkippingProcessorFlushFingerprint : Fingerprint(
    classFingerprint = SilenceSkippingProcessorConstructorFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(),
    filters = listOf(io.github.a092devs.nexfusion.morphe.opcode(Opcode.NEW_ARRAY))
)

private val skipSilenceBoolField = findFieldDirect {
    ApplySkipSilenceFingerprint.instructionMatches[1].instruction.fieldRef!!
}

val SkipSilence = patch(
    name = "Skip silence",
    description = "Adds an option to automatically skip silent pauses in audio playback.",
    use = false,
) {
    dependsOn(SettingsHook)

    PreferenceScreen.PLAYER.addPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference("morphe_skip_silence", summary = true),
            ListPreference("morphe_skip_silence_minimum_pause")
        )
    )

    try {
        val boolField = ::skipSilenceBoolField.field.apply { isAccessible = true }
        val audioSinkClass = ApplySkipSilenceFingerprint.method.declaringClass

        ApplySkipSilenceFingerprint.hookMethod {
            before { param ->
                val cur = boolField.getBoolean(param.thisObject)
                val next = SkipSilencePatch.isSkipSilenceEnabled(cur)
                if (next != cur) boolField.setBoolean(param.thisObject, next)
            }
        }

        audioSinkClass.declaredMethods.filter {
            it.returnType == Void.TYPE &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Boolean::class.javaPrimitiveType
        }.forEach { setter ->
            setter.hookMethod {
                before { param ->
                    param.args[0] = SkipSilencePatch.isSkipSilenceEnabled(param.args[0] as Boolean)
                }
            }
        }
    } catch (t: Throwable) {
        Logger.printException({ "SkipSilence: ApplySkipSilence hook failed" }, t)
    }

    runCatching {
        SilenceSkippingProcessorConstructorFingerprint.hookMethod {
            before { param ->
                param.args[0] = SkipSilencePatch.minimumSilenceDurationUs(param.args[0] as Long)
                param.args[1] = SkipSilencePatch.silenceRetentionRatio(param.args[1] as Float)
                param.args[4] = SkipSilencePatch.silenceThresholdLevel(param.args[4] as Short)
            }
        }

        SilenceSkippingProcessorQueueInputFingerprint.hookMethod {
            before { param ->
                (param.args[0] as? ByteBuffer)?.let {
                    SkipSilencePatch.updateSilenceThresholdLevel(it)
                }
            }
        }

        SilenceSkippingProcessorFlushFingerprint.hookMethod {
            before {
                SkipSilencePatch.resetSilenceThresholdLevel()
            }
        }
    }.onFailure {
        Logger.printException({ "SkipSilence: SilenceSkippingProcessor hook failed" }, it)
    }
}
