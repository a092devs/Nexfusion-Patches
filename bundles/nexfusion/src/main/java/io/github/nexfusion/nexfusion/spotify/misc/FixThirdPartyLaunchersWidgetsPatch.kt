package io.github.nexfusion.nexfusion.spotify.misc

import io.github.nexfusion.compat.XC_MethodReplacement
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.hookMethod
import io.github.nexfusion.patch
import io.github.nexfusion.nexfusion.spotify.canBindAppWidgetPermissionMethod

val FixThirdPartyLaunchersWidgets = patch(
    name = "Fix third party launchers widgets",
    description = "Allows the Spotify widgets to be added to third party launchers.",
) {
    // Only the stock launcher has the BIND_APPWIDGET permission Spotify checks for.
    val method = runCatching { ::canBindAppWidgetPermissionMethod.member }.getOrNull()
    if (method == null) {
        PatcherLog.info { "The BIND_APPWIDGET permission check was not found, skipping the widget fix" }
        return@patch
    }
    method.hookMethod(XC_MethodReplacement.returnConstant(true))
}
