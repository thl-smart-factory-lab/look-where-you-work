# Measurements

This directory contains the raw measurement data collected for the *Look Where You Work* paper. It is split into two parts:

- [`ref/`](ref) — reference data from a laser tracker with T-Probe (6DOF) used as ground truth.
- [`glasses/`](glasses) — telemetry recorded by the Android app on the evaluated smart glasses and a smartphone.

All measurements were recorded on 2026-03-13 in the lab. Positions in the reference data are in **millimetres**; positions in the app telemetry are in **metres**.

## Directory layout

```text
measurements/
├── ref/
│   ├── Ref_Measurements_LaserTrackerT-Probe_static.txt
│   ├── UDP_dyn1_trajectory.csv
│   └── UDP_dyn2_3D_orientation.csv
└── glasses/
    ├── glass_google-glass-enterprise-edition-2.log
    ├── pixel_google-pixel-8-pro.log
    ├── vuzix_vuzix-blade-2.log
    ├── vuzix_vuzix-vuzix-m4000.log
    ├── statisch1/
    ├── statisch2/
    ├── statisch-winkel-1-langsam/
    ├── statisch-winkel-2-schneller/
    ├── dynamisch1/
    ├── dynamisch2/
    ├── statisch-dynamisch-3d/
    └── dyn_3d_2/
```

## Reference data (`ref/`)

The reference measurements were captured with a laser tracker using a T-Probe, exported from *SpatialAnalyzer SA 2025.2*. Coordinates are relative to the lab-nominal frame `Solldaten::Nominal-Frame`. Stated uncertainties correspond to a 68.26 % confidence interval (1σ).

### `Ref_Measurements_LaserTrackerT-Probe_static.txt`

Static reference coordinates for the UWB system check. The file contains several groups:

- `UWB_Ancher` — nominal anchor positions (A–D).
- `UWB_Einmessung` — measured anchor positions (A–D) with uncertainties.
- `UWB_check_ist` — nominal positions of the six static check points (`check1`–`check6`, plus repeated `check5_1` / `check6_1`).
- `UWB_Check_Position` — measured positions of the same check points including per-axis uncertainty.
- `3DDrucker` — measured positions of the four 3D printers (A–D) used as the "look-at" targets.

Column layout (whitespace-separated):

```text
Group   Point   X[mm]   Y[mm]   Z[mm]   [U-x]   [U-y]   [U-z]   [U-mag]   [Timestamp]
```

### `UDP_dyn1_trajectory.csv`

6DOF reference trajectory for the dynamic run in which the cart was pushed through the lab. These coordinates are the ones used in the paper for the trajectory evaluation.

### `UDP_dyn2_3D_orientation.csv`

6DOF reference for the 3D orientation run. In this scenario the probe was also rotated around the X and Y axes (not only around Z) to exercise the full orientation space.

Row layout for both CSV files (comma-separated, key/value pairs in fixed order):

```text
X,<x[mm]>,Y,<y[mm]>,Z,<z[mm]>,Rx,<rx[deg]>,Ry,<ry[deg]>,Rz,<rz[deg]>,Time(sec),<t[s]>
```

- `X`, `Y`, `Z`: translation in millimetres.
- `Rx`, `Ry`, `Rz`: rotation in degrees.
- `Time(sec)`: timestamp in seconds relative to the start of the tracker session.

## Glasses / smartphone data (`glasses/`)

Each subfolder corresponds to one measurement scenario. Inside every scenario folder there is one log file per device, always the same four devices:

| File prefix | Device |
| --- | --- |
| `glass_google-glass-enterprise-edition-2.log` | Google Glass Enterprise Edition 2 |
| `pixel_google-pixel-8-pro.log` | Google Pixel 8 Pro (smartphone reference) |
| `vuzix_vuzix-blade-2.log` | Vuzix Blade 2 |
| `vuzix_vuzix-vuzix-m4000.log` | Vuzix M4000 |

The four `*.log` files directly under `glasses/` were captured outside of the individual scenarios (e.g. setup / free-run data) and follow the same format.

### Scenarios

| Folder | Description |
| --- | --- |
| `statisch1`, `statisch2` | Static measurements at the validation points (`check1`–`check6`). |
| `statisch-winkel-1-langsam` | Static position, device rotated slowly around the vertical axis. |
| `statisch-winkel-2-schneller` | Same as above, rotated faster. |
| `dynamisch1` | Dynamic trajectory — cart pushed through the lab (paired with `ref/UDP_dyn1_trajectory.csv`). |
| `dynamisch2` | Second dynamic trajectory run. |
| `statisch-dynamisch-3d` | Static position with 3D orientation changes (paired with `ref/UDP_dyn2_3D_orientation.csv`). |
| `dyn_3d_2` | Second run with 3D orientation. |

### Log format

Each line is one telemetry record published by the app to the MQTT broker and mirrored into the log. The format is:

```text
<ISO-timestamp> | topic=<mqtt-topic> | payload=<line-protocol> <nanosecond-timestamp>
```

Example:

```text
2026-03-13T13:28:14.504 | topic=sf/telemetry/vuzix/vuzix-blade-2 | payload=pose,deviceClass=vuzix,deviceId=vuzix-blade-2,tagName=uwb-a yaw=-1.46,pitch=0.01,roll=-2.84,positionX=5.42,positionY=4.54,lookAt="p_D",lookAtDeltaDeg=-0.71,lookAtDistM=1.54 1773404896199000000
```

The payload uses InfluxDB line protocol:

- Measurement: `pose`
- Tags: `deviceClass`, `deviceId`, `tagName` (the UWB tag used for positioning).
- Fields:
  - `yaw`, `pitch`, `roll` — device orientation in degrees.
  - `positionX`, `positionY` — UWB position in metres (2D, lab frame).
  - `lookAt` — id of the printer currently in the field of view (`p_A`–`p_D`), or `"none"`.
  - `lookAtDeltaDeg` — angular deviation to that printer in degrees; `9999.00` when no target is selected.
  - `lookAtDistM` — distance to that printer in metres; `9999.00` when no target is selected.
- Trailing integer: Unix timestamp in nanoseconds.

## Aligning app telemetry with reference data

- Convert the laser-tracker translations from millimetres to metres before comparing to `positionX` / `positionY`.
- The laser tracker provides full 3D (`X`, `Y`, `Z`); the app logs 2D (`positionX`, `positionY`) — the vertical component is fixed by the UWB setup.
- Orientation conventions differ: the reference uses `Rx`, `Ry`, `Rz` in degrees relative to the nominal frame; the app logs `yaw`, `pitch`, `roll` in degrees after the in-app calibration. A per-scenario offset is expected.
- Time axes are independent: the tracker CSVs use session-relative seconds, the app logs use absolute UTC timestamps plus a nanosecond Unix timestamp. Align via the nanosecond timestamp and the known start of each tracker run.
