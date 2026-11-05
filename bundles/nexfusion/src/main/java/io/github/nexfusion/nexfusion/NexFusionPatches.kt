package io.github.nexfusion.nexfusion

import io.github.nexfusion.AppPatchInfo
import io.github.nexfusion.AppTarget
import io.github.nexfusion.Patch
import io.github.nexfusion.PatchBundle
import io.github.nexfusion.morphe.music.YTMusicPatches
import io.github.nexfusion.morphe.youtube.YouTubePatches
import io.github.nexfusion.patches.ExtensionContextHook
import io.github.nexfusion.revanced.googlephotos.GooglePhotosPatches
import io.github.nexfusion.nexfusion.crunchyroll.CrunchyrollPatches
import io.github.nexfusion.nexfusion.discord.DiscordPatches
import io.github.nexfusion.nexfusion.facebook.FacebookPatches
import io.github.nexfusion.nexfusion.gboard.GboardPatches
import io.github.nexfusion.nexfusion.gmail.GmailPatches
import io.github.nexfusion.nexfusion.google.GoogleDiscoverPatches
import io.github.nexfusion.nexfusion.instagram.InstagramPatches
import io.github.nexfusion.nexfusion.kick.KickPatches
import io.github.nexfusion.nexfusion.linkedin.LinkedInPatches
import io.github.nexfusion.nexfusion.maps.GoogleMapsPatches
import io.github.nexfusion.nexfusion.mxplayer.MxPlayerPatches
import io.github.nexfusion.nexfusion.mxplayer.MxPlayerProPatches
import io.github.nexfusion.nexfusion.protonvpn.ProtonVpnPatches
import io.github.nexfusion.nexfusion.reddit.RedditPatches
import io.github.nexfusion.nexfusion.telegram.TelegramPatches
import io.github.nexfusion.nexfusion.twitch.TwitchPatches
import io.github.nexfusion.nexfusion.twitter.TwitterPatches

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
            appName = "Facebook (Wakizashi)",
            packageName = "com.facebook.wakizashi",
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
            appName = "Google Maps",
            packageName = "com.google.android.apps.maps",
            patches = GoogleMapsPatches,
            appIconColor = 0x34A853,
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
        AppPatchInfo(
            appName = "MX Player",
            packageName = "com.mxtech.videoplayer.ad",
            patches = MxPlayerPatches,
            appIconColor = 0x0078D7,
        ),
        AppPatchInfo(
            appName = "MX Player Pro",
            packageName = "com.mxtech.videoplayer.pro",
            patches = MxPlayerProPatches,
            appIconColor = 0x0078D7,
        ),
    )
}
