// Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group
package com.group5.signaltrail.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.util.UUID

/**
 * A user-editable description of a test device. Conditions (Wi-Fi / mobile data) are derived from it.
 * [model] is compared with Build.MODEL to auto-select the profile of the phone the app runs on.
 */
data class DeviceProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val model: String,
    val androidVersion: String,
    val hasCellular: Boolean,
    val wifiStandard: String = ""
) {
    fun matches(buildModel: String): Boolean =
        model.isNotBlank() && model.trim().equals(buildModel.trim(), ignoreCase = true)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("model", model)
        .put("androidVersion", androidVersion)
        .put("hasCellular", hasCellular)
        .put("wifiStandard", wifiStandard)

    companion object {
        fun fromJson(o: JSONObject) = DeviceProfile(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name"),
            model = o.optString("model"),
            androidVersion = o.optString("androidVersion"),
            hasCellular = o.optBoolean("hasCellular", false),
            wifiStandard = o.optString("wifiStandard")
        )

        /**
         * Pre-loaded profiles from the proposal. The model strings are our best guess of Build.MODEL
         * (GR5 2017 = BLL-L22, Pad 6 global = 23043RP34G). If the real value differs, the app shows
         * "New device detected" and offers "Link to existing profile", which fixes the model field.
         */
        val DEFAULTS = listOf(
            DeviceProfile(
                id = "huawei-gr5", name = "Huawei GR5", model = "BLL-L22",
                androidVersion = "7.0", hasCellular = true, wifiStandard = "Wi-Fi 4 (802.11n)"
            ),
            DeviceProfile(
                id = "xiaomi-pad6", name = "Xiaomi Pad 6", model = "23043RP34G",
                androidVersion = "14", hasCellular = false, wifiStandard = "Wi-Fi 6 (802.11ax)"
            )
        )
    }
}

/** Facts read from the running device (never assumed from a model name). */
data class DetectedDevice(
    val manufacturer: String,
    val model: String,
    val release: String,
    val sdk: Int,
    val hasTelephony: Boolean
) {
    val friendlyName: String
        get() = if (model.startsWith(manufacturer, ignoreCase = true)) model
        else "${manufacturer.replaceFirstChar { it.uppercase() }} $model"

    val summary: String get() = "$manufacturer $model · Android $release (API $sdk)"

    companion object {
        fun read(context: Context) = DetectedDevice(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            release = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            hasTelephony = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        )
    }
}

/** "Huawei GR5 Wi-Fi" / "Huawei GR5 mobile data" - the condition names used in the UI and analysis. */
fun conditionName(deviceName: String, intendedCsv: String): String =
    if (intendedCsv == "cellular") "$deviceName mobile data" else "$deviceName Wi-Fi"
