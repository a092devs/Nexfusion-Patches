package io.github.nexalloy.morphe.youtube.video.quality

import app.morphe.extension.youtube.patches.playback.quality.RememberVideoQualityPatch
import com.google.protobuf.MessageLite
import io.github.nexalloy.getIntField
import io.github.nexalloy.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.insertLiteralOverride
import io.github.nexalloy.morphe.youtube.misc.playertype.PlayerTypeHook
import io.github.nexalloy.morphe.youtube.shared.VideoQualityReceiver
import io.github.nexalloy.morphe.youtube.shared.videoQualityChangedFingerprint
import io.github.nexalloy.morphe.youtube.video.information.VideoInformationPatch
import io.github.nexalloy.morphe.youtube.video.information.onCreateHook
import io.github.nexalloy.patch
import io.github.nexalloy.scopedHook
import `j$`.util.Optional

val RememberVideoQuality = patch {
    dependsOn(
        VideoInformationPatch,
        PlayerTypeHook,
    )

    settingsMenuVideoQualityGroup.addAll(
        listOf(
            ListPreference(
                key = "morphe_video_quality_default_mobile",
                entriesKey = "morphe_video_quality_default_entries",
                entryValuesKey = "morphe_video_quality_default_entry_values"
            ),
            ListPreference(
                key = "morphe_video_quality_default_wifi",
                entriesKey = "morphe_video_quality_default_entries",
                entryValuesKey = "morphe_video_quality_default_entry_values"
            ),
            SwitchPreference("morphe_remember_video_quality_last_selected", summary = true),

            ListPreference(
                key = "morphe_shorts_quality_default_mobile",
                entriesKey = "morphe_shorts_quality_default_entries",
                entryValuesKey = "morphe_shorts_quality_default_entry_values",
            ),
            ListPreference(
                key = "morphe_shorts_quality_default_wifi",
                entriesKey = "morphe_shorts_quality_default_entries",
                entryValuesKey = "morphe_shorts_quality_default_entry_values"
            ),
            SwitchPreference("morphe_remember_shorts_quality_last_selected", summary = true),
            SwitchPreference("morphe_remember_video_quality_last_selected_toast", summary = true)
        )
    )

    insertLiteralOverride(45408049L, RememberVideoQualityPatch::useInitialVideoQuality)

    /*
     * The server chooses the quality of SABR streams from the client ABR state sent with each
     * request. The first request of a video sends the initial video quality as the sticky
     * resolution, but the requests sent while the video plays have none, so the server can
     * switch to another quality. Forward every built ClientAbrState to the extension. Upstream
     * injects a patch_overrideClientAbrState wrapper over each return of the SABR request
     * builder; at runtime the same call is made from this after-hook (upstream 3731d97f6).
     */
    ClientAbrStateFingerprint.hookMethod {
        after {
            val original = it.result as? MessageLite ?: return@after
            val overridden = RememberVideoQualityPatch.overrideClientAbrState(original) ?: return@after
            it.result = original.javaClass
                .getMethod("parseFrom", ByteArray::class.java)
                .invoke(null, overridden)
        }
    }

    // Inject a call to override initial video quality.
    ::PlaybackStartParametersInit.hookMethod {
        val initialResolutionField = ::InitialResolutionField.field
        after {
            val oldValue = initialResolutionField.get(it.thisObject)
            val newValue = RememberVideoQualityPatch.getInitialVideoQuality(oldValue as Optional<*>?)
            initialResolutionField.set(it.thisObject, newValue)
        }
    }

    // Inject a call to remember the selected quality for Shorts.
    ::videoQualityItemOnClickFingerprint.hookMethod {
        before { param ->
            RememberVideoQualityPatch.userChangedShortsQuality(null, param.args[2] as Int)
        }
    }

    // Inject a call to remember the user selected quality for regular videos.
    ::videoQualityChangedFingerprint.hookMethod(scopedHook(::VideoQualityReceiver.member) {
        before { param ->
            val selectedQualityIndex = param.args[0].getIntField("a")
            RememberVideoQualityPatch.userChangedQuality(selectedQualityIndex)
        }
    })

    // If this flag is enabled, Shorts restart whenever the quality changes.
    insertLiteralOverride(45387052)
    insertLiteralOverride(45399743)
}