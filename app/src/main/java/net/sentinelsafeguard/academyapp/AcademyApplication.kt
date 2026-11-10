package net.sentinelsafeguard.academyapp

import android.app.Application

// Process root. Phase 1 (foundation) has nothing to initialise:
// no API client exists until the phase-2 contract lands, and no
// background work runs. The class exists so the manifest names a
// stable application entry point the later phases extend — the
// phase-2 seam is here and in docs/API-CONTRACT.md, not scattered
// through the UI.
class AcademyApplication : Application()
