# Sentinel Academy App

The native Android app for **Sentinel Academy** — the learning side
of Sentinel Safeguard LLC — and the third app in the Sentinel
phone-app family (after the Command App and the Sentinel Mail App).

**Phase 1 — foundation (this release, 1.0.0).** Everything that
does not depend on the Academy server's app API:

- The **Emerald Depth** theme — the family identity. Every
  `ColorScheme` role is set explicitly; there is no Material
  baseline purple anywhere in this app, ever.
- The estate **motion layer** (press-sink, staggered list
  entrances, error shake, the two-beat drawn tick, hold-to-confirm)
  with its timing law pinned by unit tests, reduced-motion honoured
  throughout.
- The Academy's **own app icon** — the academy hall (pediment,
  columns, open book), approved by the owner 2026-11-10, preserved
  in `brand/` and pinned byte-for-byte by test. In-app company
  surfaces use the real Sentinel Safeguard logo, as everywhere in
  the family.
- A branded **start screen** that says honestly what the app is
  today. No fake courses, no dead buttons: a control with nothing
  real behind it does not ship.
- The **app-lock secure-store scaffolding** (Keystore-backed
  encrypted store + salted-hash PIN verifier), present and
  deliberately unwired — phase 2 gives it a lock to be.

Coming in later phases, each against the real server contract
(`docs/API-CONTRACT.md`): sign-in, catalog, lessons, progress,
streaks, certificates — then, last by the owner's sequencing, the
paywall.

## Building

JDK 17, Android SDK platform 35, Gradle wrapper (8.9). See
[BUILDING.md](BUILDING.md). Release builds sign only with the
owner's custodied key — there is no fallback key; unsigned and
debug-signed builds are compile proofs, never releases.

Dependency integrity is enforced: `gradle/verification-metadata.xml`
pins every artifact the verified build resolves, and builds fail on
any artifact that does not match its pin.

## Security

See [SECURITY.md](SECURITY.md) and [HARDENING.md](HARDENING.md).
No secrets live in this repository — signing material is injected
from the release environment at build time.
