package io.github.nexfusion.morphe.music

import io.github.nexfusion.patches.ExtensionResourceHook
import io.github.nexfusion.morphe.music.ad.HideAds
import io.github.nexfusion.morphe.music.audio.exclusiveaudio.EnableExclusiveAudioPlayback
import io.github.nexfusion.morphe.music.layout.captions.DisableAutoCaptions
import io.github.nexfusion.morphe.music.layout.upgradebutton.HideUpgradeButton
import io.github.nexfusion.morphe.music.misc.backgroundplayback.BackgroundPlayback
import io.github.nexfusion.morphe.music.misc.debugging.EnableDebugging
import io.github.nexfusion.morphe.music.misc.privacy.SanitizeSharingLinks
import io.github.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.nexfusion.morphe.music.misc.sharesheet.OpenSystemShareSheet
import io.github.nexfusion.morphe.music.video.quality.RememberVideoQuality
import io.github.nexfusion.morphe.shared.misc.CheckRecycleBitmapMediaSession

val YTMusicPatches = arrayOf(
    ExtensionResourceHook,
    BackgroundPlayback,
    HideUpgradeButton,
    HideAds,
    EnableExclusiveAudioPlayback,
    CheckRecycleBitmapMediaSession,
    EnableDebugging,
    SanitizeSharingLinks,
    OpenSystemShareSheet,
    RememberVideoQuality,
    DisableAutoCaptions,
    SettingsHook
)