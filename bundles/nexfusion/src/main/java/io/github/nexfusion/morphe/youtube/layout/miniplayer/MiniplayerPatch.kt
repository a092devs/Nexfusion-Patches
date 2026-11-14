package io.github.nexfusion.morphe.youtube.layout.miniplayer

import android.graphics.Rect
import android.view.MotionEvent
import app.morphe.extension.youtube.patches.MiniplayerPatch as ExtensionMiniplayerPatch
import io.github.nexfusion.morphe.shared.misc.settings.preference.ListPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.youtube.insertLiteralOverride
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexfusion.morphe.youtube.shared.seekbarOnDrawFingerprint
import io.github.nexfusion.patch

/**
 * Adds options to change the in-app minimized player.
 *
 * Ported from Morphe v1.45.0 miniplayerPatch:
 * Modern miniplayer feature flags, horizontal drag / offscreen behavior and button-listener hooks.
 */
val Miniplayer = patch(
    name = "Miniplayer",
    description = "Adds options to change the in-app minimized player.",
) {
    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_miniplayer_screen",
            sorting = Sorting.UNSORTED,
            preferences = setOf(
                ListPreference(
                    "morphe_miniplayer_type",
                    entriesKey = "morphe_miniplayer_type_21_29_entries",
                    entryValuesKey = "morphe_miniplayer_type_21_29_entry_values",
                ),
                SwitchPreference("morphe_miniplayer_hide_title", summary = true),
                SwitchPreference("morphe_miniplayer_glass", summary = true),
                SwitchPreference("morphe_miniplayer_disable_rounded_corners", summary = true),
                SwitchPreference("morphe_miniplayer_disable_drag_and_drop", summary = true),
                SwitchPreference("morphe_miniplayer_disable_horizontal_drag", summary = true),
                SwitchPreference("morphe_miniplayer_disable_horizontal_reposition", summary = true),
            ),
        ),
    )

    // << Modern miniplayer feature flag overrides (via the standard flag getter). >>
    insertLiteralOverride(45622882L) { ExtensionMiniplayerPatch.getModernFeatureFlagsActiveOverride(it) }
    insertLiteralOverride(45628823L) { ExtensionMiniplayerPatch.getMiniplayerDoubleTapAction(it) }
    insertLiteralOverride(45657015L) { ExtensionMiniplayerPatch.getMiniplayerOnCloseHandler(it) }
    insertLiteralOverride(45630429L) { ExtensionMiniplayerPatch.getModernMiniplayerOverride(it) }
    insertLiteralOverride(45628752L) { ExtensionMiniplayerPatch.getMiniplayerDragAndDrop(it) }
    insertLiteralOverride(45652224L) { ExtensionMiniplayerPatch.getRoundedCorners(it) }

    // << Horizontal drag and offscreen handling. >>
    runCatching {
        MiniplayerOffscreenRectValidatorFingerprint.hookMethod {
            before { param ->
                if (ExtensionMiniplayerPatch.getHorizontalDrag()) param.result = 0
            }
        }
    }
    runCatching {
        MiniplayerOffscreenHandlerFingerprint.hookMethod {
            before { param ->
                if (ExtensionMiniplayerPatch.getHorizontalDrag()) param.result = null
            }
        }
    }
    runCatching {
        NextGenWatchLayoutOnInterceptTouchEventFingerprint.hookMethod {
            before { param ->
                (param.args.firstOrNull() as? MotionEvent)
                    ?.let { ExtensionMiniplayerPatch.enableOffScreenMiniplayerButtonPressed(it) }
            }
        }
    }
    runCatching {
        MiniplayerHorizontalRepositionFingerprint.hookMethod {
            before { param ->
                val current = param.args.firstOrNull() as? Rect
                val previous = runCatching {
                    ::miniplayerPreviousRectField.field.get(param.thisObject) as? Rect
                }.getOrNull()
                param.args[0] = ExtensionMiniplayerPatch.blockOffscreenMiniplayerHorizontalReposition(current, previous)
            }
        }
    }

    // Glass minimal miniplayer: cut seekbar progress to the straight bottom edge of the glass.
    runCatching {
        ::seekbarOnDrawFingerprint.hookMethod {
            before { param ->
                val view = param.thisObject as? android.view.View
                val canvas = param.args.firstOrNull() as? android.graphics.Canvas
                if (view != null && canvas != null) {
                    app.morphe.extension.youtube.patches.MinimalMiniplayerPatch.clipSeekbar(view, canvas)
                }
            }
        }
    }
}

