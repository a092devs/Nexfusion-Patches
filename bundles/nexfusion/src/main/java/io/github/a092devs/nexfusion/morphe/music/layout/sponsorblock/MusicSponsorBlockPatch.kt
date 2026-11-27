package io.github.a092devs.nexfusion.morphe.music.layout.sponsorblock

import android.graphics.Canvas
import android.graphics.Rect
import app.morphe.extension.music.sponsorblock.MusicSponsorBlockConfig
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.sponsorblock.ui.SponsorBlockAboutPreference
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.findFieldDirect
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.music.video.information.MusicVideoInformation
import io.github.a092devs.nexfusion.morphe.music.video.information.musicVideoIdHooks
import io.github.a092devs.nexfusion.morphe.music.video.information.musicVideoTimeHooks
import io.github.a092devs.nexfusion.morphe.music.video.information.onMusicCreateHook
import io.github.a092devs.nexfusion.morphe.opcode
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceCategory
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.TextPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.screenInfoPreferenceCategory
import io.github.a092devs.nexfusion.morphe.string
import io.github.a092devs.nexfusion.morphe.youtube.layout.sponsorblock.categoryPreference
import io.github.a092devs.nexfusion.patch

private const val REMOVE_OFFLINE_TRACK_STRING = "Couldn't delete single track through orchestration: %s"

internal object MusicTimeBarDrawFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/music/watchpage/MusicPlaybackControlsTimeBar;",
    name = "draw",
    returnType = "V"
)

internal object MusicTimeBarOnMeasureFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/music/watchpage/MusicPlaybackControlsTimeBar;",
    name = "onMeasure",
    returnType = "V",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Landroid/graphics/Rect;",
        )
    )
)

internal object MusicOfflineVideoEndpointFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "Ljava/util/Map;"),
    strings = listOf("Object is not an offlineable video: %s"),
    filters = listOf(
        methodCall(
            opcodes = listOf(Opcode.INVOKE_INTERFACE, Opcode.INVOKE_VIRTUAL),
            parameters = listOf("Ljava/lang/String;", "L", "L", "L"),
            returnType = "V"
        )
    )
)

internal object MusicRemoveOfflineTrackFingerprint : Fingerprint(
    returnType = "L",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
    filters = listOf(string(REMOVE_OFFLINE_TRACK_STRING))
)

internal object MusicRemoveOfflineTrackActionFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(
        opcode(Opcode.RETURN_VOID),
        string(REMOVE_OFFLINE_TRACK_STRING)
    )
)

private val musicTimeBarRectField = findFieldDirect {
    MusicTimeBarOnMeasureFingerprint.instructionMatches.last().instruction.fieldRef!!
}

val MusicSponsorBlock = patch(
    name = "SponsorBlock",
    description = "Adds options to enable and configure SponsorBlock, which can skip non-music segments.",
    use = false,
) {
    dependsOn(
        MusicVideoInformation,
        SettingsHook,
    )

    PreferenceScreen.SPONSORBLOCK.addPreferences(
        SwitchPreference("morphe_sb_enabled", summary = true),
        SwitchPreference("morphe_sb_toast_on_skip", titleKey = "morphe_toast_on_skip_title", summary = true),

        PreferenceCategory(
            key = "morphe_sb_diff_segments",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                categoryPreference("morphe_sb_sponsor_color"),
                categoryPreference("morphe_sb_selfpromo_color"),
                categoryPreference("morphe_sb_interaction_color"),
                categoryPreference("morphe_sb_intro_color"),
                categoryPreference("morphe_sb_outro_color"),
                categoryPreference("morphe_sb_preview_color"),
                categoryPreference("morphe_sb_hook_color"),
                categoryPreference("morphe_sb_filler_color"),
                categoryPreference("morphe_sb_music_offtopic_color")
            )
        ),

        PreferenceCategory(
            key = "morphe_sb_general",
            titleKey = "morphe_general_title",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_sb_offline_segments", summary = true),
                SwitchPreference(
                    "morphe_sb_toast_on_connection_error",
                    titleKey = "morphe_toast_on_connection_error_title",
                    summary = true
                ),
                TextPreference("morphe_sb_api_url")
            )
        ),

        screenInfoPreferenceCategory(
            key = "morphe_sb_about",
            preferences = setOf(
                NonInteractivePreference(
                    key = "morphe_sb_about_api",
                    titleKey = null,
                    tag = SponsorBlockAboutPreference::class.java,
                    selectable = true
                )
            )
        )
    )

    onMusicCreateHook.add { MusicSponsorBlockConfig.initialize() }
    musicVideoTimeHooks.add { MusicSponsorBlockConfig.setVideoTime(it) }
    musicVideoIdHooks.add { MusicSponsorBlockConfig.setCurrentVideoId(it) }

    try {
        val rectField = ::musicTimeBarRectField.field.apply { isAccessible = true }

        MusicTimeBarDrawFingerprint.hookMethod {
            after { param ->
                val rect = rectField.get(param.thisObject) as? Rect
                MusicSponsorBlockConfig.setSeekbarRectangle(rect)
                MusicSponsorBlockConfig.drawSegmentTimeBars(param.args[0] as Canvas)
            }
        }
    } catch (t: Throwable) {
        Logger.printException({ "MusicSponsorBlock: TimeBar hook failed" }, t)
    }

    runCatching {
        MusicRemoveOfflineTrackFingerprint.hookMethod {
            before { param ->
                val videoId = param.args.firstOrNull() as? String
                MusicSponsorBlockConfig.offlineVideoRemoved(videoId)
            }
        }
    }.onFailure {
        Logger.printException({ "MusicSponsorBlock: RemoveOfflineTrack hook failed" }, it)
    }

    runCatching {
        MusicRemoveOfflineTrackActionFingerprint.hookMethod {
            before { param ->
                val videoId = param.args.firstOrNull() as? String
                MusicSponsorBlockConfig.offlineVideoRemoved(videoId)
            }
        }
    }.onFailure {
        Logger.printException({ "MusicSponsorBlock: RemoveOfflineTrackAction hook failed" }, it)
    }
}
