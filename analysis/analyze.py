# Generated with AI assistance (Claude Code, Claude Opus 5.5); reviewed by group
"""
SignalTrail analysis: tables + charts from the app's CSV files (any number of devices).

  python3 analyze.py --data ../data --out out

Expects measurements.csv (one row per request) in --data; signal_samples.csv / connection_events.csv
are optional. Outputs (in --out):
  sessions.csv                 one row per session (individual route repetitions)
  summary.csv                  mean ± SD across repetitions per device/condition/location/time window
  failure_rate.csv             failed requests / all requests (incl. timeouts and mismatches)
  excluded_sessions.csv        sessions where the connection type changed (kept out of comparisons)
  latency_by_location.png, speed_by_location.png, failure_rate_by_location.png
  wifi_rssi_vs_performance_<device>.png   (one per device)
  cellular_level_vs_performance.png       (cellular analysed separately)
  connection_events_summary.csv           (if connection_events.csv exists)

Rules followed (proposal Section 7):
  * the unit of repetition is the SESSION (route visit); the 3 probes inside one session are averaged
    first and never counted as 3 repetitions;
  * sessions with connection_changed = true are excluded from the main comparison;
  * each device keeps the same colour in every chart; Wi-Fi vs mobile data is shown by hatch / marker
    as well, so identity never depends on colour alone.
"""
import argparse
import os
import sys

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402

# Fixed categorical order (validated colour-blind-safe palette); never cycled.
DEVICE_COLORS = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4", "#008300"]
INK, MUTED, GRID = "#1F2933", "#5f6b76", "#e3e7ec"
TIME_LABEL = {"morning": "Morning 09:00–10:00", "lunch": "Lunch 12:15–13:15"}
CONN_LABEL = {"wifi": "Wi-Fi", "cellular": "mobile data"}
HATCH = {"wifi": "", "cellular": "///"}
MARKER = {"wifi": "o", "cellular": "^"}


def style_axes(ax):
    ax.spines[["top", "right"]].set_visible(False)
    ax.spines[["left", "bottom"]].set_color(MUTED)
    ax.tick_params(colors=MUTED, labelcolor=INK)
    ax.grid(axis="y", color=GRID, linewidth=0.8)
    ax.set_axisbelow(True)


def load(data_dir):
    path = os.path.join(data_dir, "measurements.csv")
    if not os.path.exists(path):
        sys.exit(f"measurements.csv not found in {data_dir}")
    df = pd.read_csv(path, na_values=["NA"], keep_default_na=True)
    df["connection_changed"] = df["connection_changed"].astype(str).str.lower().eq("true")
    df["condition"] = df["device_name"] + " " + df["intended_connection"].map(CONN_LABEL).fillna(df["intended_connection"])
    return df


def per_session(df):
    """Collapse request rows into one row per session (= one route repetition)."""
    probes = df[df.request_type == "probe"]
    dl = df[df.request_type == "download"]
    keys = ["session_id", "date", "day", "time_window", "run", "location", "device_name",
            "intended_connection", "condition"]
    base = df.groupby("session_id")[keys[1:]].first()
    s = pd.DataFrame(index=base.index)
    s[keys[1:]] = base
    ok_probe = probes[probes.outcome == "ok"]
    s["latency_ms"] = ok_probe.groupby("session_id")["latency_ms"].mean()
    s["latency_ok_n"] = ok_probe.groupby("session_id").size()
    ok_dl = dl[dl.outcome == "ok"]
    s["speed_mbps"] = ok_dl.groupby("session_id")["speed_mbps"].first()
    s["requests"] = df.groupby("session_id").size()
    s["failed_requests"] = df[df.outcome != "ok"].groupby("session_id").size()
    s["failed_requests"] = s["failed_requests"].fillna(0).astype(int)
    s["rssi_dbm"] = df.groupby("session_id")["rssi_dbm"].mean()
    s["cell_level"] = df.groupby("session_id")["cell_level_0_4"].mean()
    s["connection_changed"] = df.groupby("session_id")["connection_changed"].any()
    return s.reset_index()


def summarise(sessions):
    g = sessions.groupby(["device_name", "condition", "location", "time_window"])
    out = g.agg(
        n_sessions=("session_id", "count"),
        latency_mean_ms=("latency_ms", "mean"),
        latency_sd_ms=("latency_ms", "std"),
        speed_mean_mbps=("speed_mbps", "mean"),
        speed_sd_mbps=("speed_mbps", "std"),
        rssi_mean_dbm=("rssi_dbm", "mean"),
        cell_level_mean=("cell_level", "mean"),
        failed_requests=("failed_requests", "sum"),
        requests=("requests", "sum"),
    ).reset_index()
    out["failure_rate"] = out.failed_requests / out.requests
    return out.round(3)


def device_color_map(devices):
    return {d: DEVICE_COLORS[i % len(DEVICE_COLORS)] for i, d in enumerate(sorted(devices))}


def grouped_bars(sessions, summary, metric, sd, ylabel, title, fname, out, colors):
    """Small multiples: one panel per time window; bars = mean, whiskers = SD, dots = individual runs."""
    locations = sorted(sessions.location.unique())
    conds = (sessions[["condition", "device_name", "intended_connection"]].drop_duplicates()
             .sort_values(["device_name", "intended_connection"], ascending=[True, False]).values.tolist())
    windows = [w for w in ["morning", "lunch"] if w in set(sessions.time_window)] or sorted(sessions.time_window.unique())
    fig, axes = plt.subplots(1, len(windows), figsize=(6.5 * len(windows), 4.6), sharey=True, squeeze=False)
    width = 0.8 / max(len(conds), 1)
    x = np.arange(len(locations))
    for ax, w in zip(axes[0], windows):
        for i, (cond, dev, conn) in enumerate(conds):
            sub = summary[(summary.condition == cond) & (summary.time_window == w)].set_index("location")
            means = [sub[metric].get(l, np.nan) for l in locations]
            sds = [sub[sd].get(l, np.nan) if sd else 0 for l in locations]
            xs = x - 0.4 + width * (i + 0.5)
            ax.bar(xs, means, width * 0.9, color=colors[dev], hatch=HATCH.get(conn, ""), edgecolor="white",
                   linewidth=0.8, label=cond, zorder=2)
            if sd:
                ax.errorbar(xs, means, yerr=sds, fmt="none", ecolor=INK, elinewidth=1, capsize=3, zorder=3)
            runs = sessions[(sessions.condition == cond) & (sessions.time_window == w)]
            for j, l in enumerate(locations):
                v = runs[runs.location == l][metric_run_col(metric)].dropna()
                ax.scatter(np.full(len(v), xs[j]), v, s=14, color=INK, marker=MARKER.get(conn, "o"),
                           zorder=4, linewidths=0)
        ax.set_title(TIME_LABEL.get(w, w), color=INK, fontsize=11)
        ax.set_xticks(x)
        ax.set_xticklabels([l.split(" ", 1)[0] for l in locations])
        style_axes(ax)
    axes[0][0].set_ylabel(ylabel, color=INK)
    fig.suptitle(title, color=INK, fontsize=12)
    handles, labels = axes[0][0].get_legend_handles_labels()
    fig.legend(handles, labels, loc="lower center", ncol=min(len(labels), 4), frameon=False)
    fig.text(0.99, 0.01, "dots = individual sessions; whiskers = ±1 SD", ha="right", fontsize=8, color=MUTED)
    fig.tight_layout(rect=(0, 0.1, 1, 0.95))
    fig.savefig(os.path.join(out, fname), dpi=150)
    plt.close(fig)


def metric_run_col(metric):
    return {"latency_mean_ms": "latency_ms", "speed_mean_mbps": "speed_mbps", "failure_rate": "failure_rate"}[metric]


def wifi_scatter(df, out, colors):
    wifi = df[(df.intended_connection == "wifi") & (df.outcome == "ok")]
    for dev, sub in wifi.groupby("device_name"):
        fig, (a1, a2) = plt.subplots(1, 2, figsize=(10, 4))
        p = sub[sub.request_type == "probe"].dropna(subset=["rssi_dbm", "latency_ms"])
        d = sub[sub.request_type == "download"].dropna(subset=["rssi_dbm", "speed_mbps"])
        for ax, data, col, label in [(a1, p, "latency_ms", "Latency (ms)"), (a2, d, "speed_mbps", "Download speed (Mbps)")]:
            for w, mk in [("morning", "o"), ("lunch", "s")]:
                q = data[data.time_window == w]
                ax.scatter(q.rssi_dbm, q[col], s=36, color=colors[dev], marker=mk, edgecolors="white",
                           linewidths=1, label=TIME_LABEL[w])
            if len(data) >= 3 and data.rssi_dbm.nunique() > 1:
                r = data[["rssi_dbm", col]].corr(method="spearman").iloc[0, 1]
                ax.set_title(f"{label}  (Spearman ρ = {r:.2f}, n = {len(data)})", color=INK, fontsize=10)
            else:
                ax.set_title(label, color=INK, fontsize=10)
            ax.set_xlabel("Wi-Fi RSSI (dBm)", color=INK)
            ax.set_ylabel(label, color=INK)
            style_axes(ax)
        handles, labels = a1.get_legend_handles_labels()
        fig.legend(handles, labels, loc="lower center", ncol=2, frameon=False, fontsize=9)
        fig.suptitle(f"{dev}: Wi-Fi signal vs performance", color=INK)
        fig.tight_layout(rect=(0, 0.07, 1, 1))
        safe = "".join(c if c.isalnum() else "_" for c in dev)
        fig.savefig(os.path.join(out, f"wifi_rssi_vs_performance_{safe}.png"), dpi=150)
        plt.close(fig)


def cellular_plot(df, out, colors):
    cell = df[(df.intended_connection == "cellular") & (df.outcome == "ok")].dropna(subset=["cell_level_0_4"])
    if cell.empty:
        return
    fig, (a1, a2) = plt.subplots(1, 2, figsize=(10, 4))
    for ax, rtype, col, label in [(a1, "probe", "latency_ms", "Latency (ms)"),
                                  (a2, "download", "speed_mbps", "Download speed (Mbps)")]:
        sub = cell[cell.request_type == rtype].dropna(subset=[col])
        for k, (dev, dsub) in enumerate(sub.groupby("device_name")):
            jitter = (k - 0.5) * 0.12
            ax.scatter(dsub.cell_level_0_4 + jitter, dsub[col], s=36, color=colors[dev], marker="^",
                       edgecolors="white", linewidths=1, label=dev)
            means = dsub.groupby("cell_level_0_4")[col].mean()
            ax.plot(means.index + jitter, means.values, color=colors[dev], linewidth=2)
        ax.set_xticks(range(5))
        ax.set_xlim(-0.5, 4.5)
        ax.set_xlabel("Cellular signal level (0–4)", color=INK)
        ax.set_ylabel(label, color=INK)
        ax.set_title(label, color=INK, fontsize=10)
        style_axes(ax)
    handles, labels = a1.get_legend_handles_labels()
    fig.legend(handles, labels, loc="lower center", ncol=max(len(labels), 1), frameon=False, fontsize=9)
    fig.suptitle("Mobile data: signal level vs performance (line = mean per level)", color=INK)
    fig.tight_layout(rect=(0, 0.07, 1, 1))
    fig.savefig(os.path.join(out, "cellular_level_vs_performance.png"), dpi=150)
    plt.close(fig)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default=".", help="folder with measurements.csv")
    ap.add_argument("--out", default="out", help="output folder")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)

    df = load(args.data)
    sessions_all = per_session(df)
    excluded = sessions_all[sessions_all.connection_changed]
    sessions = sessions_all[~sessions_all.connection_changed].copy()
    sessions["failure_rate"] = sessions.failed_requests / sessions.requests
    main_rows = df[~df.session_id.isin(excluded.session_id)]

    summary = summarise(sessions)
    sessions_all.to_csv(os.path.join(args.out, "sessions.csv"), index=False)
    summary.to_csv(os.path.join(args.out, "summary.csv"), index=False)
    excluded.to_csv(os.path.join(args.out, "excluded_sessions.csv"), index=False)
    fr = (main_rows.assign(failed=main_rows.outcome != "ok")
          .groupby(["condition", "location", "time_window", "request_type"])
          .agg(requests=("failed", "size"), failed=("failed", "sum"),
               timeouts=("outcome", lambda s: (s == "timeout").sum()),
               errors=("outcome", lambda s: (s == "error").sum()),
               mismatches=("outcome", lambda s: (s == "mismatch").sum())).reset_index())
    fr["failure_rate"] = (fr.failed / fr.requests).round(3)
    fr.to_csv(os.path.join(args.out, "failure_rate.csv"), index=False)

    colors = device_color_map(df.device_name.unique())
    grouped_bars(sessions, summary, "latency_mean_ms", "latency_sd_ms", "Latency (ms, mean of 3 probes)",
                 "Latency per location (mean ± SD across sessions)", "latency_by_location.png", args.out, colors)
    grouped_bars(sessions, summary, "speed_mean_mbps", "speed_sd_mbps", "Download speed (Mbps, 1 MiB)",
                 "Download speed per location (mean ± SD across sessions)", "speed_by_location.png", args.out, colors)
    grouped_bars(sessions, summary, "failure_rate", None, "Failed requests (fraction)",
                 "Failure rate per location (timeouts, errors, mismatches)", "failure_rate_by_location.png",
                 args.out, colors)
    wifi_scatter(main_rows, args.out, colors)
    cellular_plot(main_rows, args.out, colors)

    ev_path = os.path.join(args.data, "connection_events.csv")
    if os.path.exists(ev_path):
        ev = pd.read_csv(ev_path, na_values=["NA"])
        ev.groupby(["date", "event", "transport"]).size().rename("count").reset_index() \
          .to_csv(os.path.join(args.out, "connection_events_summary.csv"), index=False)

    print(f"{len(sessions_all)} sessions ({len(excluded)} excluded: connection changed), "
          f"{len(df)} request rows, devices: {', '.join(sorted(colors))}")
    print(f"Outputs written to {os.path.abspath(args.out)}")
    with pd.option_context("display.width", 160, "display.max_columns", 20):
        print(summary[["condition", "location", "time_window", "n_sessions", "latency_mean_ms",
                       "latency_sd_ms", "speed_mean_mbps", "speed_sd_mbps", "failure_rate"]].to_string(index=False))


if __name__ == "__main__":
    main()
