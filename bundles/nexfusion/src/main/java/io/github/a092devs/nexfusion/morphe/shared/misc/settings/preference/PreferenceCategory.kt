@file:Suppress("DEPRECATION")

package io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference

import android.content.Context
import android.preference.Preference
import android.preference.PreferenceCategory
import android.preference.PreferenceGroup
import android.preference.PreferenceManager
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.settings.preference.NoTitlePreferenceCategory
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference.Sorting

open class PreferenceCategory(
    key: String? = null,
    titleKey: String? = if (key == null) null else "${key}_title",
    icon: String? = null,
    iconBold: String? = null,
    layout: String? = null,
    sorting: Sorting = Sorting.BY_TITLE,
    tag: Class<out PreferenceGroup> = PreferenceCategory::class.java,
    val preferences: Set<BasePreference>
) : BasePreference(sorting.appendSortType(key), titleKey, null, icon, iconBold, layout, tag) {
    lateinit var children: List<Preference>
    lateinit var preferenceGroup: PreferenceGroup
    override fun build(ctx: Context, prefMgr: PreferenceManager): Preference {
        val group = (super.build(ctx, prefMgr) as? PreferenceGroup) ?: PreferenceCategory(ctx)
        return group.apply {
            preferenceGroup = this
            children = preferences.mapNotNull {
                runCatching { it.build(ctx, prefMgr) }
                    .onFailure { e -> Logger.printDebug { "Failed to build category child ${it.key}: ${e.message}" } }
                    .getOrNull()
            }
        }
    }

    override fun onAttachedToHierarchy() {
        if (!::preferenceGroup.isInitialized) return
        preferenceGroup.apply {
            if (::children.isInitialized) {
                children.forEach { addPreference(it) }
            }
            preferences.forEach { runCatching { it.onAttachedToHierarchy() } }
        }
    }
}

internal fun noTitleUnsortedPreferenceCategory(
    vararg preferences: BasePreference
) = noTitleUnsortedPreferenceCategory(preferences.toSet())

internal fun noTitleUnsortedPreferenceCategory(
    preferences: Set<BasePreference>
) = PreferenceCategory(
    key = null,
    titleKey = null,
    sorting = Sorting.UNSORTED,
    tag = NoTitlePreferenceCategory::class.java,
    preferences = preferences
)

/**
 * Preferences shown in a dialog from the info button of the screen toolbar.
 * Must be a direct child of the screen.
 */
internal fun screenInfoPreferenceCategory(
    key: String,
    preferences: Set<BasePreference>
) = PreferenceCategory(
    key = key,
    titleKey = null,
    sorting = Sorting.UNSORTED,
    preferences = preferences
)


