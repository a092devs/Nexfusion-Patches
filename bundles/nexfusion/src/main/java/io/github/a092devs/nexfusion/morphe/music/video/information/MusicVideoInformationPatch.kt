package io.github.a092devs.nexfusion.morphe.music.video.information

import app.morphe.extension.music.shared.VideoInformation
import app.morphe.extension.shared.Logger
import io.github.a092devs.nexfusion.PatchExecutor
import io.github.a092devs.nexfusion.getStaticObjectField
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.StringComparisonType
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.string
import io.github.a092devs.nexfusion.patch
import java.lang.reflect.Method

val onMusicCreateHook = mutableListOf<() -> Unit>()
val musicVideoTimeHooks = mutableListOf<(Long) -> Unit>()
val musicVideoIdHooks = mutableListOf<(String) -> Unit>()

internal object VideoEndFingerprint : Fingerprint(
    parameters = listOf("J", "L"),
    filters = listOf(
        string("currentPositionMs."),
        string(";seekTimeUs.")
    )
)

internal object VideoIdFingerprint : Fingerprint(
    filters = listOf(
        string("Null initialPlayabilityStatus")
    ),
    custom = {
        paramCount(2..3)
    }
)

internal object PlayerControllerSetTimeReferenceFingerprint : Fingerprint(
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_DIRECT_RANGE,
            name = "<init>",
            parameters = listOf("J", "J", "J", "J", "J", "J", "J", "Z", "Ljava/lang/String;")
        ),
        string("Media progress reported outside media playback: ", comparison = StringComparisonType.CONTAINS)
    )
)

val MusicVideoInformation = patch(
    name = "<MusicVideoInformation>",
    description = "Hooks video ID, video time, and seekTo for YouTube Music."
) {
    onMusicCreateHook.add { VideoInformation.initialize() }
    musicVideoTimeHooks.add { VideoInformation.setVideoTime(it) }

    try {
        val seekMethod = VideoEndFingerprint.method
        val playerClass = seekMethod.declaringClass
        val seekSourceClass = seekMethod.parameterTypes[1]
        val seekSourceUnknown = seekSourceClass.getEnumConstants()?.firstOrNull()
            ?: seekSourceClass.getStaticObjectField("a")

        playerClass.declaredConstructors.forEach { ctor ->
            ctor.hookMethod {
                after { param ->
                    VideoInformation.setPlayer(
                        VideoInformation.PlaybackController { videoTime ->
                            seekMethod.invoke(param.thisObject, videoTime, seekSourceUnknown) as Boolean
                        }
                    )
                    onMusicCreateHook.forEach { hook ->
                        runCatching { hook() }
                    }
                }
            }
        }
    } catch (t: Throwable) {
        Logger.printException({ "MusicVideoInformation: VideoEndFingerprint failed" }, t)
    }

    try {
        PlayerControllerSetTimeReferenceFingerprint.hookMethod {
            before { param ->
                val longs = param.args.filterIsInstance<Long>()
                if (longs.size >= 2) {
                    val time = longs[1]
                    musicVideoTimeHooks.forEach { hook ->
                        runCatching { hook(time) }
                    }
                }
            }
        }
    } catch (t: Throwable) {
        Logger.printException({ "MusicVideoInformation: PlayerControllerSetTimeReferenceFingerprint failed" }, t)
    }

    try {
        var videoLengthMethod: Method? = null

        VideoIdFingerprint.hookMethod {
            before { param ->
                val id = param.args.getOrNull(1) as? String ?: return@before
                val model = param.args.firstOrNull()
                val length = if (model != null) {
                    val lenMethod = videoLengthMethod ?: model.javaClass.declaredMethods.firstOrNull {
                        it.returnType == Long::class.javaPrimitiveType && it.parameterCount == 0
                    }?.apply { isAccessible = true }.also { videoLengthMethod = it }
                    runCatching { lenMethod?.invoke(model) as? Long }.getOrNull() ?: 0L
                } else 0L
                VideoInformation.setVideoId(id)
                VideoInformation.setVideoLength(length)
                musicVideoIdHooks.forEach { hook ->
                    runCatching { hook(id) }
                }
            }
        }
    } catch (t: Throwable) {
        Logger.printException({ "MusicVideoInformation: VideoIdFingerprint failed" }, t)
    }
}
