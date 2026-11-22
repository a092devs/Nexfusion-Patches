package io.github.a092devs.nexfusion.morphe.music.video.effects

import app.morphe.extension.shared.patches.VideoEffectsPatch as VideoEffectsExt
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.setExtensionIsPatchIncluded
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.youtube.video.effects.GlVideoRendererFeatureFlagFingerprint
import io.github.a092devs.nexfusion.morphe.youtube.video.effects.PassthroughShaderConstructorFingerprint
import io.github.a092devs.nexfusion.morphe.youtube.video.effects.PassthroughShaderDrawFingerprint
import io.github.a092devs.nexfusion.patch

val VideoEffects = patch(
    name = "Video effects",
    description = "Adds shader and post-processing video effects options and controls.",
) {
    PreferenceScreen.VIDEO.addPreferences(
        ListPreference(
            key = "morphe_video_effect",
            tag = app.morphe.extension.shared.settings.preference.VideoEffectListPreference::class.java,
            entriesKey = null,
            entryValuesKey = null
        ),
        NonInteractivePreference(
            key = "morphe_video_effects_custom",
            tag = app.morphe.extension.shared.settings.preference.VideoEffectCustomEffectsPreference::class.java,
            selectable = true
        )
    )

    setExtensionIsPatchIncluded(VideoEffectsExt::class.java)

    runCatching {
        PassthroughShaderConstructorFingerprint.hookMethod {
            before { param ->
                val args = param.args
                if (args.isNotEmpty()) {
                    for (i in args.indices) {
                        val s = args[i] as? String ?: continue
                        if (s.contains("gl_FragColor")) {
                            args[i] = VideoEffectsExt.getFragmentShader(s)
                        } else if (s.contains("texCoordVar")) {
                            args[i] = VideoEffectsExt.getVertexShader(s)
                        }
                    }
                }
            }
        }
    }

    runCatching {
        PassthroughShaderDrawFingerprint.hookMethod {
            before { param ->
                val programId = param.args.firstOrNull() as? Int ?: return@before
                VideoEffectsExt.useProgram(programId)
            }
        }
    }
}
