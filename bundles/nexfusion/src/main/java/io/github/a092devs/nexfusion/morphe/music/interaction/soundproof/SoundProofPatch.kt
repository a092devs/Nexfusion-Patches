package io.github.a092devs.nexfusion.morphe.music.interaction.soundproof

import app.morphe.extension.music.patches.soundproof.SoundProofPatch
import app.morphe.extension.music.patches.soundproof.ui.SoundProofAboutPreference
import app.morphe.extension.shared.settings.preference.SeekBarPreference
import io.github.a092devs.nexfusion.morphe.music.misc.mediasession.MediaSessionHook
import io.github.a092devs.nexfusion.morphe.music.misc.mediasession.setMetadataWithSessionHooks
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceCategory
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.screenInfoPreferenceCategory
import io.github.a092devs.nexfusion.patch

val SoundProof = patch(
    name = "SoundProof",
    description = "Adds an option to skip tracks of artists reported as AI in the SoundProof community database.",
    use = false,
) {
    dependsOn(
        SettingsHook,
        MediaSessionHook,
    )

    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_music_soundproof_screen",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_music_soundproof_show_button", summary = true),
                SwitchPreference("morphe_music_soundproof_skip_ai_artists", summary = true),
                SwitchPreference(
                    "morphe_music_soundproof_toast_on_skip",
                    titleKey = "morphe_toast_on_skip_title",
                    summary = true
                ),
                PreferenceCategory(
                    key = "morphe_music_soundproof_threshold",
                    sorting = PreferenceScreenPreference.Sorting.UNSORTED,
                    preferences = setOf(
                        NonInteractivePreference(
                            key = "morphe_music_soundproof_minimum_ai_percent",
                            tag = SeekBarPreference::class.java,
                            selectable = true
                        ),
                        NonInteractivePreference(
                            key = "morphe_music_soundproof_minimum_votes",
                            tag = SeekBarPreference::class.java,
                            selectable = true
                        )
                    )
                ),
                screenInfoPreferenceCategory(
                    key = "morphe_music_soundproof_about_category",
                    preferences = setOf(
                        NonInteractivePreference(
                            key = "morphe_music_soundproof_about",
                            titleKey = null,
                            summaryKey = "morphe_music_soundproof_about_summary",
                            tag = SoundProofAboutPreference::class.java,
                            selectable = true
                        )
                    )
                )
            )
        )
    )

    setMetadataWithSessionHooks.add { session, metadata ->
        SoundProofPatch.onSetMetadata(session, metadata)
    }
}
