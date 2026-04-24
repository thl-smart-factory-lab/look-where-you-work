# LookWhereYouWork

This repository bundles the materials for the *Look Where You Work* project:

- the paper PDF,
- the Android prototype used in the experiments,
- the reference and device measurements collected during the evaluation.

It is intended as a compact artifact repository so that paper, implementation, and recorded data stay together.

## Repository structure

```text
.
├── Paper/
│   └── 2026-IPIN-Lool-Where-You-Work.pdf
├── look-where-you-work-main/
│   └── Android Studio project (app module, gradle wrapper, ...)
├── measurements/
│   ├── ref/        # laser-tracker ground truth
│   └── glasses/    # per-device telemetry per scenario
├── LICENSE
└── README.md
```

## Paper

Located in [`Paper/`](Paper):

- [`Paper/2026-IPIN-Lool-Where-You-Work.pdf`](Paper/2026-IPIN-Lool-Where-You-Work.pdf)

Title: *Look Where You Work: View-Aligned Asset Identification with UWB and Smart Glasses*. Accepted at the 2026 International Conference on Indoor Positioning and Indoor Navigation (IPIN), Rome, Italy.

## Android app

The app source lives in [`look-where-you-work-main/`](look-where-you-work-main). It is an Android Studio project that:

- connects to an MQTT broker,
- ingests UWB position data (`sf/UWB/uwb-a`) and printer status topics (`sf/printer/a`–`sf/printer/d`),
- renders the current position and heading on a lab map,
- infers which printer the user is looking at from pose and field of view,
- publishes telemetry to `sf/telemetry/<deviceClass>/<deviceId>`.

Key files:

- MQTT config: [`look-where-you-work-main/app/src/main/java/com/example/lookwhereyouwork/mqtt/MqttConfig.kt`](look-where-you-work-main/app/src/main/java/com/example/lookwhereyouwork/mqtt/MqttConfig.kt)
- Lab geometry (anchor + printer coordinates, angle corrections): [`look-where-you-work-main/app/src/main/java/com/example/lookwhereyouwork/geometry/LabGeometry.kt`](look-where-you-work-main/app/src/main/java/com/example/lookwhereyouwork/geometry/LabGeometry.kt)

Build/Targets: `minSdk = 23`, `targetSdk = 35`, Java/Kotlin target `11`.

Build a debug APK:

```bash
cd look-where-you-work-main
./gradlew assembleDebug        # Linux/macOS
.\gradlew.bat assembleDebug    # Windows
```

See [`look-where-you-work-main/README.md`](look-where-you-work-main/README.md) for configuration details, installation via ADB, and runtime behavior.

## Measurements

The [`measurements/`](measurements) directory holds the data used in the paper:

- [`measurements/ref/`](measurements/ref) — laser-tracker (T-Probe) ground truth: a static file for the anchor / check-point / printer coordinates and two 6DOF CSVs for the dynamic trajectory and 3D orientation runs.
- [`measurements/glasses/`](measurements/glasses) — per-device telemetry logs, organized into scenario folders (`statisch*`, `dynamisch*`, `statisch-winkel-*`, `statisch-dynamisch-3d`, `dyn_3d_2`). Devices covered per scenario: Google Glass Enterprise Edition 2, Vuzix Blade 2, Vuzix M4000, and a Google Pixel 8 Pro as smartphone reference.

Format details, scenario descriptions, and alignment notes between reference and device data are documented in [`measurements/README.md`](measurements/README.md).

## Citation

> **Note:** The paper is currently under review. The citation below is preliminary and will be updated once the review process is complete.

If you use this repository, the Android implementation, the measurements, or ideas derived from this work, please cite:

```bibtex
@INPROCEEDINGS{pelka2026lookwhereyouwork,
  author={Pelka, Mathias and Willemsen, Thomas},
  booktitle={2026 International Conference on Indoor Positioning and Indoor Navigation (IPIN)},
  title={Look Where You Work: View-Aligned Asset Identification with UWB and Smart Glasses},
  year={2026},
  address={Rome, Italy},
  note={Under review}
}
```

## License

Released under the MIT License. See [`LICENSE`](LICENSE).
