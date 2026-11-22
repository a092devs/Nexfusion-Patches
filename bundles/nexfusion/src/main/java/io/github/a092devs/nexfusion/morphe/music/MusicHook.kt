package io.github.a092devs.nexfusion.morphe.music

import io.github.a092devs.nexfusion.morphe.music.ad.HideAds
import io.github.a092devs.nexfusion.morphe.music.audio.exclusiveaudio.EnableExclusiveAudioPlayback
import io.github.a092devs.nexfusion.morphe.music.layout.captions.DisableAutoCaptions
import io.github.a092devs.nexfusion.morphe.music.layout.glassmenus.GlassMenus
import io.github.a092devs.nexfusion.morphe.music.layout.upgradebutton.HideUpgradeButton
import io.github.a092devs.nexfusion.morphe.music.misc.album.PlayAlbumSongs
import io.github.a092devs.nexfusion.morphe.music.misc.audio.drc.DisableDRCAudio
import io.github.a092devs.nexfusion.morphe.music.misc.backgroundplayback.BackgroundPlayback
import io.github.a092devs.nexfusion.morphe.music.misc.debugging.EnableDebugging
import io.github.a092devs.nexfusion.morphe.music.misc.privacy.SanitizeSharingLinks
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.music.misc.sharesheet.OpenSystemShareSheet
import io.github.a092devs.nexfusion.morphe.music.video.effects.VideoEffects
import io.github.a092devs.nexfusion.morphe.music.video.quality.RememberVideoQuality
import io.github.a092devs.nexfusion.morphe.shared.misc.CheckRecycleBitmapMediaSession

val YTMusicPatches = arrayOf(
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
    VideoEffects,
    DisableDRCAudio,
    PlayAlbumSongs,
    GlassMenus,
    SettingsHook
)
