/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/2964
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.a092devs.nexfusion.morphe.youtube.interaction.channelsearch

import android.widget.ImageView
import app.morphe.extension.shared.innertube.GuideResponseOuterClass.NavigationEndpoint
import app.morphe.extension.youtube.patches.ChannelSearchPatch as ChannelSearchExtension
import app.morphe.extension.youtube.patches.ToolBarPatch
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.scopedHook
import org.luckypray.dexkit.wrap.DexMethod

val ChannelSearch = patch(
    name = "Channel search",
    description = "Adds an option to search inside the channel that is currently open instead of searching all of YouTube.",
    use = false,
) {
    PreferenceScreen.GENERAL.addPreferences(
        SwitchPreference("morphe_channel_search", summary = true)
    )

    // 1. Toolbar button hook
    ToolBarButtonFingerprint.hookMethod(scopedHook(::resolveToolbarIconEnumMethod.member) {
        after {
            ToolBarPatch.setToolbarIconEnum(it.result as? Enum<*>)
        }
    })

    val setImageDrawableMethod = DexMethod("Landroid/widget/ImageView;->setImageDrawable(Landroid/graphics/drawable/Drawable;)V").toMember()
    ToolBarButtonFingerprint.hookMethod(scopedHook(setImageDrawableMethod) {
        before {
            val imageView = it.thisObject as? ImageView
            if (imageView != null) {
                ToolBarPatch.setToolbarImageView(imageView)
            }
        }
    })

    // 2. BrowseFragment endpoint setter & onCreateView hooks
    runCatching {
        ::browseFragmentSetterMethod.hookMethod {
            before {
                val endpoint = it.args[0]
                if (endpoint != null) {
                    try {
                        val bytes = endpoint.javaClass.getMethod("toByteArray").invoke(endpoint) as ByteArray
                        val navEndpoint = NavigationEndpoint.parseFrom(bytes)
                        val browseId = if (navEndpoint.hasBrowseEndpoint()) navEndpoint.browseEndpoint.browseId else null
                        ChannelSearchExtension.setBrowseId(browseId)
                    } catch (_: Exception) {
                        ChannelSearchExtension.setBrowseId(null)
                    }
                } else {
                    ChannelSearchExtension.setBrowseId(null)
                }
            }
        }
    }

    BrowseFragmentOnCreateViewFingerprint.hookMethod {
        val endpointField = ::browseFragmentEndpointField.field
        after {
            try {
                val endpoint = endpointField.get(it.thisObject)
                if (endpoint != null) {
                    val bytes = endpoint.javaClass.getMethod("toByteArray").invoke(endpoint) as ByteArray
                    val navEndpoint = NavigationEndpoint.parseFrom(bytes)
                    val browseId = if (navEndpoint.hasBrowseEndpoint()) navEndpoint.browseEndpoint.browseId else null
                    ChannelSearchExtension.setBrowseId(browseId)
                }
            } catch (_: Exception) {}
        }
    }

    // 3. Clear browseId on search feed
    SearchResultsFragmentOnCreateViewFingerprint.hookMethod {
        after {
            ChannelSearchExtension.clearBrowseId()
        }
    }
}

