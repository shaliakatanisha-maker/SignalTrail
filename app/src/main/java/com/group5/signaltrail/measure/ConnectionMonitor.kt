// Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group
package com.group5.signaltrail.measure

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.group5.signaltrail.data.CsvStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger

/** Network type as written to the CSV ("csv") and shown in the UI ("label"). */
enum class ConnType(val csv: String, val label: String) {
    WIFI("wifi", "Wi-Fi"),
    CELLULAR("cellular", "Mobile data"),
    ETHERNET("ethernet", "Ethernet"),
    VPN("vpn", "VPN"),
    OTHER("other", "Other"),
    NONE("none", "No connection")
}

/**
 * Watches the DEFAULT network with ConnectivityManager.registerDefaultNetworkCallback (API 24+).
 * It is registered once in SignalTrailApp, so logging continues between locations while the app is open.
 * Every onAvailable / onLost is written to connection_events.csv.
 */
class ConnectionMonitor(context: Context, private val csv: CsvStore) {
    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _current = MutableStateFlow(ConnType.NONE)
    val current: StateFlow<ConnType> = _current

    /** Counts default-network switches, so a switch DURING a request is caught even if before == after. */
    private val switches = AtomicInteger(0)
    val switchCount: Int get() = switches.get()

    @Volatile var lastLocation: String = ""
    @Volatile var sessionId: String = ""

    private val lastSummary = HashMap<Network, String>()
    private var started = false

    fun typeOf(caps: NetworkCapabilities?): ConnType = when {
        caps == null -> ConnType.NONE
        // A VPN also reports its underlying transport, so check it first (VPNs must be off during tests).
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnType.VPN
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnType.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnType.CELLULAR
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnType.ETHERNET
        else -> ConnType.OTHER
    }

    /** Synchronous check of the active network; used before and after every request. */
    fun activeType(): ConnType = try {
        typeOf(cm.activeNetwork?.let { cm.getNetworkCapabilities(it) })
    } catch (e: Exception) {
        ConnType.NONE
    }

    /** True if any cellular data network currently exists (it may not be the default one). */
    @Suppress("DEPRECATION") // allNetworks is deprecated on API 31 but still works and is the only API-24 option
    fun hasCellularNetwork(): Boolean = try {
        cm.allNetworks.any {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        }
    } catch (e: Exception) {
        false
    }

    fun refresh() {
        _current.value = activeType()
    }

    fun start() {
        if (started) return
        try {
            cm.registerDefaultNetworkCallback(callback)
            started = true
        } catch (e: Exception) {
            // Some OEM builds throw (e.g. too many callbacks). The app still works with activeType() polling.
            log("register_failed", "NA", ConnType.NONE.csv, null, null)
        }
        refresh()
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            switches.incrementAndGet()
            val caps = cm.getNetworkCapabilities(network)
            log("available", network.toString(), typeOf(caps).csv, caps?.validated(), caps?.linkDownstreamBandwidthKbps)
            refresh()
        }

        override fun onLost(network: Network) {
            switches.incrementAndGet()
            val previous = synchronized(lastSummary) { lastSummary.remove(network) }
            log("lost", network.toString(), previous?.substringBefore('|') ?: "unknown", null, null)
            refresh()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            // Android calls this very often (e.g. on every Wi-Fi RSSI change). To keep the log readable
            // we only write a row when the transport or the "validated" (internet works) state changes.
            val summary = "${typeOf(caps).csv}|${caps.validated()}"
            val changed = synchronized(lastSummary) { lastSummary.put(network, summary) != summary }
            if (changed) {
                log("capabilities_changed", network.toString(), typeOf(caps).csv, caps.validated(),
                    caps.linkDownstreamBandwidthKbps)
            }
            refresh()
        }
    }

    private fun NetworkCapabilities.validated() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private fun log(event: String, networkId: String, transport: String, validated: Boolean?, kbps: Int?) {
        val (date, time) = CsvStore.stamp()
        try {
            csv.appendEvent(listOf(date, time, SystemClock.elapsedRealtime(), event, networkId, transport,
                validated, kbps, lastLocation.ifBlank { null }, sessionId.ifBlank { null }))
        } catch (e: Exception) {
            // never crash the network callback thread because of storage problems
        }
    }
}
