package io.github.a092devs.nexfusion.morphe.youtube.layout.player.pip

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.settings.BooleanSetting
import app.morphe.extension.youtube.patches.VideoInformation
import io.github.a092devs.nexfusion.IHookCallback
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.youtube.video.information.VideoInformationPatch
import io.github.a092devs.nexfusion.patch
import org.luckypray.dexkit.wrap.DexMethod

private const val ACTION_SEEK_BACK = "io.github.a092devs.nexfusion.morphe.PIP_SEEK_BACK"
private const val ACTION_SEEK_FORWARD = "io.github.a092devs.nexfusion.morphe.PIP_SEEK_FORWARD"
private const val SEEK_OFFSET_MS = 5_000L
private const val SEEK_BACK_TITLE = "-5 s"
private const val SEEK_FORWARD_TITLE = "+5 s"

/**
 * Adds rewind/forward (±5 seconds) buttons to the Picture-in-picture window.
 *
 * The buttons are injected into the actions of the Picture-in-picture params that YouTube builds.
 * Each action posts a broadcast that is handled in the YouTube process and seeks through the
 * regular [VideoInformation] playback controller, so the buttons keep working while the app is
 * in the background / PiP.
 */
val PipSeekButtons = patch(
    name = "Picture-in-picture seek buttons",
    description = "Adds rewind and forward (±5 seconds) buttons to the Picture-in-picture window.",
) {
    dependsOn(VideoInformationPatch)

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_pip_seek_buttons", summary = true),
    )

    val pipSeekSetting = BooleanSetting("morphe_pip_seek_buttons", true)

    val context = appContext

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (!pipSeekSetting.get()) return

            Logger.printDebug {
                "PipSeekButtons: receiver action=${intent?.action} " +
                        "videoTime=${VideoInformation.getVideoTime()}"
            }

            when (intent?.action) {
                ACTION_SEEK_BACK -> VideoInformation.seekToRelative(-SEEK_OFFSET_MS)
                ACTION_SEEK_FORWARD -> VideoInformation.seekToRelative(SEEK_OFFSET_MS)
            }
        }
    }

    val filter = IntentFilter(ACTION_SEEK_BACK).apply { addAction(ACTION_SEEK_FORWARD) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        @Suppress("UnspecifiedRegisterReceiverFlag")
        context.registerReceiver(receiver, filter)
    }

    fun pendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    val seekBackAction by lazy {
        RemoteAction(
            Icon.createWithResource("android", android.R.drawable.ic_media_rew),
            SEEK_BACK_TITLE,
            SEEK_BACK_TITLE,
            pendingIntent(ACTION_SEEK_BACK, 1),
        )
    }

    val seekForwardAction by lazy {
        RemoteAction(
            Icon.createWithResource("android", android.R.drawable.ic_media_ff),
            SEEK_FORWARD_TITLE,
            SEEK_FORWARD_TITLE,
            pendingIntent(ACTION_SEEK_FORWARD, 2),
        )
    }

    fun mergeSeekActions(actions: List<RemoteAction>): List<RemoteAction> {
        when (actions.size) {
            0 -> return actions
            1 -> return listOf(seekBackAction, seekForwardAction, actions[0])
            else -> {
                val merged = actions.toMutableList()
                merged[0] = seekBackAction
                merged[merged.lastIndex] = seekForwardAction
                return merged
            }
        }
    }

    fun logActions(stage: String, actions: List<RemoteAction>) {
        Logger.printDebug {
            "PipSeekButtons: $stage actions=${actions.size} " +
                    "titles=${actions.joinToString { it.title.toString() }}"
        }
    }

    // Primary hook: replace the outer actions of whatever list YouTube passes to the PiP builder.
    runCatching {
        DexMethod(
            "Landroid/app/PictureInPictureParams\$Builder;->setActions(Ljava/util/List;)" +
                    "Landroid/app/PictureInPictureParams\$Builder;"
        ).hookMethod {
            before { param ->
                if (!pipSeekSetting.get()) return@before

                @Suppress("UNCHECKED_CAST")
                val actions = param.args[0] as? List<RemoteAction> ?: return@before
                logActions("builder.setActions in", actions)
                val merged = mergeSeekActions(actions)
                logActions("builder.setActions out", merged)
                param.args[0] = merged
            }
        }
    }.onFailure { e ->
        Logger.printException({ "PipSeekButtons: failed to hook the PiP actions builder" }, e)
    }

    // Fallback hook: some entry points set the params directly without calling setActions again.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val addToParams: IHookCallback = { param ->
            if (pipSeekSetting.get()) {
                val params = param.args[0] as? PictureInPictureParams
                if (params != null) {
                    logActions("pipParams in", params.actions)
                    val merged = mergeSeekActions(params.actions)
                    if (merged.map { it.title } != params.actions.map { it.title }) {
                        logActions("pipParams out", merged)
                        param.args[0] = PictureInPictureParams.Builder(params)
                            .setActions(merged)
                            .build()
                    }
                }
            }
        }

        runCatching {
            DexMethod(
                "Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V"
            ).hookMethod {
                before(addToParams)
            }

            DexMethod(
                "Landroid/app/Activity;->enterPictureInPictureMode(Landroid/app/PictureInPictureParams;)Z"
            ).hookMethod {
                before(addToParams)
            }
        }.onFailure { e ->
            Logger.printException({ "PipSeekButtons: failed to hook the PiP params entry points" }, e)
        }
    }
}

