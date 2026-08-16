# SourceTX Companion for Android

The official Android companion for the SourceTX ESP32-S3 surface transmitter.
It is designed for people who do not need to know PlatformIO, partition
layouts, or serial commands.

## What it can do

- Install a verified SourceTX factory image on the supported 4 MB ESP32-S3
  SuperMini/ST7796/FT6x36 target.
- Download and install signed stable firmware updates while preserving normal
  settings and model storage.
- Export one model as `.stxm` or all configured models as a `.stxb` bundle.
- Validate and restore desktop-compatible `.stxm` and `.stxb` backups.
- Check for signed Android companion updates from GitHub.
- Remember the selected light or dark theme.

Experimental firmware and live transmitter configuration are visible as
disabled previews; they are not advertised as working features.

## Requirements

- Android 8.0 (API 26) or newer.
- A phone or tablet with USB host/OTG support and a data-capable USB cable.
- For firmware installation or update: the currently supported official
  ESP32-S3 4 MB target, placed in its ROM bootloader when requested.
- Internet access to retrieve signed firmware or companion releases.

The app refuses unknown boards, untrusted download hosts, unsigned manifests,
invalid image layouts, incompatible model data, and Android updates signed by
a different app certificate.

## Build from source

Install JDK 17 and Android SDK 34, then open this directory in Android Studio
or run:

```text
gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

On macOS or Linux:

```text
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Production releases must use the permanent SourceTX Android signing key. See
[`docs/RELEASING.md`](docs/RELEASING.md); never publish a debug-signed APK as a
release.

## Safety and privacy

Factory erase permanently removes firmware, settings, calibration, and model
data from the connected board. The app displays a separate confirmation before
performing it. Keep USB connected for the entire write and verification step.

The app does not contain advertising or analytics and does not upload model
data. See [`PRIVACY.md`](PRIVACY.md) and [`SECURITY.md`](SECURITY.md).

## License

SourceTX-owned code is provided under the SourceTX Personal and Non-Commercial
License in [`LICENSE`](LICENSE). Third-party components retain their own terms;
see [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
