package io.github.a092devs.nexfusion.morphe.youtube.interaction.seekbar

import android.graphics.Point
import android.view.MotionEvent
import app.morphe.extension.youtube.patches.TapToSeekPatch
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.scopedHook

val enableTapToSeekPatch = patch(
    description = "Adds an option to enable tap to seek on the seekbar of the video player.",
) {
    PreferenceScreen.SEEKBAR.addPreferences(
        SwitchPreference("morphe_tap_to_seek", summary = true),
    )

    // Find the required methods to tap the seekbar.
    val startSeek = ::tapSeekStartMethod.method
    val updateSeek = ::tapSeekUpdateMethod.method
    val pointField = ::tapSeekPointField.field
    val getPoint = pointField.type.getMethod("get")

    TapToSeekFingerprint.hookMethod(scopedHook(::tapSeekTouchDownMethod.method) {
        after {
            if (it.hasThrowable() || it.thisObject !== outerParam.thisObject ||
                (outerParam.args[0] as MotionEvent).action != MotionEvent.ACTION_DOWN ||
                !TapToSeekPatch.tapToSeekEnabled()) return@after
            val point = getPoint(pointField.get(outerParam.thisObject)) as Point
            startSeek(outerParam.thisObject, point.x)
            updateSeek(outerParam.thisObject, point.x)
        }
    })
}
