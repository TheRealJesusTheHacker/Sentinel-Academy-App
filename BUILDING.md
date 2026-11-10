# Building the Sentinel Academy App

## Toolchain

- JDK 17
- Android SDK platform 35, build-tools 35.0.0
- Gradle via the wrapper (8.9) — do not substitute a system Gradle;
  the wrapper version is part of the verified build.

`local.properties` (git-ignored) points at your SDK:

```
sdk.dir=/path/to/android-sdk
```

## Dependency verification

`gradle/verification-metadata.xml` pins the SHA-256 of every
artifact the verified build resolves — the `--require-hashes`
discipline. A build that resolves an artifact not matching its pin
fails. When dependencies change, regenerate and review the metadata
in the same commit; never widen or delete pins to make a build pass.

## Builds

```sh
# Unit tests + unsigned compile proofs (debug and release):
./gradlew testDebugUnitTest assembleDebug assembleRelease

# The signed release (requires the owner's signing environment):
./gradlew signedRelease
```

## Signing

Release signing reads four environment variables, exactly as named
in `app/build.gradle.kts`:

- `SENTINEL_APP_KEYSTORE` (path to the PKCS12 keystore)
- `SENTINEL_APP_KEYSTORE_PASSWORD`
- `SENTINEL_APP_KEY_ALIAS`
- `SENTINEL_APP_KEY_PASSWORD`

All four set → release builds sign with the owner's key. Some set →
configuration fails on purpose. None set → release assembles
unsigned (compile proof only), and the signed task refuses to run.
The CI release workflow supplies them from the repo's `release`
environment secrets; locally they come from the owner's key custody.
**Never commit a keystore or a password.**

The family signing certificate (all three Sentinel apps) has
SHA-256 fingerprint
`e30cf3915a9a8774025d04bf3625748219ca4b9ea67bf5779cbca0a8b1f1e28d` —
the release workflow asserts it on every signed artifact; if an
APK's signer differs, it is not a Sentinel release.
