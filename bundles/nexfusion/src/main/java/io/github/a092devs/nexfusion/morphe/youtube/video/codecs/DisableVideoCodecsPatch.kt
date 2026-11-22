package io.github.a092devs.nexfusion.morphe.youtube.video.codecs

import android.view.Display
import app.morphe.extension.youtube.patches.DisableVideoCodecsPatch
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch
import org.luckypray.dexkit.wrap.DexMethod

val DisableVideoCodecs = patch(
    name = "Disable video codecs",
    description = "Adds options to disable or force HDR, and to disable VP9 codecs.",
) {
    PreferenceScreen.VIDEO.addPreferences(
        SwitchPreference("morphe_disable_hdr_video"),
        SwitchPreference("morphe_force_hdr_video", summary = true),
        ListPreference(
            key = "morphe_video_codec",
            tag = app.morphe.extension.youtube.settings.preference.VideoCodecListPreference::class.java
        )
    )

    DexMethod($$"Landroid/view/Display$HdrCapabilities;->getSupportedHdrTypes()[I").hookMethod {
        val guard = ThreadLocal<Boolean>()
        after {
            if (guard.get() == true) {
                return@after
            }

            guard.set(true)
            try {
                it.result =
                    DisableVideoCodecsPatch.overrideSupportedHdrTypes(it.thisObject as Display.HdrCapabilities)
            } finally {
                guard.remove()
            }
        }
    }

    Vp9CapabilityFingerprint.hookMethod {
        before {
            if (!DisableVideoCodecsPatch.allowVP9()) {
                it.result = false
            }
        }
    }
}