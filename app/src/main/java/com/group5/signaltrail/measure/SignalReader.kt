// Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group
package com.group5.signaltrail.measure

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/** One signal reading. null = not available on this device / not permitted (written as NA). */
data class SignalSnapshot(
    val elapsedMs: Long,
    val rssiDbm: Int?,
    val wifiFreqMhz: Int?,
    val wifiLinkMbps: Int?,
    val cellLevel: Int?,
    val cellDbm: Int?,
    val cellNetworkType: String?
)

/**
 * Reads Wi-Fi RSSI and cellular signal level using the platform APIs named in the proposal.
 * Every call is defensive: a restricted API (OEM ROM, missing SIM, denied permission) yields null, never a crash.
 */
class SignalReader(context: Context) {
    private val ctx = context.applicationContext
    private val wifiManager = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Latest values pushed by the cellular listener (written on the main thread, read on IO threads).
    @Volatile private var cellLevel: Int? = null
    @Volatile private var cellDbm: Int? = null
    private var registration: Any? = null

    val hasTelephonyFeature: Boolean =
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    fun hasPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Dual SIM: measurements must describe the SIM that carries mobile DATA, so we bind the
     * TelephonyManager to SubscriptionManager.getDefaultDataSubscriptionId() (API 24+).
     */
    private fun dataTelephony(): TelephonyManager? {
        if (!hasTelephonyFeature) return null
        val base = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
        return try {
            val subId = SubscriptionManager.getDefaultDataSubscriptionId()
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) base.createForSubscriptionId(subId) else base
        } catch (e: Exception) {
            base
        }
    }

    fun simReady(): Boolean = try {
        dataTelephony()?.simState == TelephonyManager.SIM_STATE_READY
    } catch (e: Exception) {
        false
    }

    /** Must be called on the main thread (PhoneStateListener needs a Looper). Called from SignalTrailApp. */
    fun startCellular() {
        if (registration != null) return
        val tm = dataTelephony() ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31+: PhoneStateListener is deprecated; TelephonyCallback is the replacement.
                val cb = ModernCallback()
                tm.registerTelephonyCallback(ctx.mainExecutor, cb)
                registration = cb
            } else {
                // API 24-30: PhoneStateListener.onSignalStrengthsChanged (as in the proposal).
                val listener = LegacyListener()
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
                registration = listener
            }
        } catch (e: Exception) {
            registration = null   // restricted ROM / SecurityException -> cellular values stay NA
        }
    }

    @Suppress("DEPRECATION")
    private inner class LegacyListener : PhoneStateListener() {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
            signalStrength?.let { update(it) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private inner class ModernCallback : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            update(signalStrength)
        }
    }

    private fun update(ss: SignalStrength) {
        cellLevel = ss.level   // SignalStrength.getLevel(): 0 (none/poor) .. 4 (great), API 23+
        cellDbm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // getCellSignalStrengths() exists only from API 29; Integer.MAX_VALUE means "unavailable".
            ss.cellSignalStrengths.firstOrNull()?.dbm?.takeIf { it in -160..-1 }
        } else null
    }

    /**
     * Wi-Fi info. API 31+: WifiManager.getConnectionInfo() is deprecated; the documented replacement is
     * NetworkCapabilities.getTransportInfo() cast to WifiInfo. We fall back to WifiManager if that is null
     * (e.g. Wi-Fi connected but not the default network). RSSI needs no location permission.
     */
    private fun currentWifiInfo(): WifiInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val caps = try { cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } } catch (e: Exception) { null }
            (caps?.transportInfo as? WifiInfo)?.let { return it }
        }
        return try {
            @Suppress("DEPRECATION")
            wifiManager?.connectionInfo
        } catch (e: Exception) {
            null
        }
    }

    fun snapshot(): SignalSnapshot {
        val wifiOn = try { wifiManager?.isWifiEnabled == true } catch (e: Exception) { false }
        val info = if (wifiOn) currentWifiInfo() else null
        // -127 (WifiInfo.INVALID_RSSI) is reported when Wi-Fi is not connected.
        val rssi = info?.rssi?.takeIf { it in -126..-1 }
        val freq = info?.frequency?.takeIf { rssi != null && it > 0 }
        val link = info?.linkSpeed?.takeIf { rssi != null && it > 0 }
        return SignalSnapshot(
            elapsedMs = SystemClock.elapsedRealtime(),
            rssiDbm = rssi,
            wifiFreqMhz = freq,
            wifiLinkMbps = link,
            cellLevel = if (hasTelephonyFeature) cellLevel else null,
            cellDbm = if (hasTelephonyFeature) cellDbm else null,
            cellNetworkType = cellNetworkType()
        )
    }

    /**
     * getDataNetworkType() needs READ_PHONE_STATE. Before Android 11 the deprecated getNetworkType()
     * works without it, so we use that when the permission is denied. Otherwise: NA.
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun cellNetworkType(): String? {
        val tm = dataTelephony() ?: return null
        return try {
            val type = when {
                hasPhoneStatePermission() -> tm.dataNetworkType
                Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> tm.networkType
                else -> return null
            }
            networkTypeName(type)
        } catch (e: Exception) {
            null
        }
    }

    private fun networkTypeName(type: Int): String? = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
        TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
        TelephonyManager.NETWORK_TYPE_CDMA -> "2G CDMA"
        TelephonyManager.NETWORK_TYPE_1xRTT -> "2G 1xRTT"
        TelephonyManager.NETWORK_TYPE_IDEN -> "2G iDEN"
        16 -> "2G GSM"                                     // NETWORK_TYPE_GSM (API 25)
        TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G EVDO"
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA -> "3G HSPA"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "3G HSPA+"
        TelephonyManager.NETWORK_TYPE_EHRPD -> "3G eHRPD"
        17 -> "3G TD-SCDMA"                                // NETWORK_TYPE_TD_SCDMA (API 25)
        TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
        18 -> "IWLAN"                                      // NETWORK_TYPE_IWLAN (API 25)
        19 -> "4G LTE-CA"                                  // hidden constant used by some OEMs
        20 -> "5G NR"                                      // NETWORK_TYPE_NR (API 29); 5G NSA reports LTE
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> null
        else -> "type_$type"
    }
}
