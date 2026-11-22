package io.github.a092devs.nexfusion.nexfusion.spotify.misc

import io.github.a092devs.nexfusion.PatcherLog
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.nexfusion.extension.spotify.DiagnoseClientPatch

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
