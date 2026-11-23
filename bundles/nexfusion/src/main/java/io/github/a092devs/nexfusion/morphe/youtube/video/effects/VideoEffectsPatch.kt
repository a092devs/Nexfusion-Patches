package io.github.a092devs.nexfusion.morphe.youtube.video.effects

import app.morphe.extension.shared.patches.VideoEffectsPatch as VideoEffectsExt
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.StringComparisonType
import io.github.a092devs.nexfusion.morphe.literal
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.setExtensionIsPatchIncluded
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.a092devs.nexfusion.morphe.string
import io.github.a092devs.nexfusion.morphe.youtube.insertLiteralOverride
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.VersionCheck
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch

internal object GlVideoRendererFeatureFlagFingerprint : Fingerprint(
    filters = listOf(literal(45727453L))
)

internal object PassthroughShaderDrawFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(definingClass = "Landroid/opengl/GLES20;", name = "glUseProgram"),
        methodCall(definingClass = "Landroid/opengl/GLES20;", name = "glUniformMatrix4fv"),
        methodCall(definingClass = "Landroid/opengl/GLES20;", name = "glDrawArrays"),
        string("null shader input for recomposition")
    )
)

internal object PassthroughShaderConstructorFingerprint : Fingerprint(
    classFingerprint = PassthroughShaderDrawFingerprint,
    name = "<init>",
    filters = listOf(
        string("texCoordVar = (texTransMat * vTexCoord).xy;", StringComparisonType.CONTAINS),
        string("gl_FragColor = texture2D(texture, texCoordVar);", StringComparisonType.CONTAINS)
    )
)

val VideoEffects = patch(
    name = "Video effects",
    description = "Adds shader and post-processing video effects options and controls.",
    use = false,
) {
    dependsOn(VersionCheck)

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

    insertLiteralOverride(45727453L, VideoEffectsExt::useGlVideoRenderer)

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
