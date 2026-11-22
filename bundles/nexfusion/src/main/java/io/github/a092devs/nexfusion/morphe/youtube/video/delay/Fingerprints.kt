package io.github.a092devs.nexfusion.morphe.youtube.video.delay

import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.opcode
import io.github.a092devs.nexfusion.morphe.string

/**
 * Media3 renderer getPositionUs:
 * `if (state == STATE_STARTED) updateCurrentPosition(); return currentPositionUs;`
 */
private val positionUsFilters = listOf(
    opcode(Opcode.IGET),
    opcode(Opcode.CONST_4),
    opcode(Opcode.IF_NE),
    opcode(Opcode.INVOKE_DIRECT),
    opcode(Opcode.IGET_WIDE),
    opcode(Opcode.RETURN_WIDE),
)

/**
 * The renderer class containing 'MediaCodecAudioRenderer'.
 *
 * The log tag is also used by audio sink listener inner classes that log with the same tag,
 * but those classes do not extend a renderer and have no matching getPositionUs method,
 * so the method fingerprint alone resolves the target (upstream additionally checks that
 * the class superclass is anything other than Object to skip those inner classes).
 */
internal object MediaCodecAudioRendererPositionFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        filters = listOf(string("MediaCodecAudioRenderer")),
    ),
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "J",
    parameters = listOf(),
    filters = positionUsFilters,
)

/**
 * Media3 DecoderAudioRenderer getPositionUs, the media clock used when audio is decoded
 * in software, such as Opus audio decoded by LibopusAudioRenderer.
 */
internal object DecoderAudioRendererPositionFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        filters = listOf(string("DecoderAudioRenderer")),
    ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "J",
    parameters = listOf(),
    filters = positionUsFilters,
)
