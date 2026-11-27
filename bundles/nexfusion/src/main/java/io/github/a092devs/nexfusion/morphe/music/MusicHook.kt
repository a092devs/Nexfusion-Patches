package io.github.a092devs.nexfusion.morphe.music

import android.app.Activity
import app.morphe.extension.shared.Utils
import io.github.a092devs.nexfusion.addModuleAssets
import io.github.a092devs.nexfusion.hookExtensionClassLoading
import io.github.a092devs.nexfusion.injectHostClassLoaderToSelf
import io.github.a092devs.nexfusion.morphe.music.ad.HideAds
import io.github.a092devs.nexfusion.morphe.music.audio.exclusiveaudio.EnableExclusiveAudioPlayback
import io.github.a092devs.nexfusion.morphe.music.interaction.dialog.RemoveViewerDiscretionDialog
import io.github.a092devs.nexfusion.morphe.music.interaction.dislikeredirection.DisableDislikeRedirection
import io.github.a092devs.nexfusion.morphe.music.interaction.downloads.Downloads
import io.github.a092devs.nexfusion.morphe.music.interaction.likefilter.QueueLikeFilter
import io.github.a092devs.nexfusion.morphe.music.interaction.loop.LoopVideo
import io.github.a092devs.nexfusion.morphe.music.interaction.playlisttracktap.PlaylistTrackTap
import io.github.a092devs.nexfusion.morphe.music.interaction.remember.repeatstate.RememberRepeatState
import io.github.a092devs.nexfusion.morphe.music.interaction.remember.shufflestate.RememberShuffleState
import io.github.a092devs.nexfusion.morphe.music.interaction.scrobbling.Scrobbling
import io.github.a092devs.nexfusion.morphe.music.interaction.sonos.SonosCast
import io.github.a092devs.nexfusion.morphe.music.interaction.soundproof.SoundProof
import io.github.a092devs.nexfusion.morphe.music.layout.applemusicartwork.AppleMusicArtwork
import io.github.a092devs.nexfusion.morphe.music.layout.buttons.navigation.NavigationBar
import io.github.a092devs.nexfusion.morphe.music.layout.captions.DisableAutoCaptions
import io.github.a092devs.nexfusion.morphe.music.layout.glassmenus.GlassMenus
import io.github.a092devs.nexfusion.morphe.music.layout.hide.general.HideLayoutComponents
import io.github.a092devs.nexfusion.morphe.music.layout.lyrics.ThirdPartyLyrics
import io.github.a092devs.nexfusion.morphe.music.layout.sponsorblock.MusicSponsorBlock
import io.github.a092devs.nexfusion.morphe.music.layout.upgradebutton.HideUpgradeButton
import io.github.a092devs.nexfusion.morphe.music.misc.album.PlayAlbumSongs
import io.github.a092devs.nexfusion.morphe.music.misc.androidauto.AndroidAuto
import io.github.a092devs.nexfusion.morphe.music.misc.audio.drc.DisableDRCAudio
import io.github.a092devs.nexfusion.morphe.music.misc.backgroundplayback.BackgroundPlayback
import io.github.a092devs.nexfusion.morphe.music.misc.debugging.EnableDebugging
import io.github.a092devs.nexfusion.morphe.music.misc.mediasession.MediaSessionHook
import io.github.a092devs.nexfusion.morphe.music.misc.privacy.SanitizeSharingLinks
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.music.misc.sharesheet.OpenSystemShareSheet
import io.github.a092devs.nexfusion.morphe.music.shared.MusicActivityOnCreateFingerprint
import io.github.a092devs.nexfusion.morphe.music.video.audio.ForceOriginalAudio
import io.github.a092devs.nexfusion.morphe.music.video.audio.SkipSilence
import io.github.a092devs.nexfusion.morphe.music.video.effects.VideoEffects
import io.github.a092devs.nexfusion.morphe.music.video.information.MusicVideoInformation
import io.github.a092devs.nexfusion.morphe.music.video.quality.RememberVideoQuality
import io.github.a092devs.nexfusion.morphe.music.video.speed.PlaybackSpeed
import io.github.a092devs.nexfusion.morphe.shared.misc.CheckRecycleBitmapMediaSession
import io.github.a092devs.nexfusion.patch

val ExtensionHook = patch(name = "<ExtensionHook>") {
    injectHostClassLoaderToSelf(bundle.classLoader, classLoader)
    hookExtensionClassLoading(bundle.classLoader)
    runCatching {
        MusicActivityOnCreateFingerprint.hookMethod {
            before {
                val activity = it.thisObject as Activity
                activity.addModuleAssets()
                Utils.setContext(activity)
            }
        }
    }
}

val PauseWhenMuted = patch(
    name = "Pause when muted",
    description = "Adds an option to pause playback automatically when volume is lowered to zero.",
    use = false,
) {
    runCatching {
        val mediaSessionClass = android.media.session.MediaSession::class.java
        io.github.a092devs.nexfusion.compat.XposedHelpers.findAndHookMethod(
            mediaSessionClass,
            "setMetadata",
            android.media.MediaMetadata::class.java,
            object : io.github.a092devs.nexfusion.compat.XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    app.morphe.extension.music.patches.PauseWhenMutedPatch.onSetMetadata(
                        param.thisObject as android.media.session.MediaSession,
                        param.args[0] as? android.media.MediaMetadata
                    )
                }
            }
        )
        io.github.a092devs.nexfusion.compat.XposedHelpers.findAndHookMethod(
            mediaSessionClass,
            "setPlaybackState",
            android.media.session.PlaybackState::class.java,
            object : io.github.a092devs.nexfusion.compat.XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    app.morphe.extension.music.patches.PauseWhenMutedPatch.onSetPlaybackState(
                        param.args[0] as? android.media.session.PlaybackState
                    )
                }
            }
        )
    }
}

val ReturnYouTubeDislike = patch(
    name = "Return YouTube Dislike",
    description = "Displays public dislike counts on music videos and tracks.",
    use = false,
) {
    dependsOn(io.github.a092devs.nexfusion.morphe.shared.misc.textcomponent.textComponentPatch)

    io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen.RETURN_YOUTUBE_DISLIKE.addPreferences(
        io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference("morphe_ryd_enabled"),
        io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference("morphe_ryd_dislike_percentage", summary = true),
        io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference("morphe_ryd_estimated_like", summary = true),
        io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference("morphe_ryd_toast_on_connection_error", titleKey = "morphe_toast_on_connection_error_title", summary = true),
        io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.screenInfoPreferenceCategory(
            key = "morphe_ryd_about",
            preferences = setOf(
                io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference(
                    key = "morphe_ryd_attribution",
                    titleKey = null,
                    tag = app.morphe.extension.shared.returnyoutubedislike.ui.ReturnYouTubeDislikeAboutPreference::class.java,
                    selectable = true
                )
            )
        ),
        io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceCategory(
            key = "morphe_ryd_statistics_category",
            sorting = io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = emptySet(),
            tag = app.morphe.extension.shared.returnyoutubedislike.ui.ReturnYouTubeDislikeDebugStatsPreferenceCategory::class.java
        )
    )

    runCatching {
        val videoIdMethod = dexkit.getMethodDirectOrNull("music_ryd_video_id") {
            findMethod {
                matcher {
                    usingStrings("Null initialPlayabilityStatus")
                }
            }.firstOrNull {
                val p = it.paramTypeNames
                (p.size == 2 && p[1] == "java.lang.String") ||
                    (p.size == 3 && p[1] == "java.lang.String" && p[2] == "boolean")
            }
        }
        if (videoIdMethod != null) {
            val cls = classLoader.loadClass(videoIdMethod.className)
            val m = cls.declaredMethods.firstOrNull {
                it.name == videoIdMethod.name && it.parameterCount == videoIdMethod.paramTypeNames.size
            }
            if (m != null) {
                io.github.a092devs.nexfusion.compat.XposedBridge.hookMethod(
                    m,
                    object : io.github.a092devs.nexfusion.compat.XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val videoId = param.args.getOrNull(1) as? String ?: return
                            app.morphe.extension.music.patches.ReturnYouTubeDislikePatch.newVideoLoaded(videoId)
                        }
                    }
                )
            }
        }
    }

    io.github.a092devs.nexfusion.morphe.shared.misc.textcomponent.hookSpannableStringOverride { ctx, orig ->
        app.morphe.extension.music.patches.ReturnYouTubeDislikePatch.onLithoTextLoaded(ctx, orig)
    }

    runCatching {
        val componentHost = classLoader.loadClass("com.facebook.litho.ComponentHost")
        io.github.a092devs.nexfusion.compat.XposedHelpers.findAndHookMethod(
            componentHost,
            "setContentDescription",
            CharSequence::class.java,
            object : io.github.a092devs.nexfusion.compat.XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val host = param.thisObject as? com.facebook.litho.ComponentHost ?: return
                    param.args[0] = app.morphe.extension.music.patches.ReturnYouTubeDislikePatch
                        .onComponentHostContentDescription(host, param.args[0] as? CharSequence)
                }
            }
        )
    }

    runCatching {
        val yogaNative = classLoader.loadClass("com.facebook.yoga.YogaNative")
        io.github.a092devs.nexfusion.compat.XposedHelpers.findAndHookMethod(
            yogaNative,
            "jni_YGNodeStyleSetWidthJNI",
            Long::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            object : io.github.a092devs.nexfusion.compat.XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val ptr = (param.args[0] as? Number)?.toLong() ?: return
                    val width = (param.args[1] as? Number)?.toFloat() ?: return
                    app.morphe.extension.music.patches.ReturnYouTubeDislikePatch.onYogaSetWidth(ptr, width)
                }
            }
        )
    }
}

val DisableAudioDucking = patch(
    name = "Disable audio ducking",
    description = "Prevents YouTube Music from lowering audio volume when navigation or system notifications trigger.",
    use = false,
) {
    runCatching {
        io.github.a092devs.nexfusion.morphe.Fingerprint(
            name = "onAudioFocusChange",
            returnType = "V",
            parameters = listOf("I"),
            filters = listOf(io.github.a092devs.nexfusion.morphe.string("AudioFocus DUCK"))
        ).hookMethod {
            before { param ->
                val focusChange = param.args.firstOrNull() as? Int ?: return@before
                if (app.morphe.extension.music.patches.DisableAudioDuckingPatch.ignoreAudioFocusChange(focusChange)) {
                    param.result = Unit
                }
            }
        }
    }
    runCatching {
        val compatClass = classLoader.loadClass("androidx.media.AudioFocusRequestCompat")
        io.github.a092devs.nexfusion.compat.XposedBridge.hookAllConstructors(
            compatClass,
            object : io.github.a092devs.nexfusion.compat.XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val lastIdx = param.args.lastIndex
                    if (lastIdx >= 0 && param.args[lastIdx] is Boolean) {
                        param.args[lastIdx] = app.morphe.extension.music.patches.DisableAudioDuckingPatch
                            .willPauseWhenDucked(param.args[lastIdx] as Boolean)
                    }
                }
            }
        )
    }
}

val YTMusicPatches = arrayOf(
    ExtensionHook,
    MediaSessionHook,
    MusicVideoInformation,
    BackgroundPlayback,
    HideUpgradeButton,
    NavigationBar,
    HideAds,
    HideLayoutComponents,
    MusicSponsorBlock,
    EnableExclusiveAudioPlayback,
    CheckRecycleBitmapMediaSession,
    EnableDebugging,
    SanitizeSharingLinks,
    OpenSystemShareSheet,
    RememberVideoQuality,
    DisableAutoCaptions,
    VideoEffects,
    DisableDRCAudio,
    PlayAlbumSongs,
    GlassMenus,
    LoopVideo,
    PauseWhenMuted,
    PlaylistTrackTap,
    QueueLikeFilter,
    RememberRepeatState,
    RememberShuffleState,
    RemoveViewerDiscretionDialog,
    DisableDislikeRedirection,
    ForceOriginalAudio,
    SkipSilence,
    PlaybackSpeed,
    ThirdPartyLyrics,
    SoundProof,
    Scrobbling,
    AppleMusicArtwork,
    SonosCast,
    ReturnYouTubeDislike,
    DisableAudioDucking,
    Downloads,
    AndroidAuto,
    SettingsHook
)
