// Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group
package com.group5.signaltrail.data

import android.content.Context
import org.json.JSONArray

/** Small key-value store (SharedPreferences + JSON) for profiles and settings. No database needed. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("signaltrail_settings", Context.MODE_PRIVATE)

    fun loadProfiles(): List<DeviceProfile> {
        val raw = prefs.getString(KEY_PROFILES, null)
        if (raw == null) {
            saveProfiles(DeviceProfile.DEFAULTS)   // first launch: pre-load the two proposal devices
            return DeviceProfile.DEFAULTS
        }
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { DeviceProfile.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            DeviceProfile.DEFAULTS
        }
    }

    fun saveProfiles(list: List<DeviceProfile>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    var serverUrl: String
        get() = prefs.getString("server_url", "https://") ?: "https://"
        set(v) = prefs.edit().putString("server_url", v).apply()

    /** Target sessions per device-condition (5 locations x 2 times x 3 days = 30). */
    var sessionTarget: Int
        get() = prefs.getInt("session_target", 30)
        set(v) = prefs.edit().putInt("session_target", v).apply()

    var locations: List<String>
        get() {
            val raw = prefs.getString("locations", null) ?: return DEFAULT_LOCATIONS
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
                    .ifEmpty { DEFAULT_LOCATIONS }
            } catch (e: Exception) {
                DEFAULT_LOCATIONS
            }
        }
        set(v) {
            val arr = JSONArray()
            v.forEach { arr.put(it) }
            prefs.edit().putString("locations", arr.toString()).apply()
        }

    var lastProfileId: String?
        get() = prefs.getString("last_profile_id", null)
        set(v) = prefs.edit().putString("last_profile_id", v).apply()

    /** Build.MODEL for which the user answered "Not now" to the new-device prompt (avoid nagging). */
    var dismissedModel: String?
        get() = prefs.getString("dismissed_model", null)
        set(v) = prefs.edit().putString("dismissed_model", v).apply()

    var phonePermissionAsked: Boolean
        get() = prefs.getBoolean("phone_perm_asked", false)
        set(v) = prefs.edit().putBoolean("phone_perm_asked", v).apply()

    companion object {
        private const val KEY_PROFILES = "profiles"
        val DEFAULT_LOCATIONS = listOf(
            "P1 Central Court",
            "P2 Goda Uda Canteen",
            "P3 Department Entrance",
            "P4 Indoor Corridor",
            "P5 FYP Lab/Seminar Room"
        )
    }
}
