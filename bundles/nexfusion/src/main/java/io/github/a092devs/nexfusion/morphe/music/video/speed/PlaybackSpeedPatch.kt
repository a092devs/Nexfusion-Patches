package io.github.a092devs.nexfusion.morphe.music.video.speed

import app.morphe.extension.music.patches.PlaybackSpeedPatch as ExtPlaybackSpeed
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.patches.ExoPlayerInterface
import app.morphe.extension.shared.settings.preference.SeekBarPreference
import io.github.a092devs.nexfusion.createProxy
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.music.video.information.MusicVideoInformation
import io.github.a092devs.nexfusion.morphe.music.video.information.musicVideoTimeHooks
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import io.github.a092devs.nexfusion.morphe.shared.misc.videoinformation.playbackParametersSetterFingerprint
import io.github.a092devs.nexfusion.new
import io.github.a092devs.nexfusion.patch

val PlaybackSpeed = patch(
    name = "Playback speed",
    description = "Adds options to change the playback speed and pitch of tracks.",
    use = false,
) {
    dependsOn(
        SettingsHook,
        MusicVideoInformation,
    )

    PreferenceScreen.PLAYER.addPreferences(
        noTitleUnsortedPreferenceCategory(
            NonInteractivePreference(
                key = "morphe_music_playback_speed",
                tag = SeekBarPreference::class.java,
                selectable = true
            ),
            SwitchPreference("morphe_music_playback_speed_change_pitch")
        )
    )

    runCatching {
        val exoPlayerClass =
            classLoader.loadClass(::playbackParametersSetterFingerprint.dexMethod.className)
        val setPlaybackParametersMethod = ::playbackParametersSetterFingerprint.method
        val playbackParametersClass =
            classLoader.loadClass(::playbackParametersSetterFingerprint.dexMethod.paramTypeNames[0])

        val floatFields = playbackParametersClass.declaredFields.filter { it.type == Float::class.java }
        val speedField = floatFields[0].apply { isAccessible = true }
        val pitchField = floatFields[1].apply { isAccessible = true }

        ::playbackParametersSetterFingerprint.hookMethod {
            before { param ->
                val speed = speedField.getFloat(param.args[0])
                val pitch = pitchField.getFloat(param.args[0])
                param.args[0] = playbackParametersClass.new(
                    ExtPlaybackSpeed.overridePlaybackSpeed(speed),
                    ExtPlaybackSpeed.overridePlaybackPitch(speed, pitch)
                )
            }
        }

        exoPlayerClass.constructors.forEach { ctor ->
            ctor.hookMethod {
                before { param ->
                    ExtPlaybackSpeed.initializeExoPlayer(
                        param.thisObject.createProxy { impl ->
                            ExoPlayerInterface { speed, pitch ->
                                setPlaybackParametersMethod(
                                    impl.get(),
                                    playbackParametersClass.new(speed, pitch)
                                )
                            }
                        }
                    )
                }
            }
        }
    }.onFailure {
        Logger.printException({ "PlaybackSpeed: ExoPlayer hook failed" }, it)
    }

    musicVideoTimeHooks.add { ExtPlaybackSpeed.setVideoTime(it) }
}
