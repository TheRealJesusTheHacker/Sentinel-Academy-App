# Hardening notes — Sentinel Academy App

What the foundation phase carries, and what each later phase must
re-audit (the family cycle is build → audit → fix → re-audit).

**Carried in phase 1**

- Full explicit Compose color scheme (no Material baseline values
  can reach the screen).
- Zero permissions; `usesCleartextTraffic=false`; single exported
  activity (the launcher).
- SecureStore (Keystore-backed encrypted preferences) with the PIN
  held only as a salted SHA-256 verifier, constant-time comparison,
  and backup/transfer exclusion for the store file.
- `FLAG_SECURE` on the main window.
- R8 minification + resource shrinking with a deliberate, commented
  ruleset; debug/verbose logging stripped mechanically.
- Gradle dependency verification (SHA-256 pins for the whole graph).
- Owner-key-only signing with a fingerprint assertion in CI.

**Phase-2 audit list (when the API lands)**

- TLS posture for the Academy API (system trust store only; no
  custom trust managers, no cleartext exceptions).
- Token storage: API credential material goes into SecureStore
  only — never plain preferences, never logs.
- Lesson-content rendering: the sanitized-subset rule in
  docs/API-CONTRACT.md Area 3 — no raw HTML reaches a renderer;
  the app has no WebView, and none is to be added.
- App-lock wiring: idle re-lock, lock on background, PIN attempt
  behaviour — built on the scaffolding, audited as its own change.
- Log audit: no tokens, no lesson content, no PII in any log line.
