package io.github.a092devs.nexfusion.morphe.shared.misc.debugging

import app.morphe.extension.shared.patches.EnableDebuggingPatch
import io.github.a092devs.nexfusion.PatchExecutor
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.BasePreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.BasePreferenceScreen
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.atLast

fun enableDebuggingPatch(
    block: PatchExecutor.() -> Unit = {},
    hookIndexedBooleanFeatureFlag: PatchExecutor.() -> Boolean = { false },
    hookStringFeatureFlag: PatchExecutor.() -> Boolean,
    hookLongFeatureFlag: PatchExecutor.() -> Boolean,
    hookDoubleFeatureFlag: PatchExecutor.() -> Boolean,
    preferenceScreen: BasePreferenceScreen.Screen,
    additionalDebugPreferences: List<BasePreference> = emptyList()
) = patch(
    name = "Enable debugging",
    description = "Adds options for debugging and exporting Morphe logs to the clipboard.",
) {
    block()

    val preferences = mutableSetOf<BasePreference>(
        SwitchPreference("morphe_debug"),
    )

    preferences.addAll(additionalDebugPreferences)

    preferences.addAll(
        listOf(
            SwitchPreference("morphe_debug_stacktrace", summary = true),
            SwitchPreference("morphe_debug_toast_on_error"),
            NonInteractivePreference(
                "morphe_debug_export_logs",
                tag = app.morphe.extension.shared.settings.preference.ExportLogToClipboardPreference::class.java,
                selectable = true
            ),
            NonInteractivePreference(
                "morphe_debug_feature_flags_manager",
                tag = app.morphe.extension.shared.settings.preference.FeatureFlagsManagerPreference::class.java,
                selectable = true
            )
        )
    )

    preferenceScreen.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_debug_screen",
            sorting = Sorting.UNSORTED,
            preferences = preferences,
        )
    )

    // Hook the methods that look up if a feature flag is active.
    ::experimentalBooleanFeatureFlagFingerprint.memberOrNull?.hookMethod {
        after {
            val result = it.result as? Boolean ?: return@after
            val args = it.args ?: return@after
            val flag = (args.getOrNull(args.size - 2) as? Long) ?: return@after
            it.result = EnableDebuggingPatch.isBooleanFeatureFlagEnabled(result, flag)
        }
    }

    if (hookIndexedBooleanFeatureFlag()) {
        ::experimentalIndexedBooleanFeatureFlagFingerprint.memberOrNull?.hookMethod {
            after {
                val result = it.result as? Boolean ?: return@after
                val args = it.args ?: return@after
                val flag = (args.getOrNull(args.size - 2) as? Long) ?: return@after
                it.result = EnableDebuggingPatch.isBooleanFeatureFlagEnabled(result, flag)
            }
        }
    }

    if (hookDoubleFeatureFlag()) {
        ::experimentalDoubleFeatureFlagFingerprint.memberOrNull?.hookMethod {
            after {
                val result = it.result as? Double ?: return@after
                val args = it.args ?: return@after
                val flag = (args.getOrNull(args.size - 2) as? Long) ?: return@after
                val def = (args.getOrNull(args.size - 1) as? Double) ?: return@after
                it.result = EnableDebuggingPatch.isDoubleFeatureFlagEnabled(result, flag, def)
            }
        }
    }

    if (hookLongFeatureFlag()) {
        ::experimentalLongFeatureFlagFingerprint.memberOrNull?.hookMethod {
            after {
                val result = it.result as? Long ?: return@after
                val args = it.args ?: return@after
                val flag = (args.getOrNull(args.size - 2) as? Long) ?: return@after
                val def = (args.getOrNull(args.size - 1) as? Long) ?: return@after
                it.result = EnableDebuggingPatch.isLongFeatureFlagEnabled(result, flag, def)
            }
        }
    }

    if (hookStringFeatureFlag()) {
        ::experimentalStringFeatureFlagFingerprint.memberOrNull?.hookMethod {
            after {
                val result = it.result as? String ?: return@after
                val args = it.args ?: return@after
                val flag = (args.getOrNull(args.size - 2) as? Long) ?: return@after
                val def = (args.getOrNull(args.size - 1) as? String) ?: return@after
                it.result = EnableDebuggingPatch.isStringFeatureFlagEnabled(result, flag, def)
            }
        }
    }

    // There exists other experimental accessor methods for byte[]
    // and wrappers for obfuscated classes, but currently none of those are hooked.
}
