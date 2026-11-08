package io.github.nexfusion.morphe.youtube.interaction.seekbar

import app.morphe.extension.youtube.patches.LiveStreamDVRPatch
import app.morphe.extension.youtube.settings.preference.LiveStreamDVRDurationPreference
import app.morphe.extension.youtube.settings.preference.LiveStreamDVRPreference
import io.github.nexfusion.morphe.AccessFlags
import io.github.nexfusion.morphe.Fingerprint
import io.github.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexfusion.morphe.Opcode
import io.github.nexfusion.morphe.fieldAccess
import io.github.nexfusion.morphe.literal
import io.github.nexfusion.morphe.opcode
import io.github.nexfusion.morphe.shared.FormatStreamModelToStringFingerprint
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexfusion.morphe.youtube.shared.VideoStreamingDataToStringFingerprint
import io.github.nexfusion.patch

internal object VideoStreamingDataAllowSeekingFingerprint : Fingerprint(
    classFingerprint = VideoStreamingDataToStringFingerprint,
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        literal(8),
        opcode(Opcode.IF_EQ, location = MatchAfterImmediately()),
        literal(1, location = MatchAfterImmediately()),
    )
)

internal object FormatStreamModelMaxDVRDurationFingerprint : Fingerprint(
    classFingerprint = FormatStreamModelToStringFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "D",
    parameters = listOf(),
    filters = listOf(
        opcode(Opcode.IGET_OBJECT),
        fieldAccess(opcode = Opcode.IGET_WIDE, type = "D", location = MatchAfterImmediately()),
        opcode(Opcode.RETURN_WIDE, location = MatchAfterImmediately()),
    )
)

val liveStreamDVRPatch = patch(
    name = "Live stream DVR",
    description = "Enables video seeking on live streams that have disabled DVR (Digital Video Recorder).",
) {
    PreferenceScreen.SEEKBAR.addPreferences(
        noTitleUnsortedPreferenceCategory(
            SwitchPreference(
                key = "morphe_live_stream_dvr",
                summary = true,
                tag = LiveStreamDVRPreference::class.java
            ),
            SwitchPreference(
                key = "morphe_expand_live_stream_dvr_duration",
                summary = true,
                tag = LiveStreamDVRDurationPreference::class.java
            )
        )
    )

    runCatching {
        VideoStreamingDataAllowSeekingFingerprint.hookMethod {
            after { param ->
                val orig = param.result as? Boolean ?: return@after
                param.result = LiveStreamDVRPatch.enableLiveStreamDVR(orig)
            }
        }
    }

    runCatching {
        FormatStreamModelMaxDVRDurationFingerprint.hookMethod {
            after { param ->
                val orig = (param.result as? Number)?.toDouble() ?: return@after
                param.result = LiveStreamDVRPatch.overrideMaxDVRDurationSeconds(orig)
            }
        }
    }
}
