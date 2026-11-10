package io.github.nexfusion.nexfusion.spotify

import io.github.nexfusion.nexfusion.spotify.ads.BlockPopupAds
import io.github.nexfusion.nexfusion.spotify.ads.DisableAdBreaks
import io.github.nexfusion.nexfusion.spotify.ads.HideAdSections
import io.github.nexfusion.nexfusion.spotify.ads.HideAdViews
import io.github.nexfusion.nexfusion.spotify.ads.HideContextMenuUpsells
import io.github.nexfusion.nexfusion.spotify.ads.HidePlaylistAds
import io.github.nexfusion.nexfusion.spotify.ads.HideVideoAds
import io.github.nexfusion.nexfusion.spotify.ads.MuteAudioAds
import io.github.nexfusion.nexfusion.spotify.layout.HidePremiumTab
import io.github.nexfusion.nexfusion.spotify.misc.DiagnoseSpotifyClient
import io.github.nexfusion.nexfusion.spotify.misc.FixThirdPartyLaunchersWidgets
import io.github.nexfusion.nexfusion.spotify.misc.ShowUnavailableTracks
import io.github.nexfusion.nexfusion.spotify.privacy.SanitizeSharingLinks

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
