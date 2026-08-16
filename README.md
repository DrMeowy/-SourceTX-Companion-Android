# SourceTX Companion for Android

Native Android companion application (**Kotlin + Jetpack Compose**) for the SourceTX RC surface transmitter ecosystem.

---

## Features

- **Direct USB-C OTG Model Transfer**:
  - Connect your Android phone or tablet directly to the ESP32-S3 transmitter via USB-C cable.
  - Export the active model (`.stxm`) or back up all transmitter models as a complete bundle (`.stxb`).
  - Inspect, validate, and restore models directly to any slot (1–20) with live Schema 21 FNV-1a verification.
- **Factory Installation & Firmware Updates**:
  - Preflight checks validating ESP32-S3 hardware identity and flash geometry.
  - Safety-first confirmation workflows with optional factory-erase protection.
- **Mobile-Optimized Touch UI**:
  - Jetpack Compose Material3 interface with seamless Dark (`#0D0F14`) and Light (`#F1F5F9`) themes matching the desktop companion app.
- **Auto-Attach USB Filter**:
  - Automatically recognizes ESP32-S3 (VID `0x303A`) upon cable connection.

---

## Project Structure

```
SourceTX-Companion-Android/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/targets.json
│       │   ├── res/xml/usb_device_filter.xml
│       │   └── java/com/sourcetx/companion/
│       │       ├── MainActivity.kt
│       │       ├── protocol/
│       │       │   ├── ModelTransferProtocol.kt
│       │       │   ├── SourceTxModelEnvelope.kt
│       │       │   ├── SourceTxModelBundle.kt
│       │       │   └── TargetsCatalog.kt
│       │       ├── usb/
│       │       │   ├── SourceTxUsbManager.kt
│       │       │   └── SourceTxSerialClient.kt
│       │       ├── viewmodel/
│       │       │   └── MainViewModel.kt
│       │       └── ui/
│       │           ├── theme/
│       │           ├── components/
│       │           └── screens/
│       └── test/
│           └── java/com/sourcetx/companion/
│               └── ModelTransferProtocolTest.kt
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Building

Open the root folder in **Android Studio** (Hedgehog or newer) and click **Run** or run:

```bash
./gradlew assembleDebug
```
