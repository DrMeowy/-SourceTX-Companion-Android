# Changelog

All notable user-visible changes are recorded here.

## Unreleased

## 0.2.5 - 2026-08-17

- Fixed ESP32-S3 bootloader SYNC response validation and continuous SLIP stream
  buffering to ensure instant, reliable flashing connection when connecting with BOOT held.

## 0.2.4 - 2026-08-17

- Updated Report Bug action in bottom status bar to use distinctive red accent styling.

## 0.2.3 - 2026-08-17

- Fixed ESP32-S3 ROM bootloader synchronization to avoid resetting the chip
  out of download mode when connecting with BOOT held.
- Integrated permanent release signing keystore into the build and release pipeline.

## 0.2.2 - 2026-08-17

- Added interactive hardware pin configuration screen over USB OTG serial,
  allowing direct reading and writing of NVS hardware settings (CRSF single-wire
  UART pin, status LED mode/pins/brightness with WS2812 support, audio buzzer/DFPlayer
  pins, and vibration motor pin).
- Added real-time pin conflict detection preventing accidental assignment of
  the same physical GPIO to multiple functions.
- Added post-flash / post-update prompt allowing immediate hardware pin
  configuration after flashing.
- Added ESP32-S3 ROM preflight, flash erase/write, target-side MD5 verification,
  and reboot handling for the supported 4 MB board.
- Made `.stxm` and `.stxb` export/import compatible with SourceTX desktop and
  firmware formats, including complete multi-model backup and restore.
- Added USB permission/error handling and separated SourceTX serial transfers
  from Espressif bootloader-only firmware actions.
- Added explicit discovery for the ESP32-S3 native USB Serial/JTAG interface
  (`303A:1001`), whose vendor-class descriptors are missed by the stock probe.
- Hardened in-app companion updates with package, version, SHA-256, and signing
  certificate verification.
- Added responsive phone/tablet layouts, clearer confirmations and errors,
  persistent theme selection, stable/experimental channel display, and a
  disabled configuration preview.
- Added unit tests, Android lint/CI, signed release automation, legal notices,
  privacy/security documentation, and a Windows Gradle wrapper.

## 0.1.5

- Initial Android prototype.
