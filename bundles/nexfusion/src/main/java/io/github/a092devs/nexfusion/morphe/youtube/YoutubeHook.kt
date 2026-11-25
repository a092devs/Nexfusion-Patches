package io.github.a092devs.nexfusion.morphe.youtube

import android.app.Activity
import app.morphe.extension.shared.Utils
import io.github.libxposed.api.XposedInterface
import io.github.a092devs.nexfusion.addModuleAssets
import io.github.a092devs.nexfusion.injectHostClassLoaderToSelf
import io.github.a092devs.nexfusion.hookExtensionClassLoading
import io.github.a092devs.nexfusion.morphe.shared.misc.debugging.experimentalIndexedBooleanFeatureFlagFingerprint
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.is_21_42_or_greater
import io.github.a092devs.nexfusion.morphe.shared.misc.CheckRecycleBitmapMediaSession
import io.github.a092devs.nexfusion.morphe.shared.misc.debugging.experimentalBooleanFeatureFlagFingerprint
import io.github.a092devs.nexfusion.morphe.youtube.ad.HideAds
import io.github.a092devs.nexfusion.morphe.youtube.interaction.autoplay.AutoplayControl
import io.github.a092devs.nexfusion.morphe.youtube.interaction.channelsearch.ChannelSearch
import io.github.a092devs.nexfusion.morphe.youtube.interaction.copyvideolink.CopyVideoLinkButtonPatch
import io.github.a092devs.nexfusion.morphe.youtube.interaction.downloads.Downloads
import io.github.a092devs.nexfusion.morphe.youtube.interaction.doubletap.disableChapterSkipDoubleTapPatch
import io.github.a092devs.nexfusion.morphe.youtube.interaction.onthego.onTheGoPatch
import io.github.a092devs.nexfusion.morphe.youtube.interaction.playlistpanel.PlaylistPanelButton
import io.github.a092devs.nexfusion.morphe.youtube.interaction.seekbar.seekbarPatch
import io.github.a092devs.nexfusion.morphe.youtube.interaction.shorts.SkipLiveShorts
import io.github.a092devs.nexfusion.morphe.youtube.interaction.swipecontrols.SwipeControls
import io.github.a092devs.nexfusion.morphe.youtube.layout.buttons.action.HideVideoActionButtons
import io.github.a092devs.nexfusion.morphe.youtube.layout.buttons.navigation.NavigationBar
import io.github.a092devs.nexfusion.morphe.youtube.layout.captions.AutoCaptions
import io.github.a092devs.nexfusion.morphe.youtube.layout.captions.CaptionsInPip
import io.github.a092devs.nexfusion.morphe.youtube.layout.dearrow.deArrowPatch
import io.github.a092devs.nexfusion.morphe.youtube.layout.sponsorblock.SponsorBlock
import io.github.a092devs.nexfusion.morphe.youtube.layout.hide.ambientmode.AmbientMode
import io.github.a092devs.nexfusion.morphe.youtube.layout.hide.general.HideLayoutComponents
import io.github.a092devs.nexfusion.morphe.youtube.layout.hide.shorts.HideShortsComponents
import io.github.a092devs.nexfusion.morphe.youtube.layout.player.playlistpanel.FullscreenPlaylistPanel
import io.github.a092devs.nexfusion.morphe.youtube.layout.statusbar.HideStatusBar
import io.github.a092devs.nexfusion.morphe.youtube.layout.miniplayer.Miniplayer
import io.github.a092devs.nexfusion.morphe.youtube.layout.player.pip.PipSeekButtons
import io.github.a092devs.nexfusion.morphe.youtube.layout.playlistautoplay.DisablePlaylistAutoplay
import io.github.a092devs.nexfusion.morphe.youtube.layout.shortsnoresume.DisableShortsResumingOnStartup
import io.github.a092devs.nexfusion.morphe.youtube.layout.startpage.changeStartPagePatch
import io.github.a092devs.nexfusion.morphe.youtube.layout.thumbnails.BypassImageRegionRestrictionsPatch
import io.github.a092devs.nexfusion.morphe.youtube.misc.backgroundplayback.BackgroundPlayback
import io.github.a092devs.nexfusion.morphe.youtube.misc.commandrouter.CommandRouterHook
import io.github.a092devs.nexfusion.morphe.youtube.misc.debugging.EnableDebugging
import io.github.a092devs.nexfusion.morphe.youtube.misc.privacy.SanitizeSharingLinks
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.youtube.misc.verticalscroll.FixVerticalScroll
import io.github.a092devs.nexfusion.morphe.youtube.shared.YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE
import io.github.a092devs.nexfusion.morphe.youtube.shared.mainActivityClass
import io.github.a092devs.nexfusion.morphe.youtube.video.audio.ForceOriginalAudio
import io.github.a092devs.nexfusion.morphe.youtube.video.audio.DisableDRCAudio
import io.github.a092devs.nexfusion.morphe.youtube.video.effects.VideoEffects
import io.github.a092devs.nexfusion.morphe.youtube.layout.glassmenus.GlassMenus
import io.github.a092devs.nexfusion.morphe.youtube.video.codecs.DisableVideoCodecs
import io.github.a092devs.nexfusion.morphe.youtube.video.delay.VideoDelay
import io.github.a092devs.nexfusion.morphe.youtube.layout.theme.Theme
import io.github.a092devs.nexfusion.morphe.youtube.video.quality.VideoQuality
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.VersionCheck
import io.github.a092devs.nexfusion.morphe.youtube.video.speed.PlaybackSpeed
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.InputType
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceCategory
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.TextPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.screenInfoPreferenceCategory
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch
import org.luckypray.dexkit.wrap.DexMethod

val ExtensionHook = patch(name = "<ExtensionHook>") {
    dependsOn(VersionCheck)
    injectHostClassLoaderToSelf(bundle.classLoader, classLoader)
    hookExtensionClassLoading(bundle.classLoader)
    val hooked = runCatching {
        DexMethod("$YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE->onCreate(Landroid/os/Bundle;)V").hookMethod {
            before {
                val mainActivity = it.thisObject as Activity
                mainActivity.addModuleAssets()
                Utils.setContext(mainActivity)
            }
        }
    }.isSuccess
    if (!hooked) {
        runCatching {
            val targetClass = runCatching { classLoader.loadClass("com.google.android.apps.youtube.app.watchwhile.MainActivity") }
                .getOrNull() ?: ::mainActivityClass.clazz
            io.github.a092devs.nexfusion.compat.XposedHelpers.findAndHookMethod(
                targetClass, "onCreate", android.os.Bundle::class.java,
                object : io.github.a092devs.nexfusion.compat.XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val mainActivity = param.thisObject as Activity
                        mainActivity.addModuleAssets()
                        Utils.setContext(mainActivity)
                    }
                }
            )
        }
    }
}

private val overrides = mutableMapOf<Long, (Boolean) -> Boolean>()

fun insertLiteralOverride(id: Long, override: Boolean = false) {
    overrides[id] = { override }
}

fun insertLiteralOverride(id: Long, override: (Boolean) -> Boolean) {
    overrides[id] = override
}

val FeatureOverride = patch {
    dependsOn(VersionCheck)
    runCatching {
        ::experimentalBooleanFeatureFlagFingerprint.hookMethod {
            priority = XposedInterface.PRIORITY_DEFAULT - 1
            after { param ->
                val args = param.args ?: return@after
                val id = (args.getOrNull(args.size - 2) as? Long) ?: return@after
                val orig = (param.result as? Boolean) ?: return@after
                val match = overrides[id] ?: return@after
                param.result = match(orig)
            }
        }
    }
    if (is_21_42_or_greater) {
        insertLiteralOverride(45857115L, true) // Media session owner check
        runCatching {
            ::experimentalIndexedBooleanFeatureFlagFingerprint.hookMethod {
                priority = XposedInterface.PRIORITY_DEFAULT - 1
                after { param ->
                    val args = param.args ?: return@after
                    val id = (args.getOrNull(args.size - 2) as? Long) ?: return@after
                    val orig = (param.result as? Boolean) ?: return@after
                    val match = overrides[id] ?: return@after
                    param.result = match(orig)
                }
            }
        }
    }
}

val HoldGesture = patch(
    name = "Hold gesture",
    description = "Adds options to change the playback speed, to keep it and to run actions by dragging after tapping and holding the video player.",
    use = false,
) {
}

val ReturnYouTubeUsername = patch(
    name = "Return YouTube Username",
    description = "Adds an option to show channel names instead of handles in comments, below the video title and in Shorts.",
    use = false,
) {
}

val ReturnYouTubeDislike = patch(
    name = "Return YouTube Dislike",
    description = "Adds an option to show the dislike count of videos with Return YouTube Dislike, and the like count and like ratio on the thumbnails of feed videos.",
    use = false,
) {
    dependsOn(
        io.github.a092devs.nexfusion.morphe.shared.misc.textcomponent.textComponentPatch,
        io.github.a092devs.nexfusion.morphe.youtube.video.videoid.VideoId,
        io.github.a092devs.nexfusion.morphe.youtube.misc.playertype.PlayerTypeHook,
    )

    PreferenceScreen.RETURN_YOUTUBE_DISLIKE.addPreferences(
        SwitchPreference("morphe_ryd_enabled"),
        SwitchPreference("morphe_ryd_dislike_percentage", summary = true),
        SwitchPreference("morphe_ryd_feed_like_count"),
        SwitchPreference("morphe_ryd_feed_like_ratio"),
        ListPreference("morphe_ryd_feed_counts_position"),
        SwitchPreference("morphe_ryd_estimated_like", summary = true),
        SwitchPreference("morphe_ryd_toast_on_connection_error", summary = true),
        screenInfoPreferenceCategory(
            key = "morphe_ryd_about",
            preferences = setOf(
                NonInteractivePreference(
                    key = "morphe_ryd_attribution",
                    titleKey = null,
                    tag = app.morphe.extension.shared.returnyoutubedislike.ui.ReturnYouTubeDislikeAboutPreference::class.java,
                    selectable = true
                )
            )
        ),
        PreferenceCategory(
            key = "morphe_ryd_statistics_category",
            sorting = Sorting.UNSORTED,
            preferences = emptySet(),
            tag = app.morphe.extension.shared.returnyoutubedislike.ui.ReturnYouTubeDislikeDebugStatsPreferenceCategory::class.java
        )
    )

    runCatching {
        io.github.a092devs.nexfusion.morphe.setExtensionIsPatchIncluded(
            app.morphe.extension.youtube.patches.ReturnYouTubeDislikePatch::class.java
        )
    }

    io.github.a092devs.nexfusion.morphe.youtube.video.videoid.videoIdHooks.add { videoId ->
        app.morphe.extension.youtube.patches.ReturnYouTubeDislikePatch.newVideoLoaded(videoId)
    }

    io.github.a092devs.nexfusion.morphe.youtube.video.videoid.hookPlayerResponseVideoId { videoId, isShortAndOpeningOrPlaying ->
        app.morphe.extension.youtube.patches.ReturnYouTubeDislikePatch.preloadVideoId(videoId, isShortAndOpeningOrPlaying)
    }

    io.github.a092devs.nexfusion.morphe.shared.misc.textcomponent.hookSpannableStringOverride { ctx, orig ->
        app.morphe.extension.youtube.patches.ReturnYouTubeDislikePatch.onLithoTextLoaded(ctx, orig)
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
                    param.args[0] = app.morphe.extension.youtube.patches.ReturnYouTubeDislikePatch
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
                    app.morphe.extension.youtube.patches.ReturnYouTubeDislikePatch.onYogaSetWidth(ptr, width)
                }
            }
        )
    }

    runCatching {
        val endpointSuper = dexkit.getClassDirectOrNull("ryd_dislike_super") {
            findMethod {
                matcher {
                    name = "<init>"
                    returnType = "void"
                    usingStrings("like/dislike")
                }
            }.first().declaredClass!!.superClass!!
        }
        val endpointSuperName = endpointSuper?.className
        if (endpointSuperName != null) {
            val serviceField = dexkit.getFieldDirectOrNull("ryd_endpoint_service_field") {
                findMethod {
                    matcher {
                        paramCount = 0
                        usingStrings("serviceName")
                    }
                }.first().usingFields.last {
                    it.field.typeName == "java.lang.String"
                }.field
            }
            val parserMethod = dexkit.getMethodDirectOrNull("ryd_like_endpoint_parser") {
                findMethod {
                    matcher {
                        declaredClass = endpointSuperName
                        returnType = "void"
                        usingStrings("")
                    }
                }.first()
            }
            val serviceFieldName = serviceField?.name
            if (parserMethod != null && serviceFieldName != null) {
                val parserCls = classLoader.loadClass(parserMethod.className)
                val targetMethod = parserCls.declaredMethods.firstOrNull { it.name == parserMethod.name }
                if (targetMethod != null) {
                    io.github.a092devs.nexfusion.compat.XposedBridge.hookMethod(
                        targetMethod,
                        object : io.github.a092devs.nexfusion.compat.XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val serviceName = runCatching {
                                    io.github.a092devs.nexfusion.compat.XposedHelpers
                                        .getObjectField(param.thisObject, serviceFieldName) as? String
                                }.getOrNull() ?: return
                                if (!serviceName.contains("like/")) return
                                val videoId = param.thisObject.javaClass.declaredFields
                                    .asSequence()
                                    .mapNotNull { f ->
                                        runCatching {
                                            f.isAccessible = true
                                            val v = f.get(param.thisObject) ?: return@mapNotNull null
                                            v.javaClass.declaredFields.firstOrNull { sf ->
                                                sf.type == String::class.java && run {
                                                    sf.isAccessible = true
                                                    val sv = sf.get(v) as? String
                                                    sv != null && sv.length == 11 && sv.all { ch -> ch.isLetterOrDigit() || ch == '-' || ch == '_' }
                                                }
                                            }?.get(v) as? String
                                        }.getOrNull()
                                    }.firstOrNull() ?: return
                                app.morphe.extension.youtube.patches.ReturnYouTubeDislikePatch.sendVote(serviceName, videoId)
                            }
                        }
                    )
                }
            }
        }
    }
}

val PlaylistSearch = patch(
    name = "Playlist search",
    description = "Adds a button to playlist pages to search the videos of that playlist.",
    use = false,
) {
}

val ReplaySlower = patch(
    name = "Replay slower button",
    description = "Adds options to rewind and play part of the video at a slower speed, with a player button or by swiping left on the video.",
    use = false,
) {
}

val ReversePlaylistAutoplay = patch(
    name = "Reverse playlist autoplay",
    description = "Adds an option to autoplay playlists in reverse order, from the last video to the first.",
    use = false,
) {
}

val YouTubePatches = arrayOf(
    ExtensionHook,
    BackgroundPlayback,
    SanitizeSharingLinks,
    HideAds,
    CopyVideoLinkButtonPatch,
    Downloads,
    ChannelSearch,
    HideShortsComponents,
    DisableShortsResumingOnStartup,
    SkipLiveShorts,
    NavigationBar,
    SwipeControls,
    seekbarPatch,
    onTheGoPatch,
    disableChapterSkipDoubleTapPatch,
    VideoQuality,
    HideLayoutComponents,
    HideVideoActionButtons,
    PlaybackSpeed,
    AutoCaptions,
    CaptionsInPip,
    EnableDebugging,
    ForceOriginalAudio,
    DisableVideoCodecs,
    deArrowPatch,
    SponsorBlock,
    VideoDelay,
    BypassImageRegionRestrictionsPatch,
    AmbientMode,
    HideStatusBar,
    Miniplayer,
    PipSeekButtons,
    DisablePlaylistAutoplay,
    ReversePlaylistAutoplay,
    HoldGesture,
    ReturnYouTubeUsername,
    ReturnYouTubeDislike,
    PlaylistSearch,
    ReplaySlower,
    CheckRecycleBitmapMediaSession,
    changeStartPagePatch,
    FixVerticalScroll,
    AutoplayControl,
    PlaylistPanelButton,
    FullscreenPlaylistPanel,
    CommandRouterHook,
    VideoEffects,
    DisableDRCAudio,
    GlassMenus,
    Theme,
    // make sure settingsHook at end to build preferences
    SettingsHook,
    FeatureOverride
)
