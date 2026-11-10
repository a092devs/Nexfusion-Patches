package io.github.nexfusion.nexfusion.spotify.misc

import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import io.github.nexfusion.nexfusion.extension.spotify.DiagnoseClientPatch

val DiagnoseSpotifyClient = patch(
    name = "Diagnose Spotify client",
    description = "Logs signing-certificate queries and integrity-request outcomes without changing them. " +
        "Does not log authentication tokens or account data. Experimental diagnostic.",
    use = false,
) {
    runCatching {
        DiagnoseClientPatch.onStartup(appContext)
        PatcherLog.info { "Spotify: Diagnostics initialized" }
    }.onFailure {
        PatcherLog.error(it) { "Spotify: Diagnostics initialization failed" }
    }
}
