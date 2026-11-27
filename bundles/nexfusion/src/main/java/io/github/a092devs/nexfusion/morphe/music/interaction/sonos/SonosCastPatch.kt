package io.github.a092devs.nexfusion.morphe.music.interaction.sonos

import android.media.AudioTrack
import android.view.View
import app.morphe.extension.music.patches.sonos.SonosCastPatch
import app.morphe.extension.shared.Logger
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.StringComparisonType
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.music.misc.mediasession.MediaSessionHook
import io.github.a092devs.nexfusion.morphe.music.misc.mediasession.setMetadataWithSessionHooks
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.opcode
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.string
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.scopedHook
import java.lang.reflect.Modifier
import java.nio.ByteBuffer

internal object MdxMediaRouteButtonPerformClickFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/libraries/youtube/mdx/mediaroute/MdxMediaRouteButton;",
    name = "performClick",
    returnType = "Z",
    parameters = listOf()
)

private object MediaRouteButtonControllerFingerprint : Fingerprint(
    name = "<clinit>",
    filters = listOf(string("MediaRouteButtonController", StringComparisonType.ENDS_WITH))
)

internal object MediaRouteButtonControllerUpdateFingerprint : Fingerprint(
    classFingerprint = MediaRouteButtonControllerFingerprint,
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(opcode = Opcode.INVOKE_STATIC, parameters = listOf("L", "I"), returnType = "Z"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        fieldAccess(definingClass = "this", type = "Z", opcode = Opcode.IGET_BOOLEAN, location = MatchAfterImmediately())
    )
)

internal object AudioSinkWriteFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/media/AudioTrack;",
            name = "write",
            parameters = listOf("Ljava/nio/ByteBuffer;", "I", "I"),
            returnType = "I"
        )
    )
)

internal object AudioSinkSetVolumeFingerprint : Fingerprint(
    classFingerprint = AudioSinkWriteFingerprint,
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/media/AudioTrack;",
            name = "setVolume",
            parameters = listOf("F"),
            returnType = "I"
        )
    )
)

private val sonosIsRouteAvailableMethod = findMethodDirect {
    MediaRouteButtonControllerUpdateFingerprint.instructionMatches.first().instruction.methodRef!!
}

val SonosCast = patch(
    name = "Sonos Cast",
    description = "Adds an option to stream the audio to Sonos and other UPnP speakers on the same Wi-Fi network from the Cast button.",
    use = false,
) {
    dependsOn(
        SettingsHook,
        MediaSessionHook,
    )

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_sonos_cast", summary = true)
    )

    try {
        val isRouteAvailable = ::sonosIsRouteAvailableMethod.method

        MediaRouteButtonControllerUpdateFingerprint.hookMethod(
            scopedHook(isRouteAvailable) {
                after { param ->
                    if (innerDepth != 0) return@after
                    val orig = param.result as? Boolean ?: return@after
                    param.result = SonosCastPatch.isCastButtonAvailable(orig)
                }
            }
        )
    } catch (t: Throwable) {
        Logger.printException({ "SonosCast: MediaRouteButtonControllerUpdate hook failed" }, t)
    }

    runCatching {
        MdxMediaRouteButtonPerformClickFingerprint.hookMethod {
            before { param ->
                if (SonosCastPatch.onCastButtonClick(param.thisObject as View)) {
                    param.result = true
                }
            }
        }
    }.onFailure {
        Logger.printException({ "SonosCast: MdxMediaRouteButton hook failed" }, it)
    }

    runCatching {
        val setVolume = AudioTrack::class.java.getDeclaredMethod("setVolume", Float::class.javaPrimitiveType)
        AudioSinkSetVolumeFingerprint.hookMethod(
            scopedHook(setVolume) {
                before { param ->
                    if (innerDepth != 0) return@before
                    param.result = SonosCastPatch.setVolume(
                        param.thisObject as AudioTrack,
                        param.args[0] as Float
                    )
                }
            }
        )

        val write = AudioTrack::class.java.getDeclaredMethod(
            "write",
            ByteBuffer::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        AudioSinkWriteFingerprint.hookMethod(
            scopedHook(write) {
                before { param ->
                    if (innerDepth != 0) return@before
                    param.result = SonosCastPatch.writeAudio(
                        param.thisObject as AudioTrack,
                        param.args[0] as ByteBuffer,
                        param.args[1] as Int,
                        param.args[2] as Int
                    )
                }
            }
        )
    }.onFailure {
        Logger.printException({ "SonosCast: AudioSink hooks failed" }, it)
    }

    setMetadataWithSessionHooks.add { session, metadata ->
        SonosCastPatch.onSetMetadata(session, metadata)
    }
}
