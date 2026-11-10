package io.github.nexfusion.nexfusion.spotify.layout

import io.github.nexfusion.hookMethod
import io.github.nexfusion.patch
import io.github.nexfusion.nexfusion.extension.spotify.HidePremiumTabPatch
import io.github.nexfusion.nexfusion.spotify.navigationTabSetConstructor

val HidePremiumTab = patch(
    name = "Hide Premium tab",
    description = "Removes the \"Premium\" tab from the bottom navigation bar.",
) {
    ::navigationTabSetConstructor.hookMethod {
        before { param ->
            val args = param.args
            val tabs = HidePremiumTabPatch.filterTabs(args[0], args[1], args[2], args[3], args[4]) ?: return@before
            tabs.copyInto(args)
            args[5] = HidePremiumTabPatch.filterFlags(args[5] as Int)
        }
    }
}
