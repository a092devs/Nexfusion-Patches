@file:Suppress("DEPRECATION", "DiscouragedApi")

package io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference

import android.content.Context
import android.content.res.Resources
import android.preference.Preference
import android.preference.PreferenceManager
import android.util.AttributeSet
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.shared.Utils
import io.github.a092devs.nexfusion.patches.BuildConfig
import io.github.a092devs.nexfusion.patches.R
import io.github.a092devs.nexfusion.new

@Suppress("MemberVisibilityCanBePrivate")
abstract class BasePreference(
    val key: String? = null,
    val titleKey: String? = "${key}_title",
    val summaryKey: String? = "${key}_summary",
    val icon: String? = null,
    val iconBold: String? = null,
    val layout: String? = null,
    val tag: Class<out Preference>
) {
    fun trySetString(
        key: String?,
        resources: Resources = Utils.getContext().resources,
        pkg: String = BuildConfig.APPLICATION_ID,
        setString: (str: String) -> Unit
    ) {
        if (key == null) return
        val cleanKey = key.removePrefix("@string/")
        val str = runCatching { ResourceUtils.getString(cleanKey) }.getOrNull()
        if (!str.isNullOrEmpty() && str != cleanKey) {
            setString(str)
            return
        }
        trySetRes(cleanKey, "string", resources, pkg) { res, id ->
            runCatching { setString(res.getString(id)) }
        }
    }

    fun trySetRes(
        key: String?,
        type: String,
        resources: Resources = Utils.getContext().resources,
        pkg: String = BuildConfig.APPLICATION_ID,
        setRes: (res: Resources, id: Int) -> Unit,
    ) {
        if (key == null) return
        val cleanKey = key.removePrefix("@$type/").removePrefix("@drawable/").removePrefix("@layout/").removePrefix("@string/")
        val idFromUtils = when (type) {
            "drawable" -> runCatching { ResourceUtils.getDrawableIdentifier(cleanKey) }.getOrDefault(0)
            "layout" -> runCatching { ResourceUtils.getLayoutIdentifier(cleanKey) }.getOrDefault(0)
            "string" -> runCatching { ResourceUtils.getStringIdentifier(cleanKey) }.getOrDefault(0)
            else -> 0
        }
        if (idFromUtils != 0) {
            setRes(resources, idFromUtils)
            return
        }
        when (val id = resources.getIdentifier(cleanKey, type, pkg)) {
            0 -> Logger.printDebug { "$cleanKey not found." }
            else -> setRes(resources, id)
        }
    }

    fun applyBaseAttrs(preference: Preference) {
        preference.also { pref ->
            key?.let { pref.key = key }
            trySetString(titleKey) { pref.title = it }
            trySetString(summaryKey) { pref.summary = it }

            val iconUsed = if (Utils.appIsUsingBoldIcons()) iconBold else icon
            trySetRes(iconUsed, "drawable") { res, id ->
                runCatching {
                    pref.icon = res.getDrawable(id, res.newTheme().apply {
                        applyStyle(R.style.MainTheme, true)
                    })
                }
            }
            layout?.let {
                val cleanLayout = it.removePrefix("@layout/")
                val layoutId = ResourceUtils.getLayoutIdentifier(cleanLayout)
                if (layoutId != 0) {
                    pref.layoutResource = layoutId
                }
            }
        }
    }

    open fun build(ctx: Context, prefMgr: PreferenceManager): Preference {
        Logger.printDebug { "build $key" }
        val pref = runCatching {
            tag.new(
                arrayOf(Context::class.java, AttributeSet::class.java),
                ctx, null
            )
        }.recoverCatching {
            tag.new(
                arrayOf(Context::class.java),
                ctx
            )
        }.recoverCatching {
            tag.getDeclaredConstructor().newInstance()
        }.getOrNull() ?: Preference(ctx)
        return (pref as Preference).apply {
            applyBaseAttrs(this)
        }
    }

    open fun onAttachedToHierarchy() {

    }
}