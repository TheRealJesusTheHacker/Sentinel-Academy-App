# Academy App API Contract — seam document

**Status: ANSWERED AND BUILT (phase 2, 2026-11-10).** The server-side
contract of record is the Live side's
`ACADEMY-APP-API-CONTRACT-2026-11-10.md`, whose BUILD RECORD shows all
ten `/api/app/*` endpoints LIVE in production with the six reused
endpoints verified under bearer auth. This document remains as the
app-side seam record from phase 1; where its TODOs differ from the
landed contract, the landed contract governs. The phase-2 client is
`data/AcademyApi.kt`; its models (`data/Models.kt`) mirror the landed
payloads field for field — including the absences (no answer/explain
field exists for one to land in; no price/commerce key exists).

**Status at phase 1: AWAITING the server-side contract.** The Academy today is a
server-rendered web service with no app API. This document is the
phase-2 build's contract of record: it fixes the six areas the app
needs, records every fact already established from the Academy's
server codebase, and marks — with TODO — each point the server crew
(Live side) must answer before the phase-2 build wires that area.
**No endpoint in this document is invented.** Where a path or field
is not yet answered, it says TODO; the app ships no guessed wiring.

The app-side seams this contract feeds:

- `AcademyApplication` — where the phase-2 API client is created.
- `SecureStore` — where returned credential material is stored (the
  PIN-verifier half already exists; the credential half is shaped by
  Area 1 below, not before).
- `AppRoot` — the start screen is replaced section by section as
  each area lands.

Family conventions the contract answers are expected to honour
(matching the Command App's `/api/app/*` surface): JSON over HTTPS,
an explicit `available`/unavailable vocabulary rather than silent
empty success, and errors that say what happened in plain language.

---

## Area 1 — Authentication & account

What the app needs: how a student signs in with their Academy
account and what the app holds afterwards.

- TODO: credential exchange — endpoint, request/response shape.
- TODO: token model — bearer/session, lifetime, refresh behaviour,
  revocation on password change.
- TODO: account profile fields the app may display (name, email).
- TODO: account language flag — the Academy serves English and
  Spanish tracks; the app must render the account's language.
  KNOWN FACT (server behaviour): *Spanish UI rendering follows the
  enrollment language — when all of a student's enrollments are
  Spanish tracks, the Academy renders in Spanish.* Whether the API
  exposes that as an explicit account flag or the app derives it
  from enrollments is TODO.

## Area 2 — Catalog & enrollment

What the app needs: the track/lesson catalog with the signed-in
student's enrollment state.

- TODO: catalog endpoint(s) — tracks, lessons within a track,
  ordering, lesson kinds (reading / video / quiz / final).
- TODO: enrollment state per track (not enrolled / enrolled /
  completed) and how enrollment is requested from the app.
- REQUIRED AS DATA: each track carries a free/paid flag. The
  paywall is a later phase (owner sequencing, 2026-11-10): in
  phase 2 the flag is **display only** — the app shows it, nothing
  is sold, nothing is paywalled. The flag must arrive as data, not
  be inferred client-side.
- TODO: catalog language — which language a track's content is in,
  per track.

## Area 3 — Lesson content

What the app needs: lesson bodies the app renders natively.

- REQUIRED SHAPE: **structured content, not scraped HTML.** The app
  is a native mirror (family law: zero WebView surfaces). If lesson
  bodies are stored as HTML server-side, the contract must name the
  sanitized HTML subset the app will parse into native blocks —
  anything outside the subset is stripped, never rendered raw.
- TODO: the structured block vocabulary (headings, paragraphs,
  lists, code, callouts, images, video references).
- TODO: media URLs and their lifetime (signed or public).
- TODO: offline behaviour expectations, if any (the app caches
  nothing until the contract says what may be cached).

## Area 4 — Progress & quizzes

What the app needs: lesson completion and quiz semantics identical
to the web Academy — the app must never invent its own idea of
"done".

- KNOWN FACTS (server semantics, from the Academy codebase):
  - Lesson activity is recorded as `progress_lessons.done_at`.
  - A track's final is recorded as `track_state.final_passed_at`
    when `final_passed = 1`.
  - Quiz *best scores* are progress data, but a quiz best by
    itself is **not** streak activity (see Area 5).
- TODO: progress read endpoint — per-lesson done state, per-track
  completion, quiz bests and attempt counts.
- TODO: progress write semantics — how the app marks a lesson
  complete; idempotency; what the server accepts as proof of
  completion for each lesson kind.
- TODO: quiz payload — question/answer shapes, pass threshold,
  attempt limits, and where grading happens (server-side expected;
  the app never grades its own final).

## Area 5 — Study streaks

What the app needs: the streak numbers the Academy already computes.

KNOWN FACTS (server semantics, verified from the Academy codebase
by the Live crew, 2026-11-10):

- The server computes `_streak_stats(user_id)` →
  **(current streak, longest streak, lessons this week)**.
- **Activity** (what counts toward a streak day) =
  `progress_lessons.done_at` timestamps **plus**
  `track_state.final_passed_at` where `final_passed = 1`.
  Quiz bests never count.
- A **streak day is an America/Chicago calendar day**.
- The current streak **may end yesterday** — a student who studied
  yesterday and not yet today still holds their streak.
- `STREAK_WEEKLY_GOAL = 3`, framed by the Academy itself as
  **"a guide, not a requirement"** — the app's copy must keep that
  framing; the goal is never presented as an obligation or a
  failure state.

- TODO: the endpoint exposing `_streak_stats` (path, response
  field names, timezone handling) — the *semantics* above are
  settled; only the wire shape is open.
- TODO: whether lessons-this-week is the Monday-anchored Chicago
  week server-side (assumed; to be confirmed in the response).

## Area 6 — Certificates

What the app needs: the student's earned certificates, and public
verification of a certificate code.

KNOWN FACTS (verification contract, filed by the Live crew):

- Certificate codes match `^SA[A-Z0-9]{6,18}$`.
- Verification answers **identically for unknown codes** — the
  response never reveals whether a code exists-but-is-invalid or
  never existed.
- Verification is rate-limited to **60 lookups/hour/IP**.
- Production certificate count at filing time: **0** (no real
  certificates had been issued yet).

- TODO: wire shape of the verification lookup (endpoint, fields
  returned for a valid code — holder name, track, date).
- TODO: the signed-in student's own certificate list (endpoint and
  fields), and the share/verify presentation the app should use.

---

*Paywall note (owner sequencing, 2026-11-10): purchasing is NOT
part of this contract yet. When the paywall phase opens, it gets
its own contract area — prices, entitlement checks, and
restore/refund behaviour are server-answered questions, and the
free/paid flags in Area 2 are the seam it builds on.*
