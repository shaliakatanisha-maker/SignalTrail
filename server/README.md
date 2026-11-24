<!-- Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group -->
# SignalTrail test server

Standard-library Python 3.8+ (no `pip install` needed).

| Endpoint | Response |
|---|---|
| `GET /probe` | `ok` (2 bytes), used for latency |
| `GET /download` | exactly **1,048,576** fixed bytes (`00 01 … FF` repeated), `Content-Length: 1048576` |

Both send `Cache-Control: no-store, no-cache, no-transform`, never compress, and close the connection
when the client sends `Connection: close` (the app always does). Each request is printed and appended
to `server_log.csv`.

## 1. Plain HTTP (pilot test on the same Wi-Fi only)

```bash
cd server
python3 server.py --port 8080
# phone (same Wi-Fi as the laptop): http://<laptop-LAN-IP>:8080
```

Campus Wi-Fi often blocks client-to-client traffic, and **mobile data can never reach a laptop's private
IP**. That is why the real study needs one public HTTPS address (section 3).

## 2. HTTPS with a self-signed certificate (optional)

```bash
cd server
# Replace 192.168.1.50 with the laptop's IP (or use DNS:my.host.name)
openssl req -x509 -newkey rsa:2048 -nodes -days 120 \
  -keyout key.pem -out cert.pem -subj "/CN=signaltrail" \
  -addext "subjectAltName=IP:192.168.1.50"
python3 server.py --port 8443 --cert cert.pem --key key.pem
```

The phone must trust the certificate. Copy `cert.pem` to the phone, then go to *Settings → Security →
Encryption & credentials → Install a certificate → CA certificate*. The menu path differs on EMUI and
MIUI; search Settings for "certificate". The app's `network_security_config.xml` trusts user-installed
CAs, which Android 7+ otherwise ignores. Android 7.0 needs `subjectAltName`; the CN alone is not
accepted.

## 3. One HTTPS address reachable from campus Wi-Fi and cellular (recommended)

The proposal requires both networks to hit **the same HTTPS address**. Choose one option and keep it for
all 90 sessions:

| Option | Command | Notes |
|---|---|---|
| **Cloudflare Tunnel** (quick) | `cloudflared tunnel --url http://localhost:8080` | Prints `https://<random>.trycloudflare.com`. The free quick tunnel URL **changes every restart**, so keep the laptop running or create a named tunnel (`cloudflared tunnel create signaltrail`) for a stable hostname. |
| **ngrok** | `ngrok http 8080` | Prints `https://<id>.ngrok-free.app`. Free plan has request limits and may show an interstitial page to browsers. The app sends a non-browser User-Agent, but add header `ngrok-skip-browser-warning` if you see HTML instead of `ok`. |
| **Small VPS** | run `server.py` on the VPS behind Caddy/nginx with a Let's Encrypt certificate | Most stable, and no laptop needed. Run it in the same region as Sri Lanka if possible. |

Run the server locally on port 8080 (plain HTTP); the tunnel provides HTTPS. Enter the `https://…`
URL in the app's **Server base URL** field and press **Test server** on both Wi-Fi and mobile data.

### Added latency (state this in the report)

With a tunnel, the path is phone → Cloudflare/ngrok edge (often Singapore or Mumbai) → tunnel → laptop
on campus. This adds tens of ms (often 30–150 ms) and its own variability to every probe. The large
majority of a short download time can be the TLS and tunnel setup, not the radio link. Because the
same path is used for every condition, **comparisons between locations, times and conditions stay
valid**, but absolute latencies are not "network latency to campus". Also note the laptop's own
uplink: if the laptop is on campus Wi-Fi, the download crosses campus Wi-Fi twice.

### Android 7 and certificates

Cloudflare and ngrok certificates chain to Let's Encrypt (ISRG Root X1/X2) or Google Trust Services
(GTS Root R1/R4). Android 7.0 does not include all of these roots, so the app bundles them in
`app/src/main/res/raw/` and trusts them through `network_security_config.xml`.
