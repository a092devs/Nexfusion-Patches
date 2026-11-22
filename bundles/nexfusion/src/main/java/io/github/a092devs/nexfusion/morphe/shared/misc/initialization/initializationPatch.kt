package io.github.a092devs.nexfusion.morphe.shared.misc.initialization

import app.morphe.extension.shared.patches.InitializationPatch
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.scopedHook


internal fun initializationPatch() = patch (
    description = "Prompts to restart the app on first load of a clean install",
) {
    GlobalConfigGroupFingerprint.hookMethod(scopedHook(::handleColdFingerprint.member) {
        InitializationPatch.onGlobalConfigUpdated()
    })
}
