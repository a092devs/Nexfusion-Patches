package io.github.nexfusion.nexfusion.reddit

import io.github.nexfusion.compat.XC_MethodReplacement
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch

// Ported from MorpheApp/morphe-patches (GPL-3.0),
// patches/src/main/kotlin/app/morphe/patches/reddit/misc/guest/StartAsGuestPatch.kt.
val StartAsGuest = patch(
    name = "Start Reddit as guest",
    description = "Skips the forced startup login screen using Reddit's native guest browsing mode.",
) {
    runCatching {
        ::FrontPageApplicationHasFinishedOnboarding.hookMethod(
            XC_MethodReplacement.returnConstant(true),
        )
    }.onFailure {
        PatcherLog.info(it) { "NexFusion: Reddit guest onboarding anchor not found" }
    }
}

