# LookWhereYouWork

This repository bundles the materials for the *LookWhereYouWork* project:

- the paper PDF,
- the Android prototype used in the project,
- the measurement logs collected during the experiments.

It is intended as a compact artifact repository for sharing the paper, implementation, and recorded data together.

## Repository Structure

```text
.
|-- Paper/
|   `-- 2026-IPIN-Lool-Where-You-Work.pdf
|-- LookWhereYouWork/
|   `-- Android Studio project
`-- measurements/
    |-- glasses/
    `-- ref/
```

## Contents

### Paper

The [`Paper/`](Paper) directory contains the paper as a PDF:

- [`Paper/2026-IPIN-Lool-Where-You-Work.pdf`](Paper/2026-IPIN-Lool-Where-You-Work.pdf)

Paper title:

*Look Where You Work: View-Aligned Asset Identification with UWB and Smart Glasses*

### Android App

The [`LookWhereYouWork/`](LookWhereYouWork) directory contains the Android application source code.

From the current codebase, the app:

- connects to an MQTT broker,
- subscribes to UWB and printer-related topics,
- visualizes position and heading inside a lab map,
- infers which printer a user is looking at based on pose and field of view.

Relevant implementation areas include:

- [`LookWhereYouWork/app/src/main/java/com/example/lookwhereyouwork/mqtt/`](LookWhereYouWork/app/src/main/java/com/example/lookwhereyouwork/mqtt)
- [`LookWhereYouWork/app/src/main/java/com/example/lookwhereyouwork/ui/`](LookWhereYouWork/app/src/main/java/com/example/lookwhereyouwork/ui)
- [`LookWhereYouWork/app/src/main/java/com/example/lookwhereyouwork/geometry/`](LookWhereYouWork/app/src/main/java/com/example/lookwhereyouwork/geometry)

Important app configuration details:

- Android `minSdk = 23`
- Android `targetSdk = 35`
- Java/Kotlin target `11`
- MQTT broker configured in [`LookWhereYouWork/app/src/main/java/com/example/lookwhereyouwork/mqtt/MqttConfig.kt`](LookWhereYouWork/app/src/main/java/com/example/lookwhereyouwork/mqtt/MqttConfig.kt)

To open or build the app:

```bash
cd LookWhereYouWork
./gradlew assembleDebug
```

On Windows:

```powershell
cd LookWhereYouWork
.\gradlew.bat assembleDebug
```

### Measurements

The [`measurements/`](measurements) directory contains recorded telemetry logs for multiple devices and scenarios.

Current top-level split:

- `measurements/ref/`: reference measurements
- `measurements/glasses/`: measurements associated with the glasses setup

Within these folders, scenarios are grouped into directories such as:

- `statisch1`
- `statisch2`
- `statisch-winkel-1-langsam`
- `statisch-winkel-2-schneller`
- `dynamisch1`
- `dynamisch2`
- `statisch-dynamisch-3d`
- `dyn_3d_2`

Each scenario contains per-device log files, for example:

- `glass_google-glass-enterprise-edition-2.log`
- `pixel_google-pixel-8-pro.log`
- `vuzix_vuzix-blade-2.log`
- `vuzix_vuzix-vuzix-m4000.log`

The log lines are plain text telemetry records and include fields such as:

- timestamp,
- MQTT topic,
- device class and device ID,
- yaw, pitch, and roll,
- position coordinates,
- inferred `lookAt` target and related metrics.

## Citation

If you use this repository, the Android implementation, the measurements, or ideas derived from this work, please cite the paper:

```bibtex
@INPROCEEDINGS{pelka2026lookwhereyouwork,
  author={Pelka, Mathias and Willemsen, Thomas},
  booktitle={2026 International Conference on Indoor Positioning and Indoor Navigation (IPIN)},
  title={Look Where You Work: View-Aligned Asset Identification with UWB and Smart Glasses},
  year={2026},
  address={Rome, Italy}
}
```

## License

The repository is released under the MIT License. See [`LICENSE`](LICENSE).
