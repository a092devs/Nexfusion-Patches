package io.github.a092devs.nexfusion.morphe.music.misc.mediasession

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import app.morphe.extension.shared.Logger
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.patch

val setMetadataHooks = mutableListOf<(MediaMetadata) -> Unit>()
val setMetadataWithSessionHooks = mutableListOf<(MediaSession, MediaMetadata) -> Unit>()
val setPlaybackStateHooks = mutableListOf<(PlaybackState) -> Unit>()

val MediaSessionHook = patch(
    name = "<MediaSessionHook>",
    description = "Hooks the metadata and the playback state that YouTube Music gives to its media session."
) {
    val inSetMetadata = ThreadLocal.withInitial { false }
    val inSetPlaybackState = ThreadLocal.withInitial { false }

    runCatching {
        MediaSession::class.java.getDeclaredMethod("setMetadata", MediaMetadata::class.java).hookMethod {
            before { param ->
                if (inSetMetadata.get() == true) return@before
                val session = param.thisObject as? MediaSession ?: return@before
                val metadata = param.args[0] as? MediaMetadata ?: return@before
                inSetMetadata.set(true)
                try {
                    setMetadataHooks.forEach { hook -> runCatching { hook(metadata) } }
                    setMetadataWithSessionHooks.forEach { hook -> runCatching { hook(session, metadata) } }
                } finally {
                    inSetMetadata.set(false)
                }
            }
        }
    }.onFailure {
        Logger.printException({ "MediaSessionHook: setMetadata failed" }, it)
    }

    runCatching {
        MediaSession::class.java.getDeclaredMethod("setPlaybackState", PlaybackState::class.java).hookMethod {
            before { param ->
                if (inSetPlaybackState.get() == true) return@before
                val state = param.args[0] as? PlaybackState ?: return@before
                inSetPlaybackState.set(true)
                try {
                    setPlaybackStateHooks.forEach { hook -> runCatching { hook(state) } }
                } finally {
                    inSetPlaybackState.set(false)
                }
            }
        }
    }.onFailure {
        Logger.printException({ "MediaSessionHook: setPlaybackState failed" }, it)
    }
}
