package io.github.a092devs.nexfusion.morphe.youtube.layout.hide.infocards

import io.github.a092devs.nexfusion.compat.XC_MethodReplacement
import io.github.a092devs.nexfusion.PatcherLog
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.VersionCheck
import io.github.a092devs.nexfusion.patch

/**
 * Hides info cards that creators add to videos. Ported from Morphe dev branch.
 *
 * Supports YouTube 21.41.525.
 */
val hideInfoCardsPatch = patch(
    name = "Hide info cards",
    description = "Adds an option to hide info cards that creators add in the video player.",
) {
    dependsOn(VersionCheck)

    runCatching {
        ::hideInfoCardsMethod.hookMethod(object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: io.github.a092devs.nexfusion.compat.XC_MethodHook.MethodHookParam): Any = false
        })
        PatcherLog.info { "NexFusion YouTube: Hide info cards installed" }
    }.onFailure { PatcherLog.info(it) { "NexFusion YouTube: Hide info cards skipped" } }
}

