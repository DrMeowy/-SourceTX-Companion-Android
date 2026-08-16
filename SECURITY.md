# Security Policy

## Supported versions

Security fixes are provided for the newest published stable version. Older
debug, prerelease, and experimental packages are not supported.

## Reporting a vulnerability

Do not publish an exploitable vulnerability, signing key, private firmware
package, or personal transmitter backup in a public issue. Contact the SourceTX
maintainer privately through the repository owner's security contact or GitHub
Security Advisories and include:

- the affected app and firmware versions;
- the connected board and Android version;
- reproduction steps and expected impact; and
- logs with serial numbers, model data, keys, and personal information removed.

## Trust model

The app accepts stable firmware only after its manifest and image signatures,
hashes, target identity, size, and ESP32-S3 image structure pass validation. An
Android app update must also match the installed package name and signing
certificate. These checks must not be bypassed to work around a failed update.

Never commit or share the Android release keystore or SourceTX firmware signing
private key. A suspected key disclosure requires stopping releases and rotating
the affected trust root before another package is published.
