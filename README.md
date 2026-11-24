<!-- Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group -->
# SignalTrail: campus network measurement

CS4473 Mobile Computing, Group Research Project, **Option F**. Group 5: Kavya G.A.C. (220318E),
Shalika T.K.T. (220604D).

SignalTrail is a small Android research instrument. At each campus location (P1–P5) it records the Wi-Fi
RSSI or the cellular signal level every 5 s. It measures latency with 3 HTTPS probes and download speed
with one 1 MiB download, and writes every result, including failures, to CSV. It follows Sections 5–7
of our proposal.

```
SignalTrail/
├── app/                      Android app (Kotlin, Jetpack Compose, minSdk 24 / targetSdk 34)
│   └── src/main/java/com/group5/signaltrail/
│       ├── SignalTrailApp.kt     long-lived objects (connection logging between locations)
│       ├── MainActivity.kt       single Activity, bottom navigation, permission dialog
│       ├── AppViewModel.kt       UI state, profiles, start/validate session
│       ├── data/                 DeviceProfile, SettingsStore (JSON prefs), CsvStore (append + fsync)
│       ├── measure/              ConnectionMonitor, SignalReader, HttpProber, SessionRunner
│       └── ui/                   Measure, History, Devices, Export screens + theme
├── server/server.py          /probe and /download (1,048,576 B) test server
├── analysis/analyze.py       pandas + matplotlib tables and charts
├── .github/workflows/build-apk.yml
└── AI_USAGE_LOG.md
```

## 1. Build the APK

**Requirements:** JDK 17 or newer, and the Android SDK (platform 34 and build-tools 34). Android Studio
installs both.

### Android Studio
1. *File → Open…* and select the `SignalTrail` folder. Let Gradle sync.
2. *Build → Build App Bundle(s) / APK(s) → Build APK(s)*.

### Command line
```bash
cd SignalTrail
# only if Android Studio is not installed: tell Gradle where the SDK is
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties      # macOS; Linux: ~/Android/Sdk
./gradlew assembleDebug
```
Output: **`app/build/outputs/apk/debug/app-debug.apk`**. It is signed with the debug key, so no signing
setup is needed.

### GitHub Actions
Pushing to GitHub runs `.github/workflows/build-apk.yml`. Download the APK from the run's
**Artifacts → SignalTrail-debug.apk**.

## 2. Install on the phones

* **USB (adb):** enable *Developer options → USB debugging*, then
  `adb install -r app/build/outputs/apk/debug/app-debug.apk`
  * Xiaomi/MIUI/HyperOS also needs *Developer options → Install via USB* (requires a Mi account and SIM).
* **Sideload:** copy the APK to the device (USB, Drive, Telegram…), open it, and allow *Install unknown
  apps* for the file manager or browser.
  * Huawei EMUI: *Settings → Security → More settings → Install apps from external sources*.

### Battery optimisation (do this on both devices)
The app keeps the screen on while open, but EMUI and MIUI can still kill or freeze apps.
* **Huawei GR5 (EMUI 5):** *Settings → Battery → Lock screen cleanup / Protected apps* → protect SignalTrail;
  *Settings → Apps → SignalTrail → Battery → "Keep running after screen off"*.
* **Xiaomi Pad 6 (MIUI/HyperOS):** *Settings → Apps → Manage apps → SignalTrail → Battery saver → No
  restrictions*, and enable *Autostart*. Lock the app in *Recents* (long-press → lock).
* Android generally: *Settings → Apps → SignalTrail → Battery → Unrestricted / Don't optimise*.
* Keep **VPNs and background downloads off** during sessions (proposal §6, fair testing).

## 3. Run the server
See [`server/README.md`](server/README.md). Short version:
```bash
cd server && python3 server.py --port 8080
cloudflared tunnel --url http://localhost:8080      # prints https://<name>.trycloudflare.com
```
Enter that `https://…` URL as **Server base URL** in the app, then press **Test server** once on Wi-Fi
and once on mobile data. **Use the same address for all sessions.**

## 4. Run a session
1. **Measure** tab: check the **device profile** (auto-selected by `Build.MODEL`).
2. Choose the **condition**: `<Device> Wi-Fi`, or `<Device> mobile data` (only shown for profiles with
   cellular and when the SIM/data network is present).
3. Set up the network:
   * Wi-Fi test: mobile data **off**, connected to campus Wi-Fi.
   * Mobile-data test: Wi-Fi **off**, mobile data on.

   The Live status card shows **OK** or **Connection mismatch**, and Start stays disabled until it matches.
4. Select **location**, **day (1–3)**, **time window** and **run** (suggested automatically). Stand at
   the marked spot and hold the device in the same orientation.
5. Press **Start measurement**. The app waits 10 s to stabilise, then sends 3 × `/probe` and
   1 × `/download`. It samples the signal every 5 s throughout. A session takes about 15–40 s.
6. The **Result** card shows the samples, 3 latencies, download bytes, duration and Mbps, plus an outcome
   chip for each request.
7. Repeat for the other conditions at the same spot, changing their order across days (proposal §5).
   Don't run the two devices' downloads at the same time.

**History** lists sessions (filters by device, location, time window and condition) and shows progress
against the target (default 30 per device-condition = 5 locations × 2 times × 3 days). *Delete last
session* is only for a session recorded by mistake, for example with the wrong location selected.

## 5. Add a device
**Devices** tab → *Add this device* (pre-filled from `Build.MANUFACTURER/MODEL/VERSION`) or *Add profile*.
On first launch on an unknown model the app asks *"New device detected. Add profile?"*. If the device
is one of the pre-loaded profiles but with a different `Build.MODEL`, use **Link to …**, which copies
the real model into that profile.

## 6. Export
**Export** tab → *Share* (FileProvider share sheet: Gmail, Drive, Bluetooth…) or *Save as…* (Storage Access
Framework, any folder). No storage permission is used. Files are stored in
`Android/data/com.group5.signaltrail/files/data/`, so this also works:
`adb pull /sdcard/Android/data/com.group5.signaltrail/files/data/ ./data/`.
**Uninstalling the app deletes the data, so export after every measurement day.**

## 7. CSV format
Missing or unavailable values are written as `NA`. Each row is written, flushed and `fsync`-ed
immediately.

### measurements.csv: one row per request (3 probes + 1 download per session)
| column | meaning |
|---|---|
| date | `yyyy-MM-dd` (phone local time) |
| session_id | `yyyyMMdd-HHmmss-DEVICE` |
| day, time_window, run | 1–3, `morning`/`lunch`, repetition number |
| location | e.g. `P1 Central Court` |
| device_name | profile name, e.g. `Huawei GR5` |
| device_manufacturer, device_model, android_version, api_level | from `android.os.Build` |
| has_cellular | from the profile |
| intended_connection / actual_connection | `wifi`, `cellular`, `vpn`, `ethernet`, `other`, `none` (actual = checked just before the request) |
| wifi_frequency_mhz, rssi_dbm | `WifiInfo.getFrequency()`, `getRssi()` just before the request |
| cell_level_0_4, cell_network_type | `SignalStrength.getLevel()`, e.g. `4G LTE` |
| request_type | `probe` or `download` |
| latency_ms | probes with outcome ok: start → response fully read, **including DNS/TCP/TLS setup** |
| bytes_received | body bytes counted by the app (download must be 1048576) |
| duration_ms | request duration (all request types and outcomes) |
| speed_mbps | `bytes × 8 / (seconds × 1,000,000)`, downloads with outcome ok |
| outcome | `ok`, `timeout` (>15 s), `error` (HTTP/IO error or wrong byte count), `mismatch` (wrong network; request not sent) |
| connection_changed | `true` if the network type differed before/after, or a network switch happened during the request |
| time_local, elapsed_realtime_ms, detail | extra: wall-clock time, monotonic clock, error text |

### signal_samples.csv: every 5 s during a session
`date, time_local, elapsed_realtime_ms, session_id, location, device_name, intended_connection,
actual_connection, rssi_dbm, wifi_frequency_mhz, wifi_link_speed_mbps, cell_level_0_4, cell_dbm,
cell_network_type`

### connection_events.csv: from app start, including between locations
`date, time_local, elapsed_realtime_ms, event (available | lost | capabilities_changed), network_id,
transport, validated, downstream_kbps, last_location, session_id`

## 8. Analysis
```bash
cd analysis
pip install -r requirements.txt
python3 analyze.py --data ../data --out out
```
The script works for any number of devices and produces:
* mean ± SD per location, time window and condition, computed **across sessions**, with the 3 probes
  averaged within a session first;
* individual runs (dots on each bar);
* failure rates;
* Wi-Fi RSSI vs latency and speed scatter plots, one per device;
* cellular level vs performance, analysed separately.

Sessions with `connection_changed = true` are excluded and listed in `excluded_sessions.csv`.

## 9. Pilot-test checklist (before day 1)
- [ ] APK installs and opens on **both** devices; the top bar shows *SignalTrail*.
- [ ] Devices tab: the profile shows **Matched** on each device (otherwise use *Link to …*).
- [ ] Huawei: Phone permission answered. Live status shows a cellular level 0–4 and a network type (or NA if denied).
- [ ] Live Wi-Fi RSSI is shown in dBm on both devices and changes when you cover the device or walk away.
- [ ] Turning Wi-Fi off and on shows *Connection mismatch* → *OK*, with rows in `connection_events.csv`.
- [ ] **Test server** succeeds over campus Wi-Fi **and** over mobile data with the same `https://` URL.
- [ ] One full session per condition: 4 result rows, download bytes = 1048576, outcome OK.
- [ ] Unplug the server and run a session: rows appear with `error`/`timeout`, and the app does not crash.
- [ ] Export: share the CSVs to a laptop and open them. `analyze.py` runs on the pilot data.
- [ ] Mark the exact positions P1–P5 (photo and description); note device orientation.
- [ ] Battery optimisation disabled (section 2); VPN off; mobile data quota checked (≈30 MiB + overhead).
- [ ] Delete pilot sessions (or keep them in a separate export) before the real day 1.

## 10. Assumptions and known limitations
* **Model strings:** the pre-loaded profiles use the guesses `BLL-L22` (GR5 2017) and `23043RP34G` (Pad 6).
  Correct them with *Link to …* on first launch.
* **RSSI update rate:** `getRssi()` returns the value cached by the Wi-Fi driver, which is typically
  refreshed every few seconds. Two samples 5 s apart can therefore be identical. We do not trigger Wi-Fi
  scans, so Android's scan throttling (4 scans / 2 min) does not apply.
* **Cellular:** the level is the 0–4 "bars", which the OEM computes from RSRP/RSSNR. dBm is only
  available on Android 10+, so it is NA on the GR5. 5G NSA is reported as LTE. The network type needs
  READ_PHONE_STATE on Android 11+ (NA if denied).
* **EMUI/MIUI** may delay callbacks or kill the app in the background. Keep the app in the foreground
  with the screen on (the app sets FLAG_KEEP_SCREEN_ON).
* **Tunnel latency:** a Cloudflare/ngrok tunnel adds edge and tunnel delay and variability. Absolute
  latencies include this; comparisons between conditions are still fair because the path is identical.
* **Latency includes connection setup** (DNS + TCP + TLS) by design (proposal §6). Expect values several
  times a plain ping. A 1 MiB download is a *short-download speed*, dominated by TCP slow start and
  setup, **not** the link's maximum throughput.
* **Clock:** durations use `SystemClock.elapsedRealtime()` (ms resolution). Wall-clock dates come from
  the phone's clock, so keep automatic time enabled.
* Two devices, one operator and three days: results cannot attribute differences to the Android version,
  the Wi-Fi standard or congestion alone (proposal §7).
