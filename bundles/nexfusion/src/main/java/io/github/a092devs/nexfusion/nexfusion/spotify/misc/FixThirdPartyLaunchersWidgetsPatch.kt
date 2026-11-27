package io.github.a092devs.nexfusion.nexfusion.spotify.misc

import io.github.a092devs.nexfusion.compat.XC_MethodReplacement
import io.github.a092devs.nexfusion.PatcherLog
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.nexfusion.spotify.canBindAppWidgetPermissionMethod

val FixThirdPartyLaunchersWidgets = patch(
    name = "Fix third party launchers widgets",
    description = "Allows the Spotify widgets to be added to third party launchers.",
    use = false,
) {
    // Only the stock launcher has the BIND_APPWIDGET permission Spotify checks for.
    val method = runCatching { ::canBindAppWidgetPermissionMethod.member }.getOrNull()
    if (method == null) {
        PatcherLog.info { "The BIND_APPWIDGET permission check was not found, skipping the widget fix" }
        return@patch
    }
    method.hookMethod(XC_MethodReplacement.returnConstant(true))
}
