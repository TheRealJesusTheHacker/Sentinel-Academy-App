package net.sentinelsafeguard.academyapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ---------------------------------------------------------------------------
// AcademyApi — the native client for the Academy App API
// (contract of record: ACADEMY-APP-API-CONTRACT-2026-11-10.md,
// BUILD RECORD: all ten /api/app/* endpoints LIVE in production;
// the six reused endpoints verified under bearer auth).
//
// Transport facts, all deliberate:
// - HTTPS only, to the one host the service already publishes.
//   No third-party SDK, no analytics, no retry-into-amplification:
//   one attempt per call, outcomes surfaced honestly.
// - The session token rides as `Authorization: Bearer <token>` —
//   the server's session_user() bearer fallback resolves it
//   through the UNCHANGED resolve_session (the token IS a
//   sessions row; §1.3).
// - The stable per-install device id rides as the `sa-bid`
//   cookie on EVERY call (contract §7, HARD requirement): paper
//   issue/score bind to it, and the paper-exam-b rate bucket keys
//   on it. Without it all app users would share one exam
//   identity. The id comes from SecureStore (generated once).
// - Lesson payloads are parsed into models that have no answer /
//   explain field — even a hypothetical server regression could
//   not put a key in the app's hands through this client, because
//   there is nowhere here for one to land.
// - The ANONYMOUS catalog is revalidated, not re-downloaded
//   (CATALOG-ETAG 2026-11-11): it is stored with its strong ETag
//   (SHA-256 of the body) in CatalogCache, refreshed with
//   If-None-Match, and a 304 keeps the stored copy. The
//   authenticated (overlay) catalog is no-store by server design
//   and is never cached.
// ---------------------------------------------------------------------------

sealed class ApiResult<out T> {
    data class Ok<T>(val value: T, val status: Int) : ApiResult<T>()
    data class Err(
        val status: Int,
        /** The server's own message/error text when it sent one. */
        val serverMessage: String?,
        /** Machine reason code when the server sent one (signup). */
        val reason: String? = null,
        val body: JSONObject? = null,
    ) : ApiResult<Nothing>()
}

class AcademyApi(
    private val store: SecureStore,
    private val catalogCache: CatalogCache? = null,
    private val baseUrl: String = BASE_URL,
) {
    private data class Raw(
        val status: Int,
        val json: JSONObject?,
        /** Response ETag header, when the server sent one. */
        val etag: String? = null,
        /** The decoded response body, exactly as received. */
        val text: String = "",
    )

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        auth: Boolean = true,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Raw {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Accept", "application/json")
            // gzip when the edge offers it (audit 2026-11-11):
            // the catalog payload is ~243 KB of JSON on every
            // refresh; negotiated compression cuts it to a
            // fraction on the wire. Identity responses are
            // untouched — the stream is wrapped only when the
            // response actually declares gzip.
            conn.setRequestProperty("Accept-Encoding", "gzip")
            // An honest, stable client identity (the app names
            // itself; server logs and edge rules can tell the app
            // from a browser at a glance).
            conn.setRequestProperty(
                "User-Agent",
                "SentinelAcademyApp/" + net.sentinelsafeguard.academyapp.BuildConfig.VERSION_NAME + " (Android)",
            )
            // §7 device identity — on every call, no exceptions.
            conn.setRequestProperty("Cookie", "sa-bid=" + store.deviceId())
            for ((name, value) in extraHeaders) {
                conn.setRequestProperty(name, value)
            }
            if (auth) {
                store.sessionToken()?.let {
                    conn.setRequestProperty("Authorization", "Bearer $it")
                }
            }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val rawStream = if (status in 200..299) conn.inputStream else conn.errorStream
            val stream = if (rawStream != null &&
                conn.contentEncoding?.contains("gzip", ignoreCase = true) == true
            ) {
                java.util.zip.GZIPInputStream(rawStream)
            } else {
                rawStream
            }
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            return Raw(status, json, etag = conn.getHeaderField("ETag"), text = text)
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun <T> call(
        method: String,
        path: String,
        body: JSONObject? = null,
        auth: Boolean = true,
        parse: (JSONObject) -> T,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        // One retry for a GET whose transport dropped mid-body
        // (observed once against the ~244 KB catalog through the
        // tunnel); writes are never retried — a dropped write is
        // surfaced, not repeated blind.
        val raw = try {
            request(method, path, body, auth)
        } catch (e: Exception) {
            if (method == "GET") {
                try {
                    request(method, path, body, auth)
                } catch (e2: Exception) {
                    return@withContext ApiResult.Err(0, null)
                }
            } else {
                return@withContext ApiResult.Err(0, null)
            }
        }
        val json = raw.json
        if (raw.status in 200..299 && json != null) {
            runCatching { ApiResult.Ok(parse(json), raw.status) }
                .getOrElse { ApiResult.Err(raw.status, null, body = json) }
        } else {
            ApiResult.Err(
                status = raw.status,
                serverMessage = json?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: json?.optString("error")?.takeIf { it.isNotBlank() },
                reason = json?.optString("reason")?.takeIf { it.isNotBlank() },
                body = json,
            )
        }
    }

    // ---------------- Auth (§1.3) ----------------

    /** 201 + session payload unarmed; 202 {pending_confirmation}
     *  armed (no token, and duplicate answers identically). The
     *  caller distinguishes by status. */
    suspend fun signup(
        email: String, password: String, displayName: String, track: String, promo: String?,
    ): ApiResult<SessionPayload> = call("POST", "/api/app/signup", JSONObject().apply {
        put("email", email); put("password", password)
        put("display_name", displayName); put("track", track)
        if (!promo.isNullOrBlank()) put("promo", promo)
    }, auth = false) { parseSession(it) }

    suspend fun login(email: String, password: String): ApiResult<SessionPayload> =
        call("POST", "/api/app/login", JSONObject().apply {
            put("email", email); put("password", password)
        }, auth = false) { parseSession(it) }

    /** Revokes ONLY the presented token (server semantics). */
    suspend fun logout(): ApiResult<Boolean> =
        call("POST", "/api/app/logout", JSONObject()) { true }

    suspend fun enroll(track: String): ApiResult<Boolean> =
        call("POST", "/api/app/enroll", JSONObject().put("track", track)) { true }

    suspend fun promo(code: String): ApiResult<JSONObject> =
        call("POST", "/api/app/promo", JSONObject().put("promo", code)) { it }

    // ---------------- Account settings (G4 twins,
    // SETTINGS-TWINS 2026-11-11) ----------------
    //
    // Every mutation answers 200 {"ok": true, "settings": {...}}
    // with the caller's FRESH settings — the parse below reads
    // that object, so the UI renders the server's confirmed state,
    // never the values it sent. Refusals are 400s carrying the web
    // flow's exact message in "error" plus a machine "reason";
    // `call` already surfaces both (serverMessage / reason), and
    // the ViewModel shows the server's words verbatim.
    // The password twin is the one mutation with NO settings
    // payload: 200 {"ok": true}, and on success every OTHER
    // session on the account is revoked server-side while the
    // presented token survives — this client's stored token is
    // therefore deliberately left untouched by a password change.

    suspend fun updateProfile(displayName: String, certName: String): ApiResult<AccountSettings> {
        val p = profilePayload(displayName, certName)
        return call("POST", "/api/app/profile", JSONObject().apply {
            for ((k, v) in p) put(k, v)
        }) { parseSettings(it) }
    }

    suspend fun updateSocial(registry: Boolean, leaderboard: Boolean): ApiResult<AccountSettings> {
        val p = socialPayload(registry, leaderboard)
        return call("POST", "/api/app/social", JSONObject().apply {
            for ((k, v) in p) put(k, v)
        }) { parseSettings(it) }
    }

    suspend fun updateNotifications(certAnniversary: Boolean): ApiResult<AccountSettings> {
        val p = notificationsPayload(certAnniversary)
        return call("POST", "/api/app/notifications", JSONObject().apply {
            for ((k, v) in p) put(k, v)
        }) { parseSettings(it) }
    }

    suspend fun changePassword(current: String, new: String): ApiResult<Boolean> =
        call("POST", "/api/app/password", JSONObject().apply {
            put("current", current); put("new", new)
        }) { it.optBoolean("ok", false) }

    private fun parseSettings(json: JSONObject): AccountSettings {
        val s = json.getJSONObject("settings")
        return accountSettingsFromMap(
            mapOf(
                "display_name" to s.optString("display_name"),
                "cert_name" to s.optString("cert_name"),
                "registry" to s.optBoolean("registry", false),
                "leaderboard" to s.optBoolean("leaderboard", false),
                "notify_cert_anniversary" to s.optBoolean("notify_cert_anniversary", false),
            ),
        )
    }

    data class SessionPayload(val token: String?, val expiresAt: Long, val pendingConfirmation: Boolean)

    private fun parseSession(json: JSONObject): SessionPayload = SessionPayload(
        token = json.optString("token").takeIf { it.isNotBlank() },
        expiresAt = json.optLong("expires_at", 0L),
        pendingConfirmation = json.optBoolean("pending_confirmation", false),
    )

    // ---------------- Reads ----------------

    suspend fun me(): ApiResult<Me> = call("GET", "/api/me") { parseMe(it) }

    /** The catalog, in its two server shapes (CATALOG-ETAG
     *  2026-11-11):
     *  - With a stored token, the session overlay rides along
     *    (enrolled / state / done). That response is per-user,
     *    no-store, and carries no validator — it is fetched fresh
     *    every time and never cached, exactly as before.
     *  - Without one, the public catalog answers under a strong
     *    ETag: the stored copy is revalidated with If-None-Match,
     *    a 304 keeps it (saving the ~244 KB body per unchanged
     *    launch), and a transport failure falls back to it (the
     *    offline case). A 200 replaces the stored copy under its
     *    new ETag. */
    suspend fun catalog(): ApiResult<Catalog> {
        if (store.sessionToken() != null) {
            return call("GET", "/api/app/catalog", auth = true) { parseCatalog(it) }
        }
        return anonymousCatalog()
    }

    /** One anonymous catalog GET with the transport's standing
     *  rule (a dropped GET is retried once; never more). A null
     *  return means the transport failed outright — status 0 in
     *  this client's vocabulary. */
    private fun getRaw(path: String, extraHeaders: Map<String, String>): Raw? =
        try {
            request("GET", path, null, auth = false, extraHeaders = extraHeaders)
        } catch (e: Exception) {
            try {
                request("GET", path, null, auth = false, extraHeaders = extraHeaders)
            } catch (e2: Exception) {
                null
            }
        }

    private suspend fun anonymousCatalog(): ApiResult<Catalog> = withContext(Dispatchers.IO) {
        val stored = catalogCache?.load()
        val headers = stored?.let { mapOf("If-None-Match" to it.etag) } ?: emptyMap()
        val raw = getRaw("/api/app/catalog", headers)
        val status = raw?.status ?: 0
        if (catalogUseStored(status, stored != null)) {
            // 304: the server confirms the stored bytes are
            // current. Status 0: no answer at all — the last
            // server-confirmed copy is the honest offline answer.
            val parsed = runCatching { parseCatalog(JSONObject(stored!!.body)) }.getOrNull()
            if (parsed != null) return@withContext ApiResult.Ok(parsed, status)
            // An integrity-verified copy that no longer parses
            // is not served; fall through and report the fetch.
        }
        if (raw != null && raw.status in 200..299 && raw.json != null) {
            // Cache only what the server validated: a 200 with
            // an ETag is stored under it; an unvalidated body is
            // used for this answer and not kept.
            val etag = raw.etag
            if (etag != null && catalogCache != null) {
                runCatching { catalogCache.store(etag, raw.text) }
            }
            return@withContext runCatching { ApiResult.Ok(parseCatalog(raw.json), raw.status) }
                .getOrElse { ApiResult.Err(raw.status, null, body = raw.json) }
        }
        ApiResult.Err(
            status = status,
            serverMessage = raw?.json?.optString("message")?.takeIf { it.isNotBlank() }
                ?: raw?.json?.optString("error")?.takeIf { it.isNotBlank() },
            reason = raw?.json?.optString("reason")?.takeIf { it.isNotBlank() },
            body = raw?.json,
        )
    }

    suspend fun lesson(track: String, course: String, lesson: String): ApiResult<LessonPayload> {
        val q = "track=${enc(track)}&course=${enc(course)}&lesson=${enc(lesson)}"
        return call("GET", "/api/app/lesson?$q") { parseLesson(it) }
    }

    suspend fun streaks(): ApiResult<Streaks> = call("GET", "/api/app/streaks") {
        Streaks(
            current = it.getInt("current"),
            longest = it.getInt("longest"),
            lessonsThisWeek = it.getInt("lessons_this_week"),
            weeklyGoal = it.getInt("weekly_goal"),
        )
    }

    suspend fun status(track: String): ApiResult<ExamStatus> =
        call("GET", "/api/status?track=${enc(track)}") {
            ExamStatus(it.optInt("attempts", 0), it.optInt("cooldown_seconds", 0))
        }

    /** The app's ONLY verify surface — never the employer
     *  /api/verify (contract §6.3). Shares the web face's
     *  60/hr/IP budget, spent server-side before lookup. */
    suspend fun verify(code: String): ApiResult<VerifyResult> =
        call("GET", "/api/app/verify?code=${enc(code)}", auth = false) {
            VerifyResult(
                found = it.optBoolean("found", false),
                name = it.optString("name").takeIf { s -> s.isNotBlank() },
                track = it.optString("track").takeIf { s -> s.isNotBlank() },
                trackName = it.optString("track_name").takeIf { s -> s.isNotBlank() },
                certificate = it.optString("certificate").takeIf { s -> s.isNotBlank() },
                issued = it.optString("issued").takeIf { s -> s.isNotBlank() },
                status = it.optString("status", "not_found"),
                valid = it.optBoolean("valid", false),
            )
        }

    // ---------------- Learning writes ----------------

    /** Server-side grading (§4.5). Lesson check: practiceIndex
     *  null. Practice: index set, never records progress. */
    suspend fun check(
        track: String, course: String, lesson: String, answer: Int, practiceIndex: Int?,
    ): ApiResult<CheckResult> = call("POST", "/api/app/check", JSONObject().apply {
        put("track", track); put("course", course); put("lesson", lesson); put("answer", answer)
        if (practiceIndex != null) put("practice", practiceIndex)
    }) {
        CheckResult(
            correct = it.optBoolean("correct", false),
            explain = it.optString("explain").takeIf { s -> s.isNotBlank() },
        )
    }

    suspend fun progressLesson(track: String, course: String, lesson: String): ApiResult<Boolean> =
        call("POST", "/api/progress/lesson", JSONObject().apply {
            put("track", track); put("course", course); put("lesson", lesson)
        }) { true }

    // ---------------- Exams (§4.3/§4.4, reused as-is) ----------------

    suspend fun paper(track: String, kind: String, course: String?, mode: String?): ApiResult<Paper> =
        call("POST", "/api/paper", JSONObject().apply {
            put("track", track); put("kind", kind)
            if (course != null) put("course", course)
            if (mode != null) put("mode", mode)
        }) { parsePaper(it) }

    suspend fun score(paper: String, answers: List<Int>, blurs: Int): ApiResult<ScoreResult> =
        call("POST", "/api/score", JSONObject().apply {
            put("paper", paper)
            put("answers", JSONArray(answers))
            put("blurs", blurs)
        }) { parseScore(it) }

    suspend fun issue(track: String, token: String): ApiResult<IssueResult> =
        call("POST", "/api/issue", JSONObject().apply {
            put("track", track); put("token", token)
        }) {
            IssueResult(
                code = it.getString("code"),
                url = it.optString("url"),
                status = it.optString("status", "valid"),
            )
        }

    // ---------------- Parsers ----------------

    private fun parseMe(json: JSONObject): Me {
        val u = json.getJSONObject("user")
        val quizzes = mutableMapOf<String, QuizBest>()
        json.optJSONObject("quizzes")?.let { q ->
            for (key in q.keys()) {
                val v = q.getJSONObject(key)
                quizzes[key] = QuizBest(v.optInt("pct", 0), v.optBoolean("passed", false))
            }
        }
        val tracks = mutableMapOf<String, TrackProgress>()
        json.optJSONObject("tracks")?.let { t ->
            for (key in t.keys()) {
                val v = t.getJSONObject(key)
                tracks[key] = TrackProgress(
                    finalPassed = v.optBoolean("final_passed", false),
                    finalPct = if (v.has("final_pct") && !v.isNull("final_pct")) v.optInt("final_pct") else null,
                    practicePct = if (v.has("practice_pct") && !v.isNull("practice_pct")) v.optInt("practice_pct") else null,
                )
            }
        }
        val certs = mutableMapOf<String, String>()
        json.optJSONObject("certs")?.let { c -> for (key in c.keys()) certs[key] = c.optString(key) }
        return Me(
            user = UserInfo(
                displayName = u.optString("display_name"),
                certName = u.optString("cert_name"),
                email = u.optString("email"),
                promo = u.optBoolean("promo", false),
                registry = u.optBoolean("registry", false),
                leaderboard = u.optBoolean("leaderboard", false),
                notifyCertAnniversary = u.optBoolean("notify_cert_anniversary", false),
            ),
            enrolled = json.optJSONArray("enrolled").toStringList(),
            imported = json.optBoolean("imported", false),
            lessons = json.optJSONArray("lessons").toStringList().toSet(),
            quizzes = quizzes,
            tracks = tracks,
            certs = certs,
            certList = json.optJSONArray("cert_list").toCertList(),
        )
    }

    private fun parseCatalog(json: JSONObject): Catalog {
        val tracksJson = json.getJSONArray("tracks")
        var overlay = false
        val tracks = (0 until tracksJson.length()).map { i ->
            val t = tracksJson.getJSONObject(i)
            val enrolled = if (t.has("enrolled") && !t.isNull("enrolled")) {
                overlay = true; t.optBoolean("enrolled")
            } else null
            val coursesJson = t.optJSONArray("courses") ?: JSONArray()
            val courses = (0 until coursesJson.length()).map { ci ->
                val c = coursesJson.getJSONObject(ci)
                val modulesJson = c.optJSONArray("modules") ?: JSONArray()
                val modules = (0 until modulesJson.length()).map { mi ->
                    val m = modulesJson.getJSONObject(mi)
                    val lessonsJson = m.optJSONArray("lessons") ?: JSONArray()
                    Module(
                        title = m.optString("title"),
                        lessons = (0 until lessonsJson.length()).map { li ->
                            val l = lessonsJson.getJSONObject(li)
                            LessonRef(
                                slug = l.optString("slug"),
                                id = l.optString("id"),
                                title = l.optString("title"),
                                minutes = l.optInt("minutes", 0),
                                done = if (l.has("done") && !l.isNull("done")) l.optBoolean("done") else null,
                            )
                        },
                    )
                }
                Course(
                    slug = c.optString("slug"),
                    title = c.optString("title"),
                    tagline = c.optString("tagline"),
                    summary = c.optString("summary"),
                    certLabel = c.optString("cert_label"),
                    difficulty = c.optString("difficulty"),
                    estMinutes = c.optInt("est_minutes", 0),
                    elective = c.optBoolean("elective", false),
                    lessonCount = c.optInt("lesson_count", modules.sumOf { it.lessons.size }),
                    quiz = c.optJSONObject("quiz")?.let {
                        QuizInfo(it.optInt("draw"), it.optInt("pass_pct"), it.optInt("pool"))
                    },
                    state = c.optString("state").takeIf { it.isNotBlank() },
                    modules = modules,
                )
            }
            Track(
                slug = t.getString("slug"),
                lang = t.optString("lang", "en"),
                name = t.optString("name"),
                short = t.optString("short"),
                kicker = t.optString("kicker"),
                pitch = t.optString("pitch"),
                pitchLong = t.optString("pitch_long"),
                certName = t.optString("cert_name"),
                access = t.optString("access", "free"),
                enrolled = enrolled,
                final = t.optJSONObject("final")?.let {
                    FinalInfo(
                        bankQuestions = it.optInt("bank_questions"),
                        draw = it.optInt("draw"),
                        minutes = it.optInt("minutes"),
                        passPct = it.optInt("pass_pct"),
                    )
                },
                courses = courses,
            )
        }
        return Catalog(tracks, overlay)
    }

    private fun parseLesson(json: JSONObject): LessonPayload {
        val blocksJson = json.getJSONArray("blocks")
        val blocks = (0 until blocksJson.length()).map { i ->
            val b = blocksJson.getJSONObject(i)
            when (b.optString("t")) {
                "p" -> Block.Paragraph(b.optString("x"))
                "h" -> Block.Heading(b.optString("x"))
                "list" -> Block.BulletList(b.optJSONArray("items").toStringList())
                "steps" -> Block.Steps(b.optJSONArray("items").toStringList())
                "callout" -> Block.Callout(b.optString("kind"), b.optString("x"))
                "exercise" -> Block.Exercise(b.optString("x"), b.optJSONArray("items").toStringList())
                else -> Block.Unknown(b.optString("t"))
            }
        }
        val course = json.getJSONObject("course")
        fun nav(key: String): LessonNav? = json.optJSONObject(key)?.let {
            LessonNav(it.optString("course"), it.optString("lesson"))
        }
        fun question(o: JSONObject) = CheckQ(o.getString("q"), o.getJSONArray("choices").toStringList())
        return LessonPayload(
            id = json.getString("id"),
            track = json.getString("track"),
            lang = json.optString("lang", "en"),
            courseSlug = course.optString("slug"),
            courseTitle = course.optString("title"),
            moduleTitle = json.optString("module_title").takeIf { it.isNotBlank() },
            title = json.getString("title"),
            intro = json.optString("intro"),
            minutes = json.optInt("minutes", 0),
            blocks = blocks,
            check = question(json.getJSONObject("check")),
            practice = json.optJSONArray("practice")?.let { arr ->
                (0 until arr.length()).map { question(arr.getJSONObject(it)) }
            } ?: emptyList(),
            prev = nav("prev"),
            next = nav("next"),
            done = json.optBoolean("done", false),
        )
    }

    private fun parsePaper(json: JSONObject): Paper {
        val qs = json.getJSONArray("questions")
        return Paper(
            paper = json.getString("paper"),
            code = json.optString("code"),
            kind = json.optString("kind"),
            track = json.optString("track"),
            course = json.optString("course").takeIf { it.isNotBlank() },
            mode = json.optString("mode").takeIf { it.isNotBlank() },
            attemptNo = json.optInt("attempt_no", 1),
            total = json.optInt("total", qs.length()),
            limitSeconds = json.optInt("limit_seconds", 0),
            expiresAt = json.optLong("expires_at", 0L),
            questions = (0 until qs.length()).map { i ->
                val q = qs.getJSONObject(i)
                CheckQ(q.getString("q"), q.getJSONArray("choices").toStringList())
            },
        )
    }

    private fun parseScore(json: JSONObject): ScoreResult {
        val review = json.optJSONArray("review")?.let { arr ->
            (0 until arr.length()).map { i ->
                val r = arr.getJSONObject(i)
                ReviewItem(
                    correct = r.optBoolean("correct", false),
                    answer = r.optInt("answer", -1),
                    explain = r.optString("explain").takeIf { it.isNotBlank() },
                )
            }
        } ?: emptyList()
        val breakdown = json.optJSONArray("breakdown")?.let { arr ->
            (0 until arr.length()).map { i ->
                val d = arr.getJSONObject(i)
                DomainBreakdown(
                    domain = d.optString("domain"), label = d.optString("label"),
                    correct = d.optInt("correct"), total = d.optInt("total"), pct = d.optInt("pct"),
                )
            }
        } ?: emptyList()
        val missed = json.optJSONArray("missed")?.let { arr ->
            (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                MissedNote(m.optString("domain"), m.optString("label"), m.optString("note"))
            }
        } ?: emptyList()
        return ScoreResult(
            scored = json.optBoolean("scored", false),
            late = json.optBoolean("late", false),
            message = json.optString("message").takeIf { it.isNotBlank() },
            score = json.optInt("score", 0),
            total = json.optInt("total", 0),
            pct = json.optInt("pct", 0),
            passed = json.optBoolean("passed", false),
            passPct = json.optInt("passPct", 0),
            flags = json.optJSONArray("flags").toStringList(),
            mode = json.optString("mode").takeIf { it.isNotBlank() },
            kind = json.optString("kind").takeIf { it.isNotBlank() },
            invalidated = json.optBoolean("invalidated", false),
            autoNote = json.optString("auto_note").takeIf { it.isNotBlank() },
            token = json.optString("token").takeIf { it.isNotBlank() },
            review = review,
            breakdown = breakdown,
            missed = missed,
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { optString(it) }
    }

    private fun JSONArray?.toCertList(): List<CertEntry> {
        if (this == null) return emptyList()
        return (0 until length()).map { i ->
            val c = getJSONObject(i)
            CertEntry(
                code = c.optString("code"),
                track = c.optString("track"),
                issued = c.optString("issued"),
                status = c.optString("status", "valid"),
            )
        }
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    companion object {
        const val BASE_URL = "https://academy.sentinelsafeguard.net"
    }
}
