package io.github.nexfusion.morphe.youtube

import android.app.Activity
import app.morphe.extension.shared.Utils
import io.github.libxposed.api.XposedInterface
import io.github.nexfusion.patches.ExtensionResourceHook
import io.github.nexfusion.addModuleAssets
import io.github.nexfusion.atLast
import io.github.nexfusion.injectHostClassLoaderToSelf
import io.github.nexfusion.hookExtensionClassLoading
import io.github.nexfusion.morphe.shared.misc.debugging.experimentalIndexedBooleanFeatureFlagFingerprint
import io.github.nexfusion.morphe.youtube.misc.playservice.is_21_42_or_greater
import io.github.nexfusion.morphe.shared.misc.CheckRecycleBitmapMediaSession
import io.github.nexfusion.morphe.shared.misc.debugging.experimentalBooleanFeatureFlagFingerprint
import io.github.nexfusion.morphe.youtube.ad.HideAds
import io.github.nexfusion.morphe.youtube.interaction.channelsearch.ChannelSearch
import io.github.nexfusion.morphe.youtube.interaction.copyvideolink.CopyVideoLinkButtonPatch
import io.github.nexfusion.morphe.youtube.interaction.downloads.Downloads
import io.github.nexfusion.morphe.youtube.interaction.doubletap.disableChapterSkipDoubleTapPatch
import io.github.nexfusion.morphe.youtube.interaction.seekbar.customSeekbarThumbPatch
import io.github.nexfusion.morphe.youtube.interaction.seekbar.seekbarPatch
import io.github.nexfusion.morphe.youtube.interaction.shorts.SkipLiveShorts
import io.github.nexfusion.morphe.youtube.interaction.swipecontrols.SwipeControls
import io.github.nexfusion.morphe.youtube.layout.buttons.action.HideVideoActionButtons
import io.github.nexfusion.morphe.youtube.layout.buttons.navigation.NavigationBar
import io.github.nexfusion.morphe.youtube.layout.captions.AutoCaptions
import io.github.nexfusion.morphe.youtube.layout.captions.CaptionsInPip
import io.github.nexfusion.morphe.youtube.layout.dearrow.deArrowPatch
import io.github.nexfusion.morphe.youtube.layout.sponsorblock.SponsorBlock
import io.github.nexfusion.morphe.youtube.layout.hide.ambientmode.AmbientMode
import io.github.nexfusion.morphe.youtube.layout.hide.general.HideLayoutComponents
import io.github.nexfusion.morphe.youtube.layout.hide.shorts.HideShortsComponents
import io.github.nexfusion.morphe.youtube.layout.statusbar.HideStatusBar
import io.github.nexfusion.morphe.youtube.layout.miniplayer.Miniplayer
import io.github.nexfusion.morphe.youtube.layout.player.pip.PipSeekButtons
import io.github.nexfusion.morphe.youtube.layout.playlistautoplay.DisablePlaylistAutoplay
import io.github.nexfusion.morphe.youtube.layout.shortsnoresume.DisableShortsResumingOnStartup
import io.github.nexfusion.morphe.youtube.layout.startpage.changeStartPagePatch
import io.github.nexfusion.morphe.youtube.layout.thumbnails.BypassImageRegionRestrictionsPatch
import io.github.nexfusion.morphe.youtube.misc.backgroundplayback.BackgroundPlayback
import io.github.nexfusion.morphe.youtube.misc.debugging.EnableDebugging
import io.github.nexfusion.morphe.youtube.misc.privacy.SanitizeSharingLinks
import io.github.nexfusion.morphe.youtube.misc.settings.SettingsHook
import io.github.nexfusion.morphe.youtube.misc.verticalscroll.FixVerticalScroll
import io.github.nexfusion.morphe.youtube.shared.YOUTUBE_MAIN_ACTIVITY_CLASS_TYPE
import io.github.nexfusion.morphe.youtube.shared.mainActivityClass
import io.github.nexfusion.morphe.youtube.video.audio.ForceOriginalAudio
import io.github.nexfusion.morphe.youtube.video.codecs.DisableVideoCodecs
import io.github.nexfusion.morphe.youtube.video.delay.VideoDelay
import io.github.nexfusion.morphe.youtube.video.quality.VideoQuality
import io.github.nexfusion.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexfusion.morphe.youtube.video.speed.PlaybackSpeed
import io.github.nexfusion.patch
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
            de.robv.android.xposed.XposedHelpers.findAndHookMethod(
                targetClass, "onCreate", android.os.Bundle::class.java,
                object : de.robv.android.xposed.XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val mainActivity = param.thisObject as Activity
                        mainActivity.addModuleAssets()
                        Utils.setContext(mainActivity)
                    }
                }
            )
        }
    }

    ExtensionResourceHook.run(this)
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
            after {
                val args = it.args ?: return@after
                if (args.size < 2) return@after
                val id = (args[args.size - 2] as? Long) ?: return@after
                val orig = (it.result as? Boolean) ?: return@after
                val match = overrides[id] ?: return@after
                it.result = match(orig)
            }
        }
    }
    if (is_21_42_or_greater) {
        runCatching {
            ::experimentalIndexedBooleanFeatureFlagFingerprint.hookMethod {
                priority = XposedInterface.PRIORITY_DEFAULT - 1
                after {
                    val args = it.args ?: return@after
                    if (args.size < 2) return@after
                    val id = (args[args.size - 2] as? Long) ?: return@after
                    val orig = (it.result as? Boolean) ?: return@after
                    val match = overrides[id] ?: return@after
                    it.result = match(orig)
                }
            }
        }
    }
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
    customSeekbarThumbPatch,
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
    CheckRecycleBitmapMediaSession,
    changeStartPagePatch,
    FixVerticalScroll,
    // make sure settingsHook at end to build preferences
    SettingsHook,
    FeatureOverride
)