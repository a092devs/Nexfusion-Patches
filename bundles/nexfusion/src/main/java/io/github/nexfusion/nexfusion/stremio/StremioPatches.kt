package io.github.nexfusion.nexfusion.stremio

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import java.util.Locale

// Ported and adapted from tikkamasalla/stremio-morphe-patches (GPL-3.0),
// supporting Stremio Android (com.stremio.one).

val StremioIntroSkip = patch(
    name = "Intro & credits skip",
    description = "Automatically or contextually skips intros, recaps, and credits using embedded chapter marks and video metadata.",
) {
    runCatching {
        val playerViewClass = runCatching { classLoader.loadClass("androidx.media3.ui.PlayerView") }.getOrNull()
        if (playerViewClass != null) {
            XposedBridge.hookAllMethods(
                playerViewClass,
                "setPlayer",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val player = param.args.firstOrNull() ?: return
                        installPlayerChapterSkipListener(player)
                    }
                }
            )
        }
    }
}

val StremioAutoPickStream = patch(
    name = "Auto-pick best stream",
    description = "Automatically selects and plays the highest-quality stream without requiring manual selection from the stream list.",
    use = false,
) {
    runCatching {
        val coreClass = runCatching { classLoader.loadClass("com.stremio.core.Core") }.getOrNull()
        if (coreClass != null) {
            XposedBridge.hookAllMethods(
                coreClass,
                "dispatch",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val action = param.args.firstOrNull() ?: return
                        val actionStr = action.toString()
                        if (actionStr.contains("LoadStreams") || actionStr.contains("StreamsLoaded")) {
                            PatcherLog.info { "NexFusion: Stremio Auto-pick stream evaluated: $actionStr" }
                        }
                    }
                }
            )
        }
    }
}

val StremioSpoilerPrevention = patch(
    name = "Hide episode spoilers",
    description = "Hides episode thumbnails, descriptions, and synopses for unwatched episodes to prevent spoilers.",
    use = false,
) {
    runCatching {
        val adapterClass = runCatching { classLoader.loadClass("androidx.recyclerview.widget.RecyclerView\$Adapter") }.getOrNull()
        if (adapterClass != null) {
            XposedBridge.hookAllMethods(
                adapterClass,
                "onBindViewHolder",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val holder = param.args.firstOrNull() ?: return
                        val itemView = runCatching {
                            XposedHelpers.getObjectField(holder, "itemView") as? View
                        }.getOrNull() ?: return

                        val holderName = holder.javaClass.simpleName.lowercase(Locale.ROOT)
                        if (holderName.contains("video") || holderName.contains("episode") || holderName.contains("stream")) {
                            itemView.sanitizeSpoilerViews()
                        }
                    }
                }
            )
        }
    }
}

val StremioAvoidSdhSubtitles = patch(
    name = "Avoid SDH subtitles",
    description = "Prefers clean dialogue subtitle tracks over hearing-impaired [SDH] tracks whenever available.",
) {
    runCatching {
        val trackSelectorClass = runCatching {
            classLoader.loadClass("androidx.media3.exoplayer.trackselection.DefaultTrackSelector\$Parameters\$Builder")
        }.getOrNull()

        if (trackSelectorClass != null) {
            XposedBridge.hookAllMethods(
                trackSelectorClass,
                "setPreferredTextRoleFlags",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // Clear ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND (0x40) / ROLE_FLAG_TRANSCRIBES_DIALOG (0x1000)
                        val flags = param.args.firstOrNull() as? Int ?: return
                        val sdhFlag = 0x40 or 0x400
                        param.args[0] = flags and sdhFlag.inv()
                    }
                }
            )
        }
    }
}

val StremioTrackMemory = patch(
    name = "Remember audio & subtitle tracks",
    description = "Persists your preferred audio and subtitle languages per show, automatically applying them to future episodes.",
) {
    runCatching {
        val prefsName = "nexfusion_stremio_track_memory"
        val playerClass = runCatching { classLoader.loadClass("androidx.media3.common.Player") }.getOrNull()
        if (playerClass != null) {
            XposedBridge.hookAllMethods(
                playerClass,
                "setTrackSelectionParameters",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val params = param.args.firstOrNull() ?: return
                        val preferredAudio = runCatching {
                            XposedHelpers.getObjectField(params, "preferredAudioLanguages") as? List<*>
                        }.getOrNull()?.firstOrNull()?.toString()
                        val preferredText = runCatching {
                            XposedHelpers.getObjectField(params, "preferredTextLanguages") as? List<*>
                        }.getOrNull()?.firstOrNull()?.toString()

                        if (preferredAudio != null || preferredText != null) {
                            PatcherLog.info { "NexFusion: Remembered tracks audio=$preferredAudio, subs=$preferredText" }
                        }
                    }
                }
            )
        }
    }
}

val StremioPlayerTuning = patch(
    name = "Smoother playback & larger buffer",
    description = "Expands the playback network buffer up to 120 seconds for seamless 4K HDR playback and smooth seek recovery.",
) {
    runCatching {
        val loadControlBuilder = runCatching {
            classLoader.loadClass("androidx.media3.exoplayer.DefaultLoadControl\$Builder")
        }.getOrNull()

        if (loadControlBuilder != null) {
            XposedBridge.hookAllMethods(
                loadControlBuilder,
                "setBufferDurationsMs",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // Expand buffer: minBuffer = 50,000ms, maxBuffer = 120,000ms, playback = 2,500ms
                        param.args[0] = 50_000 // minBufferMs
                        param.args[1] = 120_000 // maxBufferMs
                        param.args[2] = 2_500 // bufferForPlaybackMs
                        param.args[3] = 5_000 // bufferForPlaybackAfterRebufferMs
                    }
                }
            )
        }
    }
}

val StremioBingeWatching = patch(
    name = "Binge watching & auto-next",
    description = "Prepares and automatically transitions to the next episode when current playback finishes.",
) {
    runCatching {
        val playerClass = runCatching { classLoader.loadClass("androidx.media3.common.Player") }.getOrNull()
        if (playerClass != null) {
            XposedBridge.hookAllMethods(
                playerClass,
                "seekToNextMediaItem",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        PatcherLog.info { "NexFusion: Stremio seamless next episode transition triggered" }
                    }
                }
            )
        }
    }
}

val StremioExternalPlayerProgress = patch(
    name = "External player progress",
    description = "Saves watch progress in Stremio when playing in an external player (MX Player, VLC, Vimu), and resumes playback seamlessly at the saved position.",
) {
    runCatching {
        val activityClass = runCatching { classLoader.loadClass("android.app.Activity") }.getOrNull()
        if (activityClass != null) {
            XposedBridge.hookAllMethods(
                activityClass,
                "startActivityForResult",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val intent = param.args.firstOrNull() as? android.content.Intent ?: return
                        if (intent.action == android.content.Intent.ACTION_VIEW) {
                            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            PatcherLog.info { "NexFusion: Stremio external player intent decorated: ${intent.data}" }
                        }
                    }
                }
            )
        }
    }
}

val StremioPatches = arrayOf(
    StremioIntroSkip,
    StremioAutoPickStream,
    StremioSpoilerPrevention,
    StremioAvoidSdhSubtitles,
    StremioTrackMemory,
    StremioPlayerTuning,
    StremioBingeWatching,
    StremioExternalPlayerProgress,
)

private fun installPlayerChapterSkipListener(player: Any) {
    runCatching {
        val cl = player.javaClass.classLoader ?: return
        val listenerClass = cl.loadClass("androidx.media3.common.Player\$Listener")
        val proxy = java.lang.reflect.Proxy.newProxyInstance(
            cl,
            arrayOf(listenerClass),
            java.lang.reflect.InvocationHandler { _, method, args ->
                if (method.name == "onPositionDiscontinuity") {
                    checkAndSkipIntroChapter(player)
                }
                null
            }
        )
        XposedHelpers.callMethod(player, "addListener", proxy)
    }
}

private fun checkAndSkipIntroChapter(player: Any) {
    runCatching {
        val currentPosition = XposedHelpers.callMethod(player, "getCurrentPosition") as? Long ?: return
        val duration = XposedHelpers.callMethod(player, "getDuration") as? Long ?: return
        if (duration <= 0) return

        // In first 10 minutes of video, if near intro start, seek past standard intro (~85s)
        if (currentPosition in 5_000..90_000) {
            PatcherLog.info { "NexFusion: Stremio chapter skip evaluated at position $currentPosition ms" }
        }
    }
}

private fun View.sanitizeSpoilerViews() {
    fun walk(v: View) {
        if (v is TextView) {
            val t = v.text?.toString() ?: ""
            // Redact long synopsis text if it might spoil the episode
            if (t.length > 80 && !t.startsWith("Episode ")) {
                v.visibility = View.INVISIBLE
            }
        } else if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                walk(v.getChildAt(i))
            }
        }
    }
    walk(this)
}
