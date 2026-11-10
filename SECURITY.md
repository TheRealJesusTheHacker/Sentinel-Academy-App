# Security — Sentinel Academy App

## Posture

- **No secrets in the repo.** Signing material and credentials live
  in the owner's custody and the CI `release` environment only.
  Every repository is treated as public, whether it is or not.
- **Minimal surface.** The foundation phase requests **no Android
  permissions** and makes no network calls. The INTERNET permission
  arrives in phase 2 together with the API client it exists for.
- **On-device store.** The app-lock scaffolding's encrypted store
  (Android Keystore-backed, AES-256-GCM) is excluded from Android
  backup and device-to-device transfer — the PIN verifier inside it
  is device-bound by design. The PIN itself is never stored; only a
  salted SHA-256 verifier is.
- **Screen protection.** `FLAG_SECURE` is set from the first frame,
  so screenshots and the recents preview never expose a student's
  record.
- **Dependency integrity.** Gradle dependency verification pins
  every resolved artifact by SHA-256; builds fail on mismatch
  (see BUILDING.md).
- **Release integrity.** Release builds are minified with an
  enumerated, deliberate ruleset (`app/proguard-rules.pro`), signed
  only with the owner's custodied key, and the release workflow
  asserts the signing certificate's fingerprint before publishing.

## Reporting

Security issues with Sentinel Safeguard software:
**sentinel@sentinelsafeguard.net**. Please do not open a public
issue for a vulnerability.
