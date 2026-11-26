package io.github.a092devs.nexfusion.nexfusion

import io.github.a092devs.nexfusion.AppPatchInfo
import io.github.a092devs.nexfusion.AppTarget
import io.github.a092devs.nexfusion.Patch
import io.github.a092devs.nexfusion.PatchBundle
import io.github.a092devs.nexfusion.morphe.music.YTMusicPatches
import io.github.a092devs.nexfusion.morphe.youtube.YouTubePatches
import io.github.a092devs.nexfusion.patches.ExtensionContextHook
import io.github.a092devs.nexfusion.patches.ExtensionResourceHook
import io.github.a092devs.nexfusion.revanced.googlephotos.GooglePhotosPatches
import io.github.a092devs.nexfusion.nexfusion.cloudflare.CloudflareWarpPatches
import io.github.a092devs.nexfusion.nexfusion.crunchyroll.CrunchyrollPatches
import io.github.a092devs.nexfusion.nexfusion.gboard.GboardPatches
import io.github.a092devs.nexfusion.nexfusion.gmail.GmailPatches
import io.github.a092devs.nexfusion.nexfusion.google.GoogleDiscoverPatches
import io.github.a092devs.nexfusion.nexfusion.instagram.InstagramPatches
import io.github.a092devs.nexfusion.nexfusion.linkedin.LinkedInPatches
import io.github.a092devs.nexfusion.nexfusion.maps.GoogleMapsPatches
import io.github.a092devs.nexfusion.nexfusion.protonvpn.ProtonVpnPatches
import io.github.a092devs.nexfusion.nexfusion.reddit.RedditPatches
import io.github.a092devs.nexfusion.nexfusion.spotify.SpotifyPatches
import io.github.a092devs.nexfusion.nexfusion.twitter.TwitterPatches

/** Entry point of the NexFusion Patches bundle, named in its manifest. */
object NexFusionPatches : PatchBundle {
    private fun morpheApp(
        appName: String,
        packageName: String,
        patches: Array<Patch>,
        targets: List<AppTarget> = emptyList(),
        appIconColor: Int? = null,
    ) = AppPatchInfo(
        appName,
        packageName,
        arrayOf(ExtensionContextHook, ExtensionResourceHook, *patches),
        targets,
        appIconColor
    )

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
            appName = "1.1.1.1",
            packageName = "com.cloudflare.onedotonedotonedotone",
            patches = CloudflareWarpPatches,
            appIconColor = 0xF48120,
        ),
        morpheApp(
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
            appName = "Spotify",
            packageName = "com.spotify.music",
            patches = SpotifyPatches,
            appIconColor = 0x1ED760,
        ),
    )
}
