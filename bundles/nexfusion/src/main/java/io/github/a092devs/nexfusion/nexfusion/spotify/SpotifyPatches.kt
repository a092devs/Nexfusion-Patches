package io.github.a092devs.nexfusion.nexfusion.spotify

import io.github.a092devs.nexfusion.nexfusion.spotify.ads.BlockPopupAds
import io.github.a092devs.nexfusion.nexfusion.spotify.ads.DisableAdBreaks
import io.github.a092devs.nexfusion.nexfusion.spotify.ads.HideAdSections
import io.github.a092devs.nexfusion.nexfusion.spotify.ads.HideAdViews
import io.github.a092devs.nexfusion.nexfusion.spotify.ads.HideContextMenuUpsells
import io.github.a092devs.nexfusion.nexfusion.spotify.ads.HidePlaylistAds
import io.github.a092devs.nexfusion.nexfusion.spotify.ads.HideVideoAds
import io.github.a092devs.nexfusion.nexfusion.spotify.ads.MuteAudioAds
import io.github.a092devs.nexfusion.nexfusion.spotify.layout.HidePremiumTab
import io.github.a092devs.nexfusion.nexfusion.spotify.misc.DiagnoseSpotifyClient
import io.github.a092devs.nexfusion.nexfusion.spotify.misc.FixThirdPartyLaunchersWidgets
import io.github.a092devs.nexfusion.nexfusion.spotify.misc.ShowUnavailableTracks
import io.github.a092devs.nexfusion.nexfusion.spotify.privacy.SanitizeSharingLinks

val SpotifyPatches = arrayOf(
    DisableAdBreaks,
    MuteAudioAds,
    HideAdSections,
    HideAdViews,
    HideContextMenuUpsells,
    BlockPopupAds,
    HidePlaylistAds,
    HideVideoAds,
    HidePremiumTab,
    FixThirdPartyLaunchersWidgets,
    ShowUnavailableTracks,
    SanitizeSharingLinks,
    DiagnoseSpotifyClient,
)
