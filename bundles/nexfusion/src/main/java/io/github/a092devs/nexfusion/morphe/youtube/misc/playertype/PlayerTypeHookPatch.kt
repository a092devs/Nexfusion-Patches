package io.github.a092devs.nexfusion.morphe.youtube.misc.playertype

import android.annotation.SuppressLint
import android.view.View
import app.morphe.extension.youtube.patches.PlayerTypeHookPatch
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.VersionCheck
import io.github.a092devs.nexfusion.patch

@SuppressLint("NonUniqueDexKitData")
val PlayerTypeHook = patch(
    name = "PlayerTypeHook",
    description = "Hook to get the current player type and video playback state.",
    use = false,
) {
    dependsOn(VersionCheck)

    ::PlayerTypeFingerprint.hookMethod {
        before { param ->
            PlayerTypeHookPatch.setPlayerType(param.args[0] as Enum<*>)
        }
    }

    ReelWatchPagerFingerprint.hookMethod {
        val field = ::ReelPlayerViewField.field
        after { param ->
            val thiz = param.thisObject
            val view = field.get(thiz) as View
            PlayerTypeHookPatch.onShortsCreate(view)
        }
    }

    ::videoStateFingerprint.hookMethod {
        val field = ::videoStateParameterField.field
        before { param ->
            PlayerTypeHookPatch.setVideoState(field.get(param.args[0]) as Enum<*>)
        }
    }
}
