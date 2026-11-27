package io.github.a092devs.nexfusion.morphe.music.interaction.loop

import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import app.morphe.extension.music.patches.LoopVideoPatch
import app.morphe.extension.shared.Logger
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.findFieldDirect
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.music.video.information.MusicVideoInformation
import io.github.a092devs.nexfusion.morphe.music.video.information.musicVideoIdHooks
import io.github.a092devs.nexfusion.morphe.music.video.information.musicVideoTimeHooks
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.string
import io.github.a092devs.nexfusion.patch

private const val TIME_BAR_CLASS =
    "Lcom/google/android/apps/youtube/music/watchpage/MusicPlaybackControlsTimeBar;"

internal object MedialibPlayerSeekToFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("J", "L"),
    filters = listOf(string("MedialibPlayer.seekTo(positionMillis=%d)"))
)

internal object TimeBarSetScrubPositionFingerprint : Fingerprint(
    definingClass = TIME_BAR_CLASS,
    returnType = "V",
    parameters = listOf("F"),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "this", type = "Landroid/graphics/Rect;"),
        methodCall(definingClass = "Ljava/lang/Math;", name = "max")
    )
)

internal object TimeBarDrawFingerprint : Fingerprint(
    definingClass = TIME_BAR_CLASS,
    name = "draw",
    returnType = "V",
    parameters = listOf("Landroid/graphics/Canvas;")
)

private val loopTimeBarRectField = findFieldDirect {
    TimeBarSetScrubPositionFingerprint.instructionMatches.first().instruction.fieldRef!!
}

val LoopVideo = patch(
    name = "A-B loop",
    description = "Adds an option to loop part of a song by long-pressing the seekbar to set the loop start and end.",
    use = false,
) {
    dependsOn(
        SettingsHook,
        MusicVideoInformation,
    )

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_ab_loop", summary = true)
    )

    musicVideoIdHooks.add { LoopVideoPatch.videoIdChanged(it) }
    musicVideoTimeHooks.add { LoopVideoPatch.videoTimeChanged(it) }

    runCatching {
        MedialibPlayerSeekToFingerprint.hookMethod {
            before { param ->
                param.args[0] = LoopVideoPatch.clampSeekPosition(param.args[0] as Long)
            }
        }
    }.onFailure {
        Logger.printException({ "LoopVideo: MedialibPlayerSeekTo hook failed" }, it)
    }

    try {
        val rectField = ::loopTimeBarRectField.field.apply { isAccessible = true }
        val timeBarClassName = TimeBarSetScrubPositionFingerprint.method.declaringClass.name

        TimeBarSetScrubPositionFingerprint.hookMethod {
            before { param ->
                param.args[0] = LoopVideoPatch.clampSeekX(param.args[0] as Float)
            }
        }

        View::class.java.getDeclaredMethod("onTouchEvent", MotionEvent::class.java).hookMethod {
            before { param ->
                if (param.thisObject?.javaClass?.name == timeBarClassName) {
                    LoopVideoPatch.onTouchEvent(param.thisObject as View, param.args[0] as MotionEvent)
                }
            }
        }

        TimeBarDrawFingerprint.hookMethod {
            after { param ->
                val rect = rectField.get(param.thisObject) as? Rect ?: return@after
                LoopVideoPatch.drawLoopMarkers(param.args[0] as Canvas, rect)
            }
        }
    } catch (t: Throwable) {
        Logger.printException({ "LoopVideo: TimeBar hooks failed" }, t)
    }
}
