# Publishing a SourceTX Companion Android Release

This procedure preserves Android update compatibility and prevents an unsigned
or debug-signed package from becoming an official release.

## One-time signing setup

1. Create one long-lived Android release keystore offline and back it up in at
   least two encrypted locations. Losing it prevents future in-place updates.
2. Add the base64-encoded keystore to the repository secret
   `ANDROID_KEYSTORE_BASE64`.
3. Add `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and
   `ANDROID_KEY_PASSWORD` as repository secrets.
4. Never commit the keystore, decoded key, passwords, or private firmware
   signing key. The repository ignores common key filenames, but that is only a
   final guardrail.

## Release checklist

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Move the relevant `CHANGELOG.md` entries from Unreleased into the version.
3. Confirm stable firmware manifests and binaries are already published and
   signed in `sourcetx-updates` for the supported target.
4. Run `gradlew.bat testReleaseUnitTest lintRelease assembleRelease` locally.
   An unsigned local APK is suitable for inspection only.
5. Commit and push the reviewed source.
6. Create and push a signed tag exactly matching `versionName`, for example
   `v0.2.0`.
7. Check the **Publish Signed Android Release** workflow. It validates the tag,
   builds with the stored release key, verifies the APK signature, generates a
   SHA-256 file, and publishes both immutable assets.
8. On a clean Android device, install the release and perform the hardware
   acceptance checks below before announcing it.

The published APK filename must remain:

```text
SourceTX-Companion-v<version>.apk
```

The in-app updater deliberately accepts only that exact filename plus its
matching `.sha256` asset.

## Hardware acceptance checks

- Fresh install on the minimum supported Android version and a current Android
  version.
- USB permission denial, unplug during connection, wrong USB device, and a
  charge-only cable all produce understandable errors without crashing.
- Export one `.stxm`, export all models as `.stxb`, restore the single model to
  another slot, and restore the complete bundle. Verify the same files with the
  Windows companion.
- Factory-install a blank supported board, confirm first boot, touch/display,
  settings persistence, model storage, and CRSF behavior.
- Update an existing board without erase and confirm its saved settings and
  models remain intact.
- Confirm full erase requires the stronger warning and actually removes saved
  data.
- Interrupt neither a real flash nor erase intentionally unless a sacrificial
  board and recovery procedure are available.
- Install the previous production-signed APK, use the in-app update prompt, and
  confirm Android accepts the new package without uninstalling.

## Transition from old debug builds

The previously shared 0.1.x APK was debug-signed. Android will not install a
production-signed build over an app signed by a different key. Users of that
prototype must export any wanted data, uninstall it once, and install the first
production-signed release. Do not weaken the signing-certificate check to hide
this one-time transition.
