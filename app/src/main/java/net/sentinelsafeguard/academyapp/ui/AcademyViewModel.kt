package net.sentinelsafeguard.academyapp.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import net.sentinelsafeguard.academyapp.data.AcademyApi
import net.sentinelsafeguard.academyapp.data.AccountSettings
import net.sentinelsafeguard.academyapp.data.ApiResult
import net.sentinelsafeguard.academyapp.data.Catalog
import net.sentinelsafeguard.academyapp.data.CatalogCache
import net.sentinelsafeguard.academyapp.data.CheckResult
import net.sentinelsafeguard.academyapp.data.IssueResult
import net.sentinelsafeguard.academyapp.data.LessonPayload
import net.sentinelsafeguard.academyapp.data.Me
import net.sentinelsafeguard.academyapp.data.Paper
import net.sentinelsafeguard.academyapp.data.ScoreResult
import net.sentinelsafeguard.academyapp.data.SecureStore
import net.sentinelsafeguard.academyapp.data.Streaks
import net.sentinelsafeguard.academyapp.data.VerifyResult
import net.sentinelsafeguard.academyapp.data.sessionExpired
import net.sentinelsafeguard.academyapp.data.withSettings

// ---------------------------------------------------------------------------
// AcademyViewModel — the app's state and every server round trip.
//
// Session law (contract §1.2/§1.3): the token is a server sessions
// row with a fixed 7-day TTL and NO sliding renewal. The app
// stores it in SecureStore, checks the stored expiry up front,
// and treats any 401 from an authenticated call as expiry: the
// session is cleared locally and the learner lands on the sign-in
// screen with an honest notice — never a dead screen, never a
// silent retry loop.
//
// Grading law (§3.3/§4.4/§4.5): the app never grades. Lesson
// checks and practice go to /api/app/check; papers to /api/score.
// Terminal exam outcomes (late, invalidated, cooldown) are data
// the UI presents, not errors it hides.
// ---------------------------------------------------------------------------

enum class AppPhase { BOOT, LOCKED, AUTH, PENDING_CONFIRM, MAIN }

class AcademyViewModel(app: Application) : AndroidViewModel(app) {

    val store: SecureStore = SecureStore(app)

    /** The anonymous catalog's validator cache (CATALOG-ETAG
     *  2026-11-11): public content only, integrity-pinned by its
     *  SHA-256 ETag. It is deliberately NOT session-scoped — it
     *  holds no account data, so it survives sign-out and speeds
     *  the next launch's auth backdrop; the per-user overlay
     *  catalog is never written to it. */
    private val api = AcademyApi(
        store,
        CatalogCache(java.io.File(app.filesDir, "catalog_cache")),
    )

    var phase by mutableStateOf(AppPhase.BOOT)
        private set

    var busy by mutableStateOf(false)
        private set

    /** One-line notice shown on the auth screen (e.g. session
     *  expired). Resource-key-free: set from string resources via
     *  the UI layer's pending-notice handshake below. */
    var authNotice by mutableStateOf<AuthNotice?>(null)

    enum class AuthNotice { SESSION_EXPIRED, SIGNED_OUT }

    var authError by mutableStateOf<String?>(null)
    var me by mutableStateOf<Me?>(null)
        private set
    var catalog by mutableStateOf<Catalog?>(null)
        private set
    var streaks by mutableStateOf<Streaks?>(null)
        private set
    var homeError by mutableStateOf<String?>(null)
        private set

    /** True when the last catalog fetch failed (audit 2026-11-11):
     *  the catalog legs used to fail silently, which left Home
     *  claiming "not enrolled in a track yet" and Tracks/Road
     *  spinning forever on a dead connection. Screens render an
     *  honest error + retry from this flag instead. */
    var catalogFailed by mutableStateOf(false)
        private set

    /** Set when a certificate is freshly issued; the certificates
     *  screen celebrates (confetti rule: post-issue surface only)
     *  and clears it. */
    var celebrateCode by mutableStateOf<String?>(null)

    init {
        boot()
    }

    private fun nowSec(): Long = System.currentTimeMillis() / 1000

    fun boot() {
        val token = store.sessionToken()
        if (token == null) {
            phase = AppPhase.AUTH
            // The anonymous catalog is public — load it so the
            // auth screen's backdrop of tracks is real data.
            loadCatalog()
            return
        }
        if (sessionExpired(store.sessionExpiresAt(), nowSec())) {
            store.clearSession()
            authNotice = AuthNotice.SESSION_EXPIRED
            phase = AppPhase.AUTH
            loadCatalog()
            return
        }
        // A stored session exists: the app lock (when a PIN is
        // set) guards it before anything account-shaped renders.
        phase = if (store.hasPin()) AppPhase.LOCKED else AppPhase.MAIN
        refreshAll()
    }

    fun unlock() {
        if (phase == AppPhase.LOCKED) phase = AppPhase.MAIN
    }

    /** Re-lock on return from background (activity ON_STOP calls
     *  this): only when a PIN exists AND a session is stored —
     *  the lock protects the session, nothing else. */
    fun relockIfArmed() {
        if (phase == AppPhase.MAIN && store.hasPin() && store.sessionToken() != null) {
            phase = AppPhase.LOCKED
        }
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            when (val r = api.catalog()) {
                is ApiResult.Ok -> {
                    catalog = r.value
                    catalogFailed = false
                }
                is ApiResult.Err -> catalogFailed = true
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            busy = true
            when (val r = api.me()) {
                is ApiResult.Ok -> {
                    me = r.value
                    homeError = null
                }
                is ApiResult.Err -> {
                    if (r.status == 401) return@launch expireSession()
                    // A transport failure carries no server
                    // message; surface the network sentinel rather
                    // than failing invisibly (audit 2026-11-11).
                    homeError = r.serverMessage ?: NETWORK_ERROR
                }
            }
            when (val r = api.catalog()) {
                is ApiResult.Ok -> {
                    catalog = r.value
                    catalogFailed = false
                }
                is ApiResult.Err -> catalogFailed = true
            }
            (api.streaks() as? ApiResult.Ok)?.let { streaks = it.value }
            busy = false
        }
    }

    /** Session-scoped state must not outlive the session (audit
     *  2026-11-11): a pending celebration, a verify result, an
     *  unclaimed issue, a live paper or the previous account's
     *  overlay catalog leaking into the NEXT account's session on
     *  the same process would show one learner another's state. */
    private fun clearSessionScopedState() {
        celebrateCode = null
        clearVerify()
        clearIssue()
        clearExam()
        cooldownSeconds = 0
        homeError = null
        catalogFailed = false
        catalog = null
    }

    private fun expireSession() {
        store.clearSession()
        me = null
        streaks = null
        clearSessionScopedState()
        authNotice = AuthNotice.SESSION_EXPIRED
        phase = AppPhase.AUTH
        busy = false
        loadCatalog()
    }

    // ---------------- Auth ----------------

    fun login(email: String, password: String) {
        viewModelScope.launch {
            busy = true; authError = null
            when (val r = api.login(email.trim(), password)) {
                is ApiResult.Ok -> {
                    val s = r.value
                    if (s.pendingConfirmation || s.token == null) {
                        phase = AppPhase.PENDING_CONFIRM
                    } else {
                        store.saveSession(s.token, s.expiresAt)
                        authNotice = null
                        phase = AppPhase.MAIN
                        refreshAll()
                    }
                }
                is ApiResult.Err -> authError = r.serverMessage ?: NETWORK_ERROR
            }
            busy = false
        }
    }

    fun signup(email: String, password: String, displayName: String, track: String, promo: String?) {
        viewModelScope.launch {
            busy = true; authError = null
            when (val r = api.signup(email.trim(), password, displayName.trim(), track, promo)) {
                is ApiResult.Ok -> {
                    val s = r.value
                    if (r.status == 202 || s.pendingConfirmation || s.token == null) {
                        // Armed email-confirmation mode: success
                        // and duplicate answer identically (202, no
                        // token) — the app says "check your email"
                        // and claims nothing more.
                        phase = AppPhase.PENDING_CONFIRM
                    } else {
                        store.saveSession(s.token, s.expiresAt)
                        authNotice = null
                        phase = AppPhase.MAIN
                        refreshAll()
                    }
                }
                is ApiResult.Err -> authError = when (r.reason) {
                    "password_short" -> PASSWORD_SHORT
                    "password_long" -> PASSWORD_LONG
                    "duplicate_email" -> DUPLICATE_EMAIL
                    else -> r.serverMessage ?: NETWORK_ERROR
                }
            }
            busy = false
        }
    }

    fun backToAuth() {
        phase = AppPhase.AUTH
        authError = null
    }

    fun logout() {
        viewModelScope.launch {
            busy = true
            // Server revokes ONLY this token; the local copy goes
            // regardless of the round trip's outcome.
            runCatching { api.logout() }
            store.clearSession()
            me = null; streaks = null
            clearSessionScopedState()
            authNotice = AuthNotice.SIGNED_OUT
            phase = AppPhase.AUTH
            busy = false
            loadCatalog()
        }
    }

    // ---------------- Catalog actions ----------------

    var actionMessage by mutableStateOf<String?>(null)

    fun enroll(track: String) {
        viewModelScope.launch {
            busy = true
            when (val r = api.enroll(track)) {
                is ApiResult.Ok -> { actionMessage = null; refreshAll() }
                is ApiResult.Err -> {
                    if (r.status == 401) return@launch expireSession()
                    actionMessage = r.serverMessage ?: NETWORK_ERROR
                }
            }
            busy = false
        }
    }

    fun applyPromo(code: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val r = api.promo(code.trim())) {
                is ApiResult.Ok -> { refreshAll(); onResult(true, null) }
                is ApiResult.Err -> {
                    if (r.status == 401) return@launch expireSession()
                    onResult(false, r.serverMessage)
                }
            }
        }
    }

    // ---------------- Account settings (G4 twins) ----------------

    var settingsBusy by mutableStateOf(false)
        private set

    /** A confirmed mutation carries the caller's FRESH settings;
     *  fold them into `me` so every surface renders the server's
     *  read-back (a stripped name shows stripped), not the values
     *  the app sent. */
    private fun applySettings(s: AccountSettings) {
        val m = me ?: return
        me = m.copy(user = m.user.withSettings(s))
    }

    private fun settingsCall(
        block: suspend () -> ApiResult<AccountSettings>,
        onResult: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            settingsBusy = true
            when (val r = block()) {
                is ApiResult.Ok -> { applySettings(r.value); onResult(true, null) }
                is ApiResult.Err -> {
                    if (r.status == 401) return@launch expireSession()
                    // The server's own words, verbatim — including
                    // a transport failure's null, which the UI
                    // maps to the network sentence; never silence.
                    onResult(false, r.serverMessage ?: NETWORK_ERROR)
                }
            }
            settingsBusy = false
        }
    }

    fun saveProfile(displayName: String, certName: String, onResult: (Boolean, String?) -> Unit) =
        settingsCall({ api.updateProfile(displayName, certName) }, onResult)

    fun saveSocial(registry: Boolean, leaderboard: Boolean, onResult: (Boolean, String?) -> Unit) =
        settingsCall({ api.updateSocial(registry, leaderboard) }, onResult)

    fun saveNotifications(certAnniversary: Boolean, onResult: (Boolean, String?) -> Unit) =
        settingsCall({ api.updateNotifications(certAnniversary) }, onResult)

    /** Password change (SETTINGS-TWINS §4): refusals carry the web
     *  flow's exact messages — surfaced verbatim through
     *  [onResult]'s message. On success the server has signed out
     *  every OTHER session; this device's token survives by
     *  design, so nothing local changes — the UI's success copy
     *  states that plainly. */
    fun changePassword(current: String, new: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            settingsBusy = true
            when (val r = api.changePassword(current, new)) {
                is ApiResult.Ok -> onResult(true, null)
                is ApiResult.Err -> {
                    if (r.status == 401) return@launch expireSession()
                    onResult(false, r.serverMessage ?: NETWORK_ERROR)
                }
            }
            settingsBusy = false
        }
    }

    // ---------------- Lesson ----------------

    var lesson by mutableStateOf<LessonPayload?>(null)
        private set
    var lessonError by mutableStateOf<String?>(null)
        private set
    var lessonLoading by mutableStateOf(false)
        private set

    fun openLesson(track: String, course: String, lessonSlug: String) {
        viewModelScope.launch {
            lessonLoading = true; lessonError = null; lesson = null
            when (val r = api.lesson(track, course, lessonSlug)) {
                is ApiResult.Ok -> lesson = r.value
                is ApiResult.Err -> {
                    if (r.status == 401) return@launch expireSession()
                    lessonError = r.serverMessage ?: NETWORK_ERROR
                }
            }
            lessonLoading = false
        }
    }

    /** Grade via the server. onResult receives the CheckResult;
     *  a correct lesson check has already recorded completion
     *  server-side — the app then refreshes /api/me. */
    fun submitCheck(practiceIndex: Int?, answer: Int, onResult: (CheckResult?) -> Unit) {
        val l = lesson ?: return onResult(null)
        viewModelScope.launch {
            when (val r = api.check(l.track, l.courseSlug, lSlugOf(l), answer, practiceIndex)) {
                is ApiResult.Ok -> {
                    if (practiceIndex == null && r.value.correct) refreshAll()
                    onResult(r.value)
                }
                is ApiResult.Err -> {
                    if (r.status == 401) expireSession()
                    onResult(null)
                }
            }
        }
    }

    private fun lSlugOf(l: LessonPayload): String = l.id.substringAfterLast("--")

    // ---------------- Exams ----------------

    var paper by mutableStateOf<Paper?>(null)
        private set
    var paperStartedAtMs by mutableStateOf(0L)
        private set
    var examError by mutableStateOf<String?>(null)
        private set
    var scoreResult by mutableStateOf<ScoreResult?>(null)
        private set
    var examBlurs by mutableStateOf(0)
        private set
    var cooldownSeconds by mutableStateOf(0)
        private set

    /** The scored paper and the answers submitted with it,
     *  retained so the result screen can render the full quiz
     *  review (question text, your answer, the correct answer —
     *  audit 2026-11-11, capability gap: the payloads always
     *  carried everything needed; the app threw the paper away
     *  at score time). The server's review still reveals the
     *  correct positions only AFTER grading; nothing here grades
     *  or pre-reveals. Cleared with the exam. */
    var reviewPaper by mutableStateOf<Paper?>(null)
        private set
    var reviewAnswers by mutableStateOf<List<Int>>(emptyList())
        private set

    fun startExam(track: String, kind: String, course: String?, mode: String?) {
        viewModelScope.launch {
            busy = true; examError = null; scoreResult = null
            reviewPaper = null; reviewAnswers = emptyList()
            when (val r = api.paper(track, kind, course, mode)) {
                is ApiResult.Ok -> {
                    paper = r.value
                    paperStartedAtMs = System.currentTimeMillis()
                    examBlurs = 0
                }
                is ApiResult.Err -> {
                    if (r.status == 401) return@launch expireSession()
                    examError = r.serverMessage ?: NETWORK_ERROR
                    if (kind == "final") refreshCooldown(track)
                }
            }
            busy = false
        }
    }

    fun refreshCooldown(track: String) {
        viewModelScope.launch {
            (api.status(track) as? ApiResult.Ok)?.let { cooldownSeconds = it.value.cooldownSeconds }
        }
    }

    /** The honest blurs count: every time the app leaves the
     *  foreground during a live paper, the count rises by one and
     *  is submitted as-is (contract §4.4 — the app sends it
     *  honestly; the server decides what it means). */
    fun noteBackgrounded() {
        if (paper != null && scoreResult == null) examBlurs += 1
    }

    fun submitExam(answers: List<Int>) {
        val p = paper ?: return
        viewModelScope.launch {
            busy = true; examError = null
            when (val r = api.score(p.paper, answers, examBlurs)) {
                is ApiResult.Ok -> {
                    scoreResult = r.value
                    reviewPaper = p
                    reviewAnswers = answers
                    paper = null
                    refreshAll()
                }
                is ApiResult.Err -> {
                    if (r.status == 401) return@launch expireSession()
                    // The paper stays on screen with the answers
                    // intact; PaperView renders this error above
                    // the submit button (audit 2026-11-11 — a
                    // failed score used to vanish silently).
                    examError = r.serverMessage ?: NETWORK_ERROR
                }
            }
            busy = false
        }
    }

    fun clearExam() {
        paper = null; scoreResult = null; examError = null
        reviewPaper = null; reviewAnswers = emptyList()
    }

    // ---------------- Certificates ----------------

    var issueResult by mutableStateOf<IssueResult?>(null)
        private set
    var issueError by mutableStateOf<String?>(null)
        private set

    fun issueCertificate(track: String, passToken: String) {
        viewModelScope.launch {
            busy = true; issueError = null
            when (val r = api.issue(track, passToken)) {
                is ApiResult.Ok -> {
                    issueResult = r.value
                    celebrateCode = r.value.code
                    refreshAll()
                }
                is ApiResult.Err -> {
                    if (r.status == 401) return@launch expireSession()
                    issueError = r.serverMessage ?: NETWORK_ERROR
                }
            }
            busy = false
        }
    }

    fun clearIssue() {
        issueResult = null; issueError = null
    }

    var verifyResult by mutableStateOf<VerifyResult?>(null)
        private set
    var verifyBusy by mutableStateOf(false)
        private set

    fun verify(code: String) {
        viewModelScope.launch {
            verifyBusy = true; verifyResult = null
            when (val r = api.verify(code)) {
                is ApiResult.Ok -> verifyResult = r.value
                is ApiResult.Err -> verifyResult = VerifyResult(
                    // NOT "not_found" (audit 2026-11-11): a 429
                    // means the shared budget was spent BEFORE
                    // any lookup ran, and a transport failure
                    // likewise performed none. Claiming "no
                    // certificate matches that code" for a lookup
                    // that never happened would be a false
                    // statement; the UI presents these as their
                    // own states instead.
                    found = false, status = "error", valid = false,
                    lookupError = if (r.status == 429) "rate" else "net",
                )
            }
            verifyBusy = false
        }
    }

    fun clearVerify() {
        verifyResult = null
    }

    companion object {
        // Sentinel messages the UI maps to string resources; the
        // server's own words are preferred whenever it sent any.
        const val NETWORK_ERROR = "NETWORK_ERROR"
        const val PASSWORD_SHORT = "PASSWORD_SHORT"
        const val PASSWORD_LONG = "PASSWORD_LONG"
        const val DUPLICATE_EMAIL = "DUPLICATE_EMAIL"
    }
}
