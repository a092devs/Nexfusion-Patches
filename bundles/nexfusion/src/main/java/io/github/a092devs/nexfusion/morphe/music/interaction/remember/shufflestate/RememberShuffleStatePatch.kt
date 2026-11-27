package io.github.a092devs.nexfusion.morphe.music.interaction.remember.shufflestate

import app.morphe.extension.music.patches.RememberShuffleStatePatch
import app.morphe.extension.shared.Logger
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.literal
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.music.video.information.MusicVideoInformation
import io.github.a092devs.nexfusion.morphe.music.video.information.musicVideoIdHooks
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.string
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.scopedHook

internal object ShuffleOnClickFingerprint : Fingerprint(
    name = "onClick",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(literal(45468L))
)

internal object ShuffleEnumFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        string("SHUFFLE_OFF"),
        string("SHUFFLE_ALL"),
        string("SHUFFLE_DISABLED")
    )
)

internal object ShuffleEnumValuesFingerprint : Fingerprint(
    classFingerprint = ShuffleEnumFingerprint,
    name = "values",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf()
)

val RememberShuffleState = patch(
    name = "Remember shuffle state",
    description = "Adds an option to remember the shuffle state when playing a new track or playlist.",
    use = false,
) {
    dependsOn(
        SettingsHook,
        MusicVideoInformation,
    )

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_remember_shuffle_state")
    )

    try {
        val enumClass = ShuffleEnumValuesFingerprint.method.declaringClass
        val onClickMethod = ShuffleOnClickFingerprint.method
        val stateField = onClickMethod.declaringClass.declaredFields
            .firstOrNull { f ->
                f.type.declaredMethods.any { it.returnType == enumClass && it.parameterCount == 0 }
            }?.apply { isAccessible = true }

        ShuffleOnClickFingerprint.hookMethod {
            after { param ->
                val target = stateField?.get(param.thisObject) ?: return@after
                val state = target.javaClass.declaredMethods
                    .firstOrNull { it.returnType == enumClass && it.parameterCount == 0 }
                    ?.apply { isAccessible = true }
                    ?.invoke(target) as? Enum<*>
                if (state != null) {
                    RememberShuffleStatePatch.saveShuffleState(state)
                }
            }
        }
    } catch (t: Throwable) {
        Logger.printException({ "RememberShuffleState: ShuffleOnClick hook failed" }, t)
    }

    musicVideoIdHooks.add { videoId ->
        RememberShuffleStatePatch.applySavedShuffleState(videoId)
    }
}
