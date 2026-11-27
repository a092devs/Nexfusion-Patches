package io.github.a092devs.nexfusion.nexfusion.spotify.misc

import io.github.a092devs.nexfusion.compat.XC_MethodHook
import io.github.a092devs.nexfusion.compat.XposedBridge
import io.github.a092devs.nexfusion.compat.XposedHelpers
import io.github.a092devs.nexfusion.PatcherLog
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.nexfusion.spotify.showUnavailableTracksField

/**
 * Forces Spotify's own "Show unplayable songs" setting on.
 *
 * Spotify already ships this setting (`show_unavailable_tracks`); when disabled the app hides
 * tracks that cannot be played (wrong market, removed, …) from collections instead of showing
 * them greyed out. The setting is stored in the server-synced reactive settings state, and the
 * UI reads the boolean field of that state directly.
 *
 * We make sure every freshly built settings state has the field set, which makes the app render
 * unavailable tracks again — at least greyed out.
 */
val ShowUnavailableTracks = patch(
    name = "Show unplayable songs",
    description = "Forces Spotify's own \"Show unplayable songs\" setting on, so tracks that " +
        "cannot be played (wrong market, removed, …) stay visible in collections instead of " +
        "being hidden. Playing them still depends on the account.",
    use = true,
) {
    val field = runCatching { ::showUnavailableTracksField.field }.getOrNull()
    if (field == null) {
        PatcherLog.info { "Spotify: Show unplayable songs anchor not found" }
        return@patch
    }
    var writeFailureReported = false
    XposedBridge.hookAllConstructors(field.declaringClass, object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            runCatching {
                XposedHelpers.setBooleanField(param.thisObject, field.name, true)
            }.onFailure { err ->
                if (!writeFailureReported) {
                    writeFailureReported = true
                    PatcherLog.error(err) {
                        "Show unplayable songs: cannot set ${field.declaringClass.simpleName}.${field.name}"
                    }
                }
            }
        }
    })
    PatcherLog.info {
        "Show unplayable songs: forced ${field.declaringClass.simpleName}.${field.name} = true"
    }
}
