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
import io.github.a092devs.nexfusion.patch

val LoopVideo = patch(
    name = "A-B loop",
    description = "Adds an option to loop part of a song by long-pressing the seekbar to set loop start and end points.",
    use = false,
) {}

val PauseWhenMuted = patch(
    name = "Pause when muted",
    description = "Adds an option to pause playback automatically when volume is lowered to zero.",
    use = false,
) {}

val PlaylistTrackTap = patch(
    name = "Playlist track tap",
    description = "Adds options to customize playback behavior when tapping individual tracks in playlists.",
    use = false,
) {}

val QueueLikeFilter = patch(
    name = "Queue like filter",
    description = "Adds an option to automatically filter disliked tracks from the upcoming playback queue.",
    use = false,
) {}

val RememberRepeatState = patch(
    name = "Remember repeat state",
    description = "Preserves repeat playback preferences across app restarts.",
    use = false,
) {}

val RememberShuffleState = patch(
    name = "Remember shuffle state",
    description = "Preserves shuffle playback preferences across app restarts.",
    use = false,
) {}

val RemoveViewerDiscretionDialog = patch(
    name = "Remove viewer discretion dialog",
    description = "Suppresses content advisory and viewer discretion warnings when playing age-restricted songs.",
    use = false,
) {}

val DisableDislikeRedirection = patch(
    name = "Disable dislike redirection",
    description = "Prevents skipping to the next track when tapping the dislike button.",
    use = false,
) {}

val ForceOriginalAudio = patch(
    name = "Force original audio",
    description = "Adds an option to force high-fidelity original audio streams for music videos.",
    use = false,
) {}

val SkipSilence = patch(
    name = "Skip silence",
    description = "Adds an option to detect and skip silent gaps between songs.",
    use = false,
) {}

val PlaybackSpeed = patch(
    name = "Playback speed",
    description = "Adds options to customize and fine-tune playback speed for songs and music videos.",
    use = false,
) {}

val ThirdPartyLyrics = patch(
    name = "Third-party lyrics",
    description = "Adds support for rich, multi-provider synchronized lyrics, translation, romanization, and custom styling.",
    use = false,
) {}

val SoundProof = patch(
    name = "SoundProof",
    description = "Adds SoundProof AI blocker to detect and mute AI-generated tracks.",
    use = false,
) {}

val Scrobbling = patch(
    name = "Scrobbling",
    description = "Adds scrobbler integration to sync played tracks with Last.fm and ListenBrainz profiles.",
    use = false,
) {}

val AppleMusicArtwork = patch(
    name = "Apple Music artwork",
    description = "Replaces standard album artwork with animated high-resolution Apple Music artwork.",
    use = false,
) {}

val SonosCast = patch(
    name = "Sonos cast",
    description = "Adds direct discovery and streaming to local Sonos speakers and devices.",
    use = false,
) {}

val ReturnYouTubeDislike = patch(
    name = "Return YouTube Dislike",
    description = "Displays public dislike counts on music videos and tracks.",
    use = false,
) {}

val DisableAudioDucking = patch(
    name = "Disable audio ducking",
    description = "Prevents YouTube Music from lowering audio volume when navigation or system notifications trigger.",
    use = false,
) {}

val Downloads = patch(
    name = "Downloads",
    description = "Enables download action buttons and offline audio caching.",
    use = false,
) {}

val AndroidAuto = patch(
    name = "Android Auto",
    description = "Enables YouTube Music integration and browsing in Android Auto head units.",
    use = false,
) {}

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
