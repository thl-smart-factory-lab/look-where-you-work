import math
import threading
from dataclasses import dataclass
from typing import Dict, Optional, Tuple
from pathlib import Path
from datetime import datetime
import re

import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation
import paho.mqtt.client as mqtt


# ----------------------------
# CONFIG
# ----------------------------
MQTT_HOST = "10.6.22.65"
MQTT_PORT = 1883
MQTT_TOPIC = "sf/telemetry/#"

HEADING_LEN = 2.5  # length of heading line in lab units

# If you use sentinels in Android:
POS_INVALID = -9999.0
ANG_INVALID = 9999.0

# Logging
LOG_DIR = Path("logs")
LOG_DIR.mkdir(exist_ok=True)


# ----------------------------
# STATIC LAB GEOMETRY (2D)
# ----------------------------
ANCHORS = {
    "A": (-1.440, 0.278),
    "B": (5.591, 1.000),
    "C": (0.400, 11.926),
    "D": (5.502, 11.633),
}

PRINTERS = {
    "p_A": (0.521, 4.6),
    "p_B": (0.521, 3.4),
    "p_C": (0.521, 2.2),
    "p_D": (5.585, 3.006),
}


# ----------------------------
# Influx Line Protocol parsing (simple)
# ----------------------------
def _unescape_tag(s: str) -> str:
    out = ""
    i = 0
    while i < len(s):
        if s[i] == "\\" and i + 1 < len(s):
            out += s[i + 1]
            i += 2
        else:
            out += s[i]
            i += 1
    return out


def parse_line_protocol(line: str):
    line = line.strip()
    if not line:
        return None

    parts = line.split(" ")
    if len(parts) < 2:
        return None

    mt = parts[0]
    fields_part = parts[1]
    ts_ns = None
    if len(parts) >= 3:
        try:
            ts_ns = int(parts[2])
        except Exception:
            ts_ns = None

    mt_parts = mt.split(",")
    measurement = mt_parts[0]
    tags: Dict[str, str] = {}
    for t in mt_parts[1:]:
        if "=" in t:
            k, v = t.split("=", 1)
            tags[_unescape_tag(k)] = _unescape_tag(v)

    # split fields by commas not inside quotes
    tokens = []
    cur = ""
    in_quotes = False
    esc = False
    for ch in fields_part:
        if esc:
            cur += ch
            esc = False
            continue
        if ch == "\\":
            cur += ch
            esc = True
            continue
        if ch == '"':
            in_quotes = not in_quotes
            cur += ch
            continue
        if ch == "," and not in_quotes:
            tokens.append(cur)
            cur = ""
        else:
            cur += ch
    if cur:
        tokens.append(cur)

    fields: Dict[str, object] = {}
    for tok in tokens:
        tok = tok.strip()
        if "=" not in tok:
            continue
        k, v = tok.split("=", 1)
        k = k.strip()
        v = v.strip()

        if len(v) >= 2 and v[0] == '"' and v[-1] == '"':
            raw = v[1:-1]
            raw = raw.replace('\\"', '"').replace("\\\\", "\\")
            fields[k] = raw
        else:
            try:
                fields[k] = float(v)
            except Exception:
                fields[k] = v

    return measurement, tags, fields, ts_ns


# ----------------------------
# Logging helpers
# ----------------------------
log_lock = threading.Lock()


def safe_filename(name: str) -> str:
    return re.sub(r"[^a-zA-Z0-9._-]+", "_", name)


def log_device_message(device_key: str, topic: str, payload: str):
    now = datetime.now().isoformat(timespec="milliseconds")
    filename = safe_filename(device_key) + ".log"
    path = LOG_DIR / filename

    line = f"{now} | topic={topic} | payload={payload}\n"

    with log_lock:
        with open(path, "a", encoding="utf-8") as f:
            f.write(line)


# ----------------------------
# Live state per device
# ----------------------------
@dataclass
class DeviceState:
    x: Optional[float] = None
    y: Optional[float] = None
    yaw: Optional[float] = None
    look_at: Optional[str] = None
    delta: Optional[float] = None
    dist: Optional[float] = None
    ts_ns: Optional[int] = None
    last_topic: Optional[str] = None


devices: Dict[str, DeviceState] = {}
lock = threading.Lock()


def is_valid_pos(v: Optional[float]) -> bool:
    return v is not None and not math.isnan(v) and v != POS_INVALID


def is_valid_ang(v: Optional[float]) -> bool:
    return v is not None and not math.isnan(v) and v != ANG_INVALID


# ----------------------------
# MQTT callbacks for PAHO 1.x
# ----------------------------
def on_connect(client, userdata, flags, rc):
    print(f"[MQTT] connected rc={rc}")
    client.subscribe(MQTT_TOPIC)
    print(f"[MQTT] subscribed {MQTT_TOPIC}")


def on_message(client, userdata, msg):
    payload = msg.payload.decode("utf-8", errors="replace").strip()

    # log raw to console
    print(f"[MQTT] topic={msg.topic} payload={payload}")

    parsed = parse_line_protocol(payload)
    if not parsed:
        return

    measurement, tags, fields, ts_ns = parsed

    if measurement != "pose":
        return

    # device key = deviceClass/deviceId if present, else topic
    dclass = tags.get("deviceClass", "unknown")
    did = tags.get("deviceId", msg.topic.replace("/", "_"))
    device_key = f"{dclass}/{did}"

    # write raw message to device specific logfile
    log_device_message(device_key, msg.topic, payload)

    x = fields.get("positionX")
    y = fields.get("positionY")
    yaw = fields.get("yaw")
    look = fields.get("lookAt")
    delta = fields.get("lookAtDeltaDeg")
    dist = fields.get("lookAtDistM")

    with lock:
        st = devices.get(device_key) or DeviceState()
        st.last_topic = msg.topic
        st.ts_ns = ts_ns
        st.x = float(x) if isinstance(x, (int, float)) else st.x
        st.y = float(y) if isinstance(y, (int, float)) else st.y
        st.yaw = float(yaw) if isinstance(yaw, (int, float)) else st.yaw
        st.look_at = str(look) if look is not None else st.look_at
        st.delta = float(delta) if isinstance(delta, (int, float)) else st.delta
        st.dist = float(dist) if isinstance(dist, (int, float)) else st.dist
        devices[device_key] = st

    print(
        f"[PARSE] device={device_key} "
        f"x={st.x} y={st.y} yaw={st.yaw} "
        f"lookAt={st.look_at} Δ={st.delta} dist={st.dist} ts={st.ts_ns}"
    )


def mqtt_thread():
    # PAHO 1.x style client creation
    client = mqtt.Client()
    client.on_connect = on_connect
    client.on_message = on_message
    client.connect(MQTT_HOST, MQTT_PORT, keepalive=30)
    client.loop_forever()


# ----------------------------
# Plot setup
# ----------------------------
fig, ax = plt.subplots(figsize=(8, 6), dpi=110)

# Anchors
ax.scatter(
    [p[0] for p in ANCHORS.values()],
    [p[1] for p in ANCHORS.values()],
    marker="o"
)
for k, (x, y) in ANCHORS.items():
    ax.text(x + 0.1, y + 0.1, k)

# Printers
ax.scatter(
    [p[0] for p in PRINTERS.values()],
    [p[1] for p in PRINTERS.values()],
    marker="s"
)
printer_text = {}
for k, (x, y) in PRINTERS.items():
    printer_text[k] = ax.text(x + 0.1, y, k)

# device_key -> (point_line, heading_line)
artists: Dict[str, Tuple] = {}

status_text = ax.text(0.02, 0.98, "", transform=ax.transAxes, va="top")

all_x = [p[0] for p in ANCHORS.values()] + [p[0] for p in PRINTERS.values()]
all_y = [p[1] for p in ANCHORS.values()] + [p[1] for p in PRINTERS.values()]
pad = 1.0
ax.set_xlim(min(all_x) - pad, max(all_x) + pad)
ax.set_ylim(min(all_y) - pad, max(all_y) + pad)
ax.set_aspect("equal", adjustable="box")
ax.grid(True)


def fmt_opt(v, nd=2):
    if v is None:
        return "-"
    try:
        return f"{float(v):.{nd}f}"
    except Exception:
        return str(v)


def ensure_artists(device_key: str):
    if device_key in artists:
        return artists[device_key]

    point, = ax.plot([], [], marker="o", linestyle="None", label=device_key)
    heading, = ax.plot([], [], linestyle="-")

    artists[device_key] = (point, heading)
    ax.legend(loc="lower left", fontsize=8)
    return artists[device_key]


def update(_frame):
    for k, t in printer_text.items():
        t.set_fontweight("normal")

    with lock:
        snapshot = dict(devices)

    for device_key, st in snapshot.items():
        point, heading = ensure_artists(device_key)

        if is_valid_pos(st.x) and is_valid_pos(st.y):
            point.set_data([st.x], [st.y])

            if is_valid_ang(st.yaw):
                rad = math.radians(st.yaw)
                hx = st.x + HEADING_LEN * math.cos(rad)
                hy = st.y + HEADING_LEN * math.sin(rad)
                heading.set_data([st.x, hx], [st.y, hy])
            else:
                heading.set_data([], [])
        else:
            point.set_data([], [])
            heading.set_data([], [])

        if isinstance(st.look_at, str) and st.look_at in PRINTERS and st.look_at != "none":
            printer_text[st.look_at].set_fontweight("bold")

    lines = [f"devices: {len(snapshot)}"]
    for dk, st in list(snapshot.items())[:5]:
        lines.append(
            f"{dk}: pos=({fmt_opt(st.x)},{fmt_opt(st.y)}) "
            f"yaw={fmt_opt(st.yaw)} lookAt={st.look_at}"
        )
    if len(snapshot) > 5:
        lines.append("...")

    status_text.set_text("\n".join(lines))
    return []


if __name__ == "__main__":
    t = threading.Thread(target=mqtt_thread, daemon=True)
    t.start()

    ani = FuncAnimation(fig, update, interval=100, cache_frame_data=False)
    plt.show()