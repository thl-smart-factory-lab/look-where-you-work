# LookWhereYouWork

`LookWhereYouWork` is an Android app for visualizing UWB position data, device orientation, and MQTT messages in a lab environment. The app shows the current position on a map, determines which printer the user is most likely looking at based on device orientation, and displays the latest MQTT payload for that printer.

## Overview

After launch, the app automatically connects to an MQTT broker and mainly works with two kinds of data:

- UWB position data
- printer topics

In addition, it continuously publishes telemetry containing position, orientation, and inferred viewing direction.

## Requirements

- Android Studio
- installed Android SDK
- Android device with network access to the MQTT broker
- working orientation / motion sensors
- MQTT broker with the expected topics

## Configuration

Before using the app, the main environment-specific settings should be adjusted to match your lab setup.

### MQTT broker and topics

The MQTT configuration is located in [MqttConfig.kt](app/src/main/java/com/example/lookwhereyouwork/mqtt/MqttConfig.kt).

The following values are currently defined there:

- `HOST`
- `PORT`
- `TOPICS`

If the broker address, port, or topic names change, this file needs to be updated.

### Lab geometry

The room geometry is defined in [LabGeometry.kt](app/src/main/java/com/example/lookwhereyouwork/geometry/LabGeometry.kt).

This file contains, among other things:

- UWB anchor positions
- printer positions
- angle corrections used by the viewing-direction logic

If the physical room layout changes, these values should be updated as well.

### Android SDK

The local SDK configuration is stored in `local.properties`, for example:

```properties
sdk.dir=C\:\\AndroidSDK
```

This file is machine-specific and should not be reused across different environments.

## Usage

After launch, the app automatically connects to the configured MQTT broker.

### Map view

The main page shows:

- the current position on the lab map
- the viewing direction calculated from the device sensors
- the currently inferred printer inside the field of view

The bottom section also displays:

- `Looking at`: the currently detected printer
- `Orientation`: yaw, pitch, and roll
- `Delta`: angular deviation to the detected printer

### Reset

Pressing `Reset` sets the current device orientation as the new reference. This is useful if the orientation starts drifting or the initial calibration was not accurate enough.

### Printer view

The second page shows the topic of the most recently detected printer. Below that, the latest received payload for that printer topic is displayed. If no printer has been detected yet, a placeholder message remains visible.

### Debug view

On Pixel devices, an additional debug area is shown with MQTT status information and the latest topic values. On other devices, this extra view may be reduced.

## MQTT behavior

The app currently processes in particular:

- `sf/UWB/uwb-a` for position data
- `sf/printer/a` to `sf/printer/d` for printer status

It also publishes telemetry to a topic using the following pattern:

```text
sf/telemetry/<deviceClass>/<deviceId>
```

The telemetry includes, among other things:

- yaw, pitch, and roll
- `positionX` and `positionY`
- the currently inferred printer
- angular deviation and distance

### Example: incoming UWB location data

The app currently reads UWB position data from the topic:

```text
sf/UWB/uwb-a
```

The payload must contain `positionX` and `positionY`. Other fields may be present, but they are ignored by the position parser.

Example payload:

```text
tagId=uwb-a, positionX=2.35, positionY=4.80, quality=0.97
```

Another valid example:

```text
timestamp=1712345678 positionX=1.20 positionY=3.45 anchor=A
```

Important:

- the topic must match the configured UWB topic
- the payload must include both `positionX` and `positionY`
- the values must be numeric
- the exact field order does not matter

## Installation and startup

### Run from Android Studio

1. Open the project in Android Studio.
2. Make sure the SDK and JDK are configured correctly.
3. Connect a device via USB or start an emulator.
4. Run the app from Android Studio.

### Build the debug APK

From the project directory:

```powershell
.\gradlew.bat assembleDebug
```

The APK is then available at:

```text
app/build/intermediates/apk/debug/app-debug.apk
```

### Install the APK

With USB debugging enabled, the APK can be installed via ADB:

```powershell
adb install -r app/build/intermediates/apk/debug/app-debug.apk
```

Alternatively, the APK can be copied to the device and installed manually. Depending on the Android version, installation from unknown sources may need to be enabled.

## APK in the repository

It is technically possible to commit an APK to the repository, but for normal development this is usually not a good idea. APKs are binary files, increase repository size, and are hard to diff meaningfully in Git.

This is more reasonable in cases like:

- a final submission
- a fixed demo build
- providing the app directly to non-developers from the repository

For day-to-day development, it is better to build the APK locally when needed.

## Notes

- Broker, topics, and lab geometry are currently hardcoded in the app.
- The app expects matching MQTT payloads, especially position data containing `positionX` and `positionY`.
- Working device sensors are important for the viewing-direction logic.
