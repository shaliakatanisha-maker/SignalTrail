# Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group
"""
SignalTrail measurement server (Python 3.8+, standard library only).

  GET /probe     -> "ok" (2 bytes)                      used for latency
  GET /download  -> exactly 1,048,576 fixed bytes       used for download speed

Both responses: Content-Length set, no caching, no compression, "Connection: close" honoured.
Every request is printed to the console (and appended to server_log.csv) so a session can be
cross-checked with the phone's measurements.csv.

Examples
  python3 server.py --port 8080                                   # plain HTTP (pilot / behind a tunnel)
  python3 server.py --port 8443 --cert cert.pem --key key.pem     # HTTPS with your own certificate
"""
import argparse
import datetime as dt
import os
import ssl
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DOWNLOAD_SIZE = 1_048_576  # 1 MiB
# Deterministic, incompressible-looking content: bytes 0..255 repeated (identical every run).
DOWNLOAD_BODY = bytes(range(256)) * (DOWNLOAD_SIZE // 256)
assert len(DOWNLOAD_BODY) == DOWNLOAD_SIZE

LOG_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "server_log.csv")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "SignalTrailServer/1.0"

    def do_GET(self):
        start = time.perf_counter()
        path = self.path.split("?", 1)[0]
        if path == "/probe":
            body, ctype = b"ok", "text/plain; charset=utf-8"
        elif path == "/download":
            body, ctype = DOWNLOAD_BODY, "application/octet-stream"
        else:
            body, ctype = b"not found", "text/plain; charset=utf-8"
        status = 200 if path in ("/probe", "/download") else 404

        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        # no-transform stops proxies/CDNs (e.g. Cloudflare) from compressing or altering the body
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, no-transform")
        self.send_header("Pragma", "no-cache")
        if self.headers.get("Connection", "").lower() == "close":
            self.send_header("Connection", "close")
            self.close_connection = True
        self.end_headers()
        try:
            self.wfile.write(body)
            self.wfile.flush()
            sent = len(body)
        except (BrokenPipeError, ConnectionResetError):
            sent = -1  # client gave up (e.g. its 15 s timeout)
        self._log(path, status, sent, (time.perf_counter() - start) * 1000)

    def _log(self, path, status, sent, ms):
        now = dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
        client = self.headers.get("CF-Connecting-IP") or self.client_address[0]
        ua = self.headers.get("User-Agent", "-")
        print(f"{now}  {client:<15} {path:<10} {status}  {sent:>8} B  {ms:8.1f} ms  {ua}", flush=True)
        new = not os.path.exists(LOG_FILE)
        with open(LOG_FILE, "a", encoding="utf-8") as f:
            if new:
                f.write("server_time,client,path,status,bytes_sent,server_ms,user_agent\n")
            f.write(f"{now},{client},{path},{status},{sent},{ms:.1f},\"{ua}\"\n")

    def log_message(self, fmt, *args):  # silence the default stderr log (we print our own line)
        pass


def main():
    ap = argparse.ArgumentParser(description="SignalTrail probe/download server")
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("--cert", help="PEM certificate (enables HTTPS)")
    ap.add_argument("--key", help="PEM private key")
    args = ap.parse_args()

    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    scheme = "http"
    if args.cert:
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ctx.minimum_version = ssl.TLSVersion.TLSv1_2  # Android 7 supports TLS 1.2
        ctx.load_cert_chain(args.cert, args.key)
        httpd.socket = ctx.wrap_socket(httpd.socket, server_side=True)
        scheme = "https"
    print(f"SignalTrail server on {scheme}://{args.host}:{args.port}  (/probe, /download = {DOWNLOAD_SIZE} B)")
    print("time                     client          path       code     bytes   server-ms  user-agent")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nstopped")


if __name__ == "__main__":
    main()
