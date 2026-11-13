# Sentinel Academy Desktop (1.0.0)

The Compose Multiplatform desktop edition of Sentinel Academy —
same production Academy API, same session-token model, same
per-install `sa-bid` device-id discipline, same screens. It is a
**standalone Gradle build** in this directory (not a module of the
Android build), so the Android build's dependency pins stay
byte-identical.

## How sharing works

`build.gradle.kts` compiles the shared sources directly from
`../app/src/main/java`: the data layer (`Models`, `AcademyLogic`,
`CatalogCache`, `AppStore`, `AcademyApi`) and every UI screen
(`AcademyViewModel`, `AppRoot`, Auth, Home, Lesson, Exam, Certs/
Settings, Components, Motion, Confetti, Theme). Platform seams are
package-local functions with identical signatures in each
platform's `ui/Platform.kt`:

- strings/images — Android delegates to `androidx.compose.ui.res`;
  desktop reads tables **generated at build time from the same
  `strings.xml` files** (EN + ES, system locale) and the same PNG
  artwork;
- credential store — `AppStore` interface: Android
  `SecureStore` (Keystore), desktop `DesktopStore` (below);
- lifecycle — Android lifecycle observer; desktop window focus
  (focus loss = exam `blurs`, focus return = PIN relock);
- view-model creation, app version, external URL opening,
  reduced motion, extra settings section.

## Desktop credential store (`DesktopStore`)

Chosen at runtime, in order:

1. **Windows** — DPAPI (`CryptProtectData` via JNA) over the
   value file; only the same Windows user can decrypt.
2. **Linux** — Secret Service via libsecret (JNA), when the
   library is present and a secret service answers a probe.
3. **Fallback** — AES-256-GCM under a random key in a
   0600-permissions file in the per-user config dir
   (`~/.config/SentinelAcademy` / `%APPDATA%\SentinelAcademy`).
   When this posture is active the app's **About section labels
   it** ("OS key storage unavailable on this machine —
   credentials are encrypted with a key stored in a
   permissions-locked file").

The PIN verifier is a salted SHA-256 on every platform; the PIN
itself is never stored. The desktop install generates its own
stable 128-bit `sa-bid`, sent only to the Academy.

Honestly absent on desktop: `FLAG_SECURE` (no desktop equivalent
is claimed) and system-back navigation (detail screens carry
their own Back controls).

## Build

On the build box (this repo's family route — JDK 17, systemd-run
memory envelope):

```
cd desktop
../gradlew --version           # wrapper 8.9, same as Android
./gradlew packageDeb           # build/outputs/main/deb/*.deb
./gradlew run                  # local run
```

Dependencies resolve from Maven Central only and are pinned in
`gradle/verification-metadata.xml` (generated from the resolved
graph, committed — the fleet's hash-lock standard).
