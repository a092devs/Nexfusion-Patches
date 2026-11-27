package io.github.a092devs.nexfusion.nexfusion.spotify.ads

import io.github.a092devs.nexfusion.PatcherLog
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.nexfusion.extension.spotify.DisableAdBreaksPatch
import io.github.a092devs.nexfusion.nexfusion.spotify.coroutineClientBaseConstructor

val DisableAdBreaks = patch(
    name = "Disable ad breaks",
    description = "Disables the ad slots that play ads between songs. Experimental.",
    use = true,
) {
    runCatching {
        ::coroutineClientBaseConstructor.hookMethod {
            before { param ->
                val transport = param.args.getOrNull(0) ?: return@before
                DisableAdBreaksPatch.onTransportCreated(transport)
            }
        }
        PatcherLog.info { "Spotify: Disable ad breaks hook registered" }
    }.onFailure {
        PatcherLog.error(it) { "Spotify: Disable ad breaks hook failed" }
    }
}
