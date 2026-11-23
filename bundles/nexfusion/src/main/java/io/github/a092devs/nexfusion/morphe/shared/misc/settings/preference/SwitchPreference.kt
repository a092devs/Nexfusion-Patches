@file:Suppress("DEPRECATION", "DiscouragedApi")

package io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference

import android.content.Context
import android.preference.Preference
import android.preference.PreferenceManager
import app.morphe.extension.shared.settings.preference.BooleanSettingPreference

@Suppress("MemberVisibilityCanBePrivate")
class SwitchPreference(
    key: String? = null,
    titleKey: String = "${key}_title",
    summary: Boolean = false,
    summaryKey: String? = null,
    tag: Class<out Preference> = BooleanSettingPreference::class.java,
    icon: String? = null,
    iconBold: String? = null,
    layout: String? = null,
) : BasePreference(key, titleKey, summaryKey ?: if (summary) "${key}_summary" else null, icon, iconBold, layout, tag) {
    override fun build(ctx: Context, prefMgr: PreferenceManager): Preference {
        val pref = if (tag == BooleanSettingPreference::class.java) {
            BooleanSettingPreference(ctx)
        } else {
            super.build(ctx, prefMgr)
        }
        return pref.apply {
            applyBaseAttrs(this)
        }
    }
}
