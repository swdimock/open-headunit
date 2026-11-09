package com.andrerinas.openheadunit.phoenix

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.aap.AapService
import com.andrerinas.openheadunit.connection.CommManager
import com.andrerinas.openheadunit.connection.ConnectionPriorityPolicy
import com.andrerinas.openheadunit.main.OnboardingActivity
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phoenix head-unit launch contract. The Phoenix launcher starts the main activity with
 * `phoenix.transport` = `tcp` (plus `phoenix.host` / `phoenix.port`) or `usb`, and optionally
 * `phoenix.inset_bottom` (px kept free at the bottom of the screen for its climate bar).
 *
 * TCP reuses the same path as the upstream `headunit://connect?ip=` deep link
 * ([CommManager.connect] with a user tier), adds a configurable port, and retries every
 * [RETRY_INTERVAL_MS] after each failed attempt while the main screen is visible and no session is up.
 */
object PhoenixLaunch {
    const val EXTRA_TRANSPORT = "phoenix.transport"
    const val EXTRA_HOST = "phoenix.host"
    const val EXTRA_PORT = "phoenix.port"
    const val EXTRA_INSET_BOTTOM = "phoenix.inset_bottom"

    const val DEFAULT_PORT = 5277
    const val RETRY_INTERVAL_MS = 5_000L

    private const val PREFS = "phoenix"
    private const val KEY_ACTIVE = "active"

    sealed interface Request {
        data class Tcp(val host: String, val port: Int) : Request
        object Usb : Request
    }

    /** Pure parse of the launch extras; null when the intent is not a Phoenix launch. */
    fun parse(transport: String?, host: String?, port: Any?): Request? = when (transport?.trim()?.lowercase()) {
        "tcp" -> {
            val h = host?.trim().orEmpty()
            if (h.isEmpty()) null else Request.Tcp(h, parsePort(port))
        }
        "usb" -> Request.Usb
        else -> null
    }

    /** `am start --ei` gives an Int, `--es` a String; anything unusable falls back to 5277. */
    fun parsePort(port: Any?): Int {
        val value = when (port) {
            is Int -> port
            is Long -> port.toInt()
            is String -> port.trim().toIntOrNull()
            else -> null
        }
        return value?.takeIf { it in 1..65535 } ?: DEFAULT_PORT
    }

    fun parse(extras: Bundle?): Request? {
        if (extras == null) return null
        @Suppress("DEPRECATION")
        return parse(extras.getString(EXTRA_TRANSPORT), extras.getString(EXTRA_HOST), extras.get(EXTRA_PORT))
    }

    /** True once the app has been started by the Phoenix launcher (enables the home button). */
    fun isActive(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)

    private var retryJob: Job? = null
    private var statusView: TextView? = null

    /** Called from the main activity's launch-intent handler. Returns true if it was a Phoenix launch. */
    fun handle(activity: ComponentActivity, intent: Intent?): Boolean {
        val request = parse(intent?.extras) ?: return false
        AppLog.i("Phoenix: launch request $request")
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ACTIVE, true).apply()

        val settings = Settings(activity)
        // Launched by the head unit's own launcher: the first-run wizard would only get in the way.
        if (settings.onboardingVersion < OnboardingActivity.CURRENT_ONBOARDING_VERSION) {
            settings.onboardingVersion = OnboardingActivity.CURRENT_ONBOARDING_VERSION
        }
        intent?.extras?.let { extras ->
            if (extras.containsKey(EXTRA_INSET_BOTTOM)) {
                val inset = parseInset(extras.get(EXTRA_INSET_BOTTOM))
                if (inset != null && inset != settings.insetBottom) settings.insetBottom = inset
            }
        }
        PhoenixHomeButton.attach(activity)

        retryJob?.cancel()
        retryJob = null
        when (request) {
            is Request.Usb -> {
                removeStatus()
                ContextCompat.startForegroundService(activity, Intent(activity, AapService::class.java).apply {
                    action = AapService.ACTION_CHECK_USB
                    putExtra(AapService.EXTRA_USER_REQUESTED, true)
                })
            }
            is Request.Tcp -> retryJob = startTcpLoop(activity, request)
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun parseInset(value: Any?): Int? = when (value) {
        is Int -> value
        is String -> value.trim().toIntOrNull()
        else -> null
    }?.takeIf { it in 0..2000 }

    private fun startTcpLoop(activity: ComponentActivity, request: Request.Tcp): Job =
        activity.lifecycleScope.launch {
            val commManager = App.provide(activity).commManager
            var attempt = 0
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try { while (true) {
                    val state = commManager.connectionState.value
                    if (TcpRetryPolicy.shouldAttempt(state.phase())) {
                        attempt++
                        showStatus(activity, waitingText(request, attempt))
                        ContextCompat.startForegroundService(activity, Intent(activity, AapService::class.java).apply {
                            action = AapService.ACTION_CONNECT_SOCKET
                        })
                        withContext(Dispatchers.IO) {
                            commManager.connect(request.host, request.port, ConnectionPriorityPolicy.Tier.USER)
                        }
                        val after = commManager.connectionState.value
                        if (TcpRetryPolicy.shouldAttempt(after.phase())) {
                            val reason = (after as? CommManager.ConnectionState.Error)?.message ?: "no answer"
                            AppLog.i("Phoenix: TCP ${request.host}:${request.port} attempt $attempt failed: $reason")
                        }
                    } else {
                        removeStatus()
                    }
                    delay(RETRY_INTERVAL_MS)
                } } finally {
                    removeStatus()
                }
            }
        }

    private fun waitingText(request: Request.Tcp, attempt: Int) =
        "Android Auto: waiting for phone at ${request.host}:${request.port} (attempt $attempt, retry every ${RETRY_INTERVAL_MS / 1000} s)\n" +
            "Phone: Android Auto > Start head unit server, then on the PC: adb -s <phone> forward tcp:${request.port} tcp:5277"

    private fun CommManager.ConnectionState.phase(): TcpRetryPolicy.Phase = when (this) {
        is CommManager.ConnectionState.Disconnected -> TcpRetryPolicy.Phase.IDLE
        is CommManager.ConnectionState.Error -> TcpRetryPolicy.Phase.FAILED
        else -> TcpRetryPolicy.Phase.BUSY
    }

    private fun showStatus(activity: Activity, text: String) {
        val view = statusView?.takeIf { it.context === activity && it.parent != null } ?: run {
            removeStatus()
            val content = activity.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout ?: return
            TextView(activity).apply {
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 20f
                gravity = Gravity.CENTER
                setPadding(32, 20, 32, 20)
                setBackgroundColor(0xCC202020.toInt())
                content.addView(this, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP
                ).apply { topMargin = 96 })
                statusView = this
            }
        }
        view.text = text
    }

    private fun removeStatus() {
        statusView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        statusView = null
    }
}

/** Pure retry decision for the Phoenix TCP loop. */
object TcpRetryPolicy {
    enum class Phase { IDLE, FAILED, BUSY }

    /** Retry only when nothing is in flight: connecting, handshaking or a live session are left alone. */
    fun shouldAttempt(phase: Phase): Boolean = phase != Phase.BUSY
}
