package com.andrerinas.openheadunit.phoenix

import android.content.Context
import com.andrerinas.openheadunit.connection.projection.LoopbackBindPolicy

/**
 * A Phoenix TCP launch to a loopback host reaches a REMOTE phone through a tunnel (emulator:
 * `adb reverse tcp:5277 tcp:5277` to the PC, `adb forward` on to the phone's head unit server).
 *
 * Upstream classifies every loopback peer as Self Mode, this device projecting to itself
 * (`SocketProjectionConnection.isLoopbackPeer`). For a Self Mode session the service discovery
 * response leaves out the media and speech sinks, so the phone keeps that audio and plays it
 * itself, and the media session, reconnect and disconnect-notice logic also treat it as Self Mode.
 * This remembers the endpoint the Phoenix launcher declared, so a session on it is classified as
 * an ordinary wireless one. Persisted so a reconnect after process death keeps the classification;
 * cleared by a non-loopback or USB Phoenix launch. Self Mode on any other endpoint is unaffected.
 */
object PhoenixTunnel {
    private const val PREFS = "phoenix"
    private const val KEY_ENDPOINT = "loopback_tunnel"

    @Volatile private var cached: String? = null
    @Volatile private var loaded = false

    /** Pure: the tunnel endpoint a launch [request] declares, or null if it declares none. */
    fun endpointFor(request: PhoenixLaunch.Request): String? {
        val tcp = request as? PhoenixLaunch.Request.Tcp ?: return null
        return if (LoopbackBindPolicy.needsNetworkBinding(tcp.host)) null else key(tcp.host, tcp.port)
    }

    /** Pure: whether a peer at [host]:[port] is the [declared] tunnel endpoint. */
    fun matches(declared: String?, host: String, port: Int): Boolean = declared != null && declared == key(host, port)

    fun declare(context: Context, request: PhoenixLaunch.Request) {
        val endpoint = endpointFor(request)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ENDPOINT, endpoint).apply()
        cached = endpoint
        loaded = true
    }

    fun isTunnel(context: Context, host: String, port: Int): Boolean {
        if (!loaded) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return false
            cached = prefs.getString(KEY_ENDPOINT, null)
            loaded = true
        }
        return matches(cached, host, port)
    }

    private fun key(host: String, port: Int) = "${host.trim().lowercase()}:$port"
}
