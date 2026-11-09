package com.andrerinas.openheadunit.phoenix

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Holds audio output while the Phoenix launcher reports that the car's audio path is not ready
 * (broadcast [ACTION_AUDIO_PATH] with boolean [EXTRA_READY]). Video is unaffected. Never having
 * received the broadcast means ready.
 *
 * The mixer keeps running and writes silence while held, so stream timing and acks to the phone
 * are unchanged and nothing is buffered up to burst out on release.
 */
object PhoenixAudioGate {
    const val ACTION_AUDIO_PATH = "com.phoenix.launcher.action.AUDIO_PATH"
    const val EXTRA_READY = "ready"

    @Volatile
    var ready: Boolean = true
        private set

    val held: Boolean get() = !ready

    fun setReady(value: Boolean) {
        if (ready == value) return
        ready = value
        AppLog.i("Phoenix: audio path ${if (value) "READY - audio unmuted" else "NOT READY - audio held"}")
    }

    /** Pure mapping from a broadcast to the new state; null leaves it unchanged. */
    fun readyFrom(action: String?, hasExtra: Boolean, extra: Boolean): Boolean? =
        if (action == ACTION_AUDIO_PATH && hasExtra) extra else null

    /** Runtime registration for the app process (the manifest entry covers a cold process). */
    fun register(context: Context) {
        ContextCompat.registerReceiver(
            context.applicationContext, Receiver(), IntentFilter(ACTION_AUDIO_PATH), ContextCompat.RECEIVER_EXPORTED
        )
    }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            readyFrom(intent.action, intent.hasExtra(EXTRA_READY), intent.getBooleanExtra(EXTRA_READY, true))
                ?.let(::setReady)
        }
    }
}
