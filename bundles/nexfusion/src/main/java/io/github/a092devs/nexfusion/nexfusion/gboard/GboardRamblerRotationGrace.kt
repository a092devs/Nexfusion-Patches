package io.github.a092devs.nexfusion.nexfusion.gboard

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps a Rambler voice dictation session alive across a screen rotation.
 * Ported from Akshay Kadam's PixelBoard (GPL-3.0).
 *
 * A rotation makes Gboard rebuild its input method view and data, deactivating the
 * Agentic Dictation extension and reactivating it ~80ms later. Gboard reads
 * `agentic_dictation_deactivation_delay_ms` to decide whether to stop speech recognition.
 * By default, this is 0 so closing the keyboard stops Rambler instantly without battery drain;
 * during a rotation transition window, we grant a 1500ms grace period so dictation persists.
 */
object GboardRamblerRotationGrace {

    private const val TAG = "NexFusion"
    private const val LOG_PREFIX = "[rambler-rotation-grace] "

    const val DEACTIVATION_GRACE_MS = 1500L
    const val DISPLAY_CHANGE_WINDOW_MS = 2000L

    private const val UNKNOWN = Int.MIN_VALUE
    private const val MAX_INFO_LOGS = 30
    private val infoLogCount = AtomicInteger(0)

    private val lock = Any()
    @Volatile
    private var listening = false
    private var orientation = UNKNOWN
    private var widthDp = UNKNOWN
    private var heightDp = UNKNOWN

    @Volatile
    private var graceUntil = 0L

    @Volatile
    var clockOverride: (() -> Long)? = null

    /** Returns the deactivation delay to hand Gboard: 1500ms mid-rotation, else 0ms. */
    fun deactivationDelayMs(): Long {
        if (graceUntil == 0L) return 0L
        val delay = deactivationDelayMs(nowUptimeMs())
        if (delay > 0L) {
            logInfo("display changed, keeping Rambler alive for $delay ms")
        }
        return delay
    }

    fun deactivationDelayMs(nowUptimeMs: Long): Long {
        return if (nowUptimeMs <= graceUntil) DEACTIVATION_GRACE_MS else 0L
    }

    fun ensureListening(context: Context?) {
        if (listening || context == null) return
        synchronized(lock) {
            if (listening) return
            try {
                val appCtx = context.applicationContext ?: context
                appCtx.registerComponentCallbacks(object : ComponentCallbacks {
                    override fun onConfigurationChanged(newConfig: Configuration) {
                        record(newConfig)
                    }

                    override fun onLowMemory() {}
                })
                record(appCtx.resources.configuration)
            } catch (t: Throwable) {
                logInfo("could not track display changes: $t")
            }
            listening = true
        }
    }

    private fun record(configuration: Configuration) {
        onDisplayConfiguration(
            configuration.orientation,
            configuration.screenWidthDp,
            configuration.screenHeightDp,
            nowUptimeMs()
        )
    }

    private fun nowUptimeMs(): Long {
        return clockOverride?.invoke() ?: SystemClock.uptimeMillis()
    }

    fun onDisplayConfiguration(newOrientation: Int, newWidthDp: Int, newHeightDp: Int, nowUptimeMs: Long) {
        synchronized(lock) {
            val known = orientation != UNKNOWN
            val changed = newOrientation != orientation || newWidthDp != widthDp || newHeightDp != heightDp
            orientation = newOrientation
            widthDp = newWidthDp
            heightDp = newHeightDp
            if (known && changed) {
                graceUntil = nowUptimeMs + DISPLAY_CHANGE_WINDOW_MS
            }
        }
    }

    private fun logInfo(message: String) {
        runCatching {
            if (infoLogCount.getAndIncrement() < MAX_INFO_LOGS) {
                Log.i(TAG, LOG_PREFIX + message)
            }
        }
    }

    fun resetForTests() {
        synchronized(lock) {
            clockOverride = null
            infoLogCount.set(0)
            listening = false
            orientation = UNKNOWN
            widthDp = UNKNOWN
            heightDp = UNKNOWN
            graceUntil = 0L
        }
    }
}
