package io.github.nexalloy.nexfusion

import io.github.nexalloy.AppPatchInfo
import io.github.nexalloy.AppTarget
import io.github.nexalloy.Patch
import io.github.nexalloy.PatchBundle
import io.github.nexalloy.morphe.music.YTMusicPatches
import io.github.nexalloy.morphe.youtube.YouTubePatches
import io.github.nexalloy.patches.ExtensionContextHook
import io.github.nexalloy.revanced.googlephotos.GooglePhotosPatches
import io.github.nexalloy.nexfusion.crunchyroll.CrunchyrollPatches
import io.github.nexalloy.nexfusion.discord.DiscordPatches
import io.github.nexalloy.nexfusion.facebook.FacebookPatches
import io.github.nexalloy.nexfusion.gboard.GboardPatches
import io.github.nexalloy.nexfusion.gmail.GmailPatches
import io.github.nexalloy.nexfusion.google.GoogleDiscoverPatches
import io.github.nexalloy.nexfusion.instagram.InstagramPatches
import io.github.nexalloy.nexfusion.kick.KickPatches
import io.github.nexalloy.nexfusion.linkedin.LinkedInPatches
import io.github.nexalloy.nexfusion.protonvpn.ProtonVpnPatches
import io.github.nexalloy.nexfusion.reddit.RedditPatches
import io.github.nexalloy.nexfusion.spotify.SpotifyPatches
import io.github.nexalloy.nexfusion.telegram.TelegramPatches
import io.github.nexalloy.nexfusion.twitch.TwitchPatches
import io.github.nexalloy.nexfusion.twitter.TwitterPatches

/** Entry point of the NexFusion Patches bundle, named in its manifest. */
object NexFusionPatches : PatchBundle {
    private fun morpheApp(
        appName: String,
        packageName: String,
        patches: Array<Patch>,
        targets: List<AppTarget> = emptyList(),
        appIconColor: Int? = null,
    ) = AppPatchInfo(appName, packageName, arrayOf(ExtensionContextHook, *patches), targets, appIconColor)

    override val apps = listOf(
        morpheApp("YouTube", "com.google.android.youtube", YouTubePatches, appIconColor = 0xFF0033),
        morpheApp("YT Music", "com.google.android.apps.youtube.music", YTMusicPatches, appIconColor = 0xFF0000),
        morpheApp("Google Photos", "com.google.android.apps.photos", GooglePhotosPatches, appIconColor = 0x4285F4),
        AppPatchInfo(
            appName = "Crunchyroll",
            packageName = "com.crunchyroll.crunchyroid",
            patches = CrunchyrollPatches,
            appIconColor = 0xFF5C00,
        ),
        AppPatchInfo(
            appName = "Discord",
            packageName = "com.discord",
            patches = DiscordPatches,
            appIconColor = 0x5865F2,
        ),
        AppPatchInfo(
            appName = "Facebook",
            packageName = "com.facebook.katana",
            patches = FacebookPatches,
            appIconColor = 0x0866FF,
        ),
        AppPatchInfo(
            appName = "Instagram",
            packageName = "com.instagram.android",
            patches = InstagramPatches,
            appIconColor = 0xE1306C,
        ),
        AppPatchInfo(
            appName = "Gboard",
            packageName = "com.google.android.inputmethod.latin",
            patches = GboardPatches,
            appIconColor = 0x4285F4,
        ),
        AppPatchInfo(
            appName = "Gmail",
            packageName = "com.google.android.gm",
            patches = GmailPatches,
            appIconColor = 0xEA4335,
        ),
        AppPatchInfo(
            appName = "Google (Discover)",
            packageName = "com.google.android.googlequicksearchbox",
            patches = GoogleDiscoverPatches,
            appIconColor = 0x4285F4,
        ),
        AppPatchInfo(
            appName = "LinkedIn",
            packageName = "com.linkedin.android",
            patches = LinkedInPatches,
            appIconColor = 0x0A66C2,
        ),

        AppPatchInfo(
            appName = "Proton VPN",
            packageName = "ch.protonvpn.android",
            patches = ProtonVpnPatches,
            appIconColor = 0x6D4AFF,
        ),
        AppPatchInfo(
            appName = "Reddit",
            packageName = "com.reddit.frontpage",
            patches = RedditPatches,
            appIconColor = 0xFF4500,
        ),
        AppPatchInfo(
            appName = "Spotify",
            packageName = "com.spotify.music",
            patches = SpotifyPatches,
            appIconColor = 0x1ED760,
        ),
        AppPatchInfo(
            appName = "X",
            packageName = "com.twitter.android",
            patches = TwitterPatches,
            appIconColor = 0x000000,
        ),
        AppPatchInfo(
            appName = "Telegram",
            packageName = "org.telegram.messenger",
            patches = TelegramPatches,
            appIconColor = 0x2AABEE,
        ),
        AppPatchInfo(
            appName = "Telegram (Web)",
            packageName = "org.telegram.messenger.web",
            patches = TelegramPatches,
            appIconColor = 0x2AABEE,
        ),
        AppPatchInfo(
            appName = "Kick",
            packageName = "com.kick.mobile",
            patches = KickPatches,
            appIconColor = 0x53FC18,
        ),
        AppPatchInfo(
            appName = "Twitch",
            packageName = "tv.twitch.android.app",
            patches = TwitchPatches,
            appIconColor = 0x9146FF,
        ),
    )
}
