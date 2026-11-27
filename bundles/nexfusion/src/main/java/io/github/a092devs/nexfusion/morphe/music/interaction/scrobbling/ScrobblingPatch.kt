package io.github.a092devs.nexfusion.morphe.music.interaction.scrobbling

import app.morphe.extension.music.patches.scrobbling.ScrobblePatch
import app.morphe.extension.music.settings.preference.LastFMTokenPreference
import app.morphe.extension.music.settings.preference.ListenBrainzTokenPreference
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.settings.preference.SeekBarPreference
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchFirst
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.findFieldDirect
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.music.misc.mediasession.MediaSessionHook
import io.github.a092devs.nexfusion.morphe.music.misc.mediasession.setMetadataHooks
import io.github.a092devs.nexfusion.morphe.music.misc.mediasession.setPlaybackStateHooks
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.music.video.information.MusicVideoInformation
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceCategory
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.TextPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.screenInfoPreferenceCategory
import io.github.a092devs.nexfusion.morphe.string
import io.github.a092devs.nexfusion.patch

internal object EndpointServiceNameFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    parameters = listOf(),
    returnType = "L",
    filters = listOf(
        string("serviceName"),
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "this", type = "Ljava/lang/String;")
    )
)

internal object DislikeFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters = listOf(string("like/dislike"))
)

internal object RequestParameterCheckFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Ljava/lang/String;"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, smali = "Ljava/lang/String;->isEmpty()Z"),
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Ljava/lang/String;")
    )
)

private val endpointServiceNameField = findFieldDirect {
    EndpointServiceNameFingerprint.instructionMatches.last().instruction.fieldRef!!
}

private val requestVideoIdField = findFieldDirect {
    RequestParameterCheckFingerprint.instructionMatches.last().instruction.fieldRef!!
}

val Scrobbling = patch(
    name = "Scrobbling",
    description = "Adds options to add played tracks to Last.fm and ListenBrainz.",
    use = false,
) {
    dependsOn(
        SettingsHook,
        MediaSessionHook,
        MusicVideoInformation,
    )

    PreferenceScreen.SCROBBLING.addPreferences(
        PreferenceCategory(
            key = "morphe_music_listenbrainz",
            preferences = setOf(
                SwitchPreference(
                    key = "morphe_music_listenbrainz_enabled",
                    titleKey = "morphe_music_scrobbling_enabled_title"
                ),
                NonInteractivePreference(
                    key = "morphe_music_listenbrainz_token",
                    titleKey = "morphe_music_listenbrainz_token_title",
                    summaryKey = null,
                    tag = ListenBrainzTokenPreference::class.java,
                    selectable = true
                ),
                SwitchPreference(
                    key = "morphe_music_listenbrainz_now_playing",
                    titleKey = "morphe_music_scrobbling_now_playing_title"
                ),
                NonInteractivePreference(
                    key = "morphe_music_listenbrainz_min_song_duration",
                    titleKey = "morphe_music_scrobbling_min_song_duration_title",
                    summaryKey = "morphe_music_scrobbling_min_song_duration_summary",
                    tag = SeekBarPreference::class.java,
                    selectable = true
                ),
                NonInteractivePreference(
                    key = "morphe_music_listenbrainz_delay_percent",
                    titleKey = "morphe_music_scrobbling_delay_percent_title",
                    summaryKey = "morphe_music_scrobbling_delay_percent_summary",
                    tag = SeekBarPreference::class.java,
                    selectable = true
                ),
                NonInteractivePreference(
                    key = "morphe_music_listenbrainz_delay_seconds",
                    titleKey = "morphe_music_scrobbling_delay_seconds_title",
                    summaryKey = "morphe_music_scrobbling_delay_seconds_summary",
                    tag = SeekBarPreference::class.java,
                    selectable = true
                )
            ),
            sorting = Sorting.UNSORTED
        ),
        PreferenceCategory(
            key = "morphe_music_lastfm",
            preferences = setOf(
                SwitchPreference(
                    key = "morphe_music_lastfm_enabled",
                    titleKey = "morphe_music_scrobbling_enabled_title"
                ),
                NonInteractivePreference(
                    key = "morphe_music_lastfm_session_key",
                    titleKey = "morphe_music_lastfm_token_title",
                    summaryKey = null,
                    tag = LastFMTokenPreference::class.java,
                    selectable = true
                ),
                SwitchPreference(
                    key = "morphe_music_lastfm_now_playing",
                    titleKey = "morphe_music_scrobbling_now_playing_title"
                ),
                SwitchPreference("morphe_music_lastfm_love_on_like", summary = true),
                NonInteractivePreference(
                    key = "morphe_music_lastfm_min_song_duration",
                    titleKey = "morphe_music_scrobbling_min_song_duration_title",
                    summaryKey = "morphe_music_scrobbling_min_song_duration_summary",
                    tag = SeekBarPreference::class.java,
                    selectable = true
                ),
                NonInteractivePreference(
                    key = "morphe_music_lastfm_delay_percent",
                    titleKey = "morphe_music_scrobbling_delay_percent_title",
                    summaryKey = "morphe_music_scrobbling_delay_percent_summary",
                    tag = SeekBarPreference::class.java,
                    selectable = true
                ),
                NonInteractivePreference(
                    key = "morphe_music_lastfm_delay_seconds",
                    titleKey = "morphe_music_scrobbling_delay_seconds_title",
                    summaryKey = "morphe_music_scrobbling_delay_seconds_summary",
                    tag = SeekBarPreference::class.java,
                    selectable = true
                )
            ),
            sorting = Sorting.UNSORTED
        ),
        PreferenceCategory(
            key = "morphe_settings_music_scrobbling_metadata",
            preferences = setOf(
                SwitchPreference("morphe_music_scrobbling_metadata_cleanup"),
                TextPreference("morphe_music_scrobbling_custom_regex"),
                SwitchPreference("morphe_music_scrobbling_parse_title", summary = true),
                SwitchPreference("morphe_music_scrobbling_guess_album", summary = true)
            )
        ),
        screenInfoPreferenceCategory(
            key = "morphe_music_scrobbling_about_category",
            preferences = setOf(
                NonInteractivePreference(
                    key = "morphe_music_scrobbling_about",
                    titleKey = null,
                    summaryKey = "morphe_music_scrobbling_about_summary"
                )
            )
        )
    )

    setPlaybackStateHooks.add { ScrobblePatch.onSetPlaybackState(it) }
    setMetadataHooks.add { ScrobblePatch.onSetMetadata(it) }

    try {
        val serviceNameField = ::endpointServiceNameField.field.apply { isAccessible = true }
        val videoIdField = ::requestVideoIdField.field.apply { isAccessible = true }
        val dislikeSuper = DislikeFingerprint.constructor.declaringClass.superclass!!
        val targetField = dislikeSuper.declaredFields.firstOrNull {
            it.type == videoIdField.declaringClass
        }?.apply { isAccessible = true }

        val likeEndpointParserFp = Fingerprint(
            definingClass = "L${dislikeSuper.name.replace('.', '/')};",
            returnType = "V",
            filters = listOf(
                fieldAccess(opcode = Opcode.SGET_OBJECT, location = MatchFirst()),
                fieldAccess(opcode = Opcode.IPUT_OBJECT, definingClass = "this"),
                string("")
            )
        )

        if (targetField != null) {
            likeEndpointParserFp.hookMethod {
                after { param ->
                    val serviceName = serviceNameField.get(param.thisObject) as? String ?: return@after
                    val target = targetField.get(param.thisObject) ?: return@after
                    val videoId = videoIdField.get(target) as? String ?: return@after
                    ScrobblePatch.onLikeClicked(serviceName, videoId)
                }
            }
        }
    } catch (t: Throwable) {
        Logger.printException({ "Scrobbling: LikeEndpointParser hook failed" }, t)
    }
}
