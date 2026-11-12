package net.sentinelsafeguard.academyapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.sentinelsafeguard.academyapp.R
import net.sentinelsafeguard.academyapp.data.certCodeWellFormed
import net.sentinelsafeguard.academyapp.data.capSettingsName
import net.sentinelsafeguard.academyapp.data.profileInputProblem
import net.sentinelsafeguard.academyapp.data.signupPasswordProblem
import net.sentinelsafeguard.academyapp.ui.theme.SentinelContainer
import net.sentinelsafeguard.academyapp.ui.theme.SentinelContainerHigh
import net.sentinelsafeguard.academyapp.ui.theme.SentinelGreen
import net.sentinelsafeguard.academyapp.ui.theme.SentinelMuted
import net.sentinelsafeguard.academyapp.ui.theme.SentinelOnGreen

// ---------------------------------------------------------------------------
// Certificates + verify, and Settings.
//
// Verify honesty (contract §6): the app calls ONLY
// /api/app/verify — never the employer /api/verify (a learner
// bearer is refused there by design, and the app must never hold
// an employer token). Malformed codes are refused CLIENT-side
// before a lookup is spent from the shared 60/hour budget, and
// are presented IDENTICALLY to never-issued codes — the same
// sentence, the same surface — exactly as every public face
// presents them.
//
// The celebration confetti overlays THIS screen when a fresh
// certificate lands (post-issue surface, owner's confetti rule);
// the issue action itself stayed morph/tick on the exam screen.
// ---------------------------------------------------------------------------

@Composable
fun CertificatesScreen(vm: AcademyViewModel, reduced: Boolean) {
    val certs = vm.me?.certList ?: emptyList()
    val trackNames = vm.catalog?.tracks?.associate { it.slug to it.certName } ?: emptyMap()
    LaunchedEffect(vm.celebrateCode) {
        if (vm.celebrateCode != null) {
            delay(3600)
            vm.celebrateCode = null
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            SectionLabel(stringResource(R.string.certs_title))
            Spacer(Modifier.height(12.dp))
            if (certs.isEmpty()) {
                Text(
                    text = stringResource(R.string.certs_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SentinelMuted,
                )
            } else {
                certs.forEach { cert ->
                    SentinelCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.academy_mark),
                                contentDescription = stringResource(R.string.academy_mark_description),
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = trackNames[cert.track] ?: cert.track,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = cert.code,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SentinelGreen,
                                )
                            }
                            Text(
                                text = stringResource(
                                    if (cert.status == "valid") R.string.certs_status_valid else R.string.certs_status_revoked,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (cert.status == "valid") SentinelGreen else SentinelMuted,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.certs_issued_on, cert.issued),
                            style = MaterialTheme.typography.bodySmall,
                            color = SentinelMuted,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            VerifySection(vm, reduced)
        }
        CertificateConfetti(triggerKey = vm.celebrateCode, reduced = reduced)
    }
}

@Composable
private fun VerifySection(vm: AcademyViewModel, reduced: Boolean) {
    var code by remember { mutableStateOf("") }
    var localNotFound by remember { mutableStateOf(false) }
    SectionLabel(stringResource(R.string.verify_title))
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.verify_hint),
        style = MaterialTheme.typography.bodySmall,
        color = SentinelMuted,
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = code,
        onValueChange = { code = it; localNotFound = false; vm.clearVerify() },
        label = { Text(stringResource(R.string.verify_code)) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SentinelGreen,
            focusedLabelColor = SentinelGreen,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    PrimaryButton(
        text = stringResource(R.string.verify_cta),
        busy = vm.verifyBusy,
        reduced = reduced,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            // Format gate FIRST: a malformed code never spends a
            // lookup, and reads exactly like a never-issued one.
            if (!certCodeWellFormed(code)) {
                localNotFound = true
            } else {
                vm.verify(code.trim())
            }
        },
    )
    val result = vm.verifyResult
    when {
        localNotFound -> {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.verify_not_found), style = MaterialTheme.typography.bodyMedium, color = SentinelMuted)
        }
        result != null && result.lookupError != null -> {
            // No lookup happened (audit 2026-11-11): the shared
            // budget was spent (429) or the Academy could not be
            // reached. Neither may wear the "no certificate
            // matches" sentence — that claim belongs only to a
            // lookup that actually ran.
            Spacer(Modifier.height(12.dp))
            ErrorLine(
                stringResource(
                    if (result.lookupError == "rate") R.string.verify_error_rate else R.string.error_network,
                ),
            )
        }
        result != null && result.found -> {
            Spacer(Modifier.height(12.dp))
            SentinelCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DrawnTick(modifier = Modifier.size(22.dp), playKey = 1, reduced = reduced)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.verify_found), style = MaterialTheme.typography.titleMedium, color = SentinelGreen)
                }
                Spacer(Modifier.height(8.dp))
                result.name?.let { Text(stringResource(R.string.verify_name, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground) }
                // Labels match the payload (audit 2026-11-11):
                // `certificate` is the certificate's title and
                // `track_name` the track's name — the card used
                // to print the title under the "Track:" label
                // and never show the track name at all.
                result.certificate?.let { Text(stringResource(R.string.verify_certificate, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground) }
                (result.trackName ?: result.track)?.let { Text(stringResource(R.string.verify_track, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground) }
                result.issued?.let { Text(stringResource(R.string.verify_issued, it), style = MaterialTheme.typography.bodyMedium, color = SentinelMuted) }
            }
        }
        result != null -> {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.verify_not_found), style = MaterialTheme.typography.bodyMedium, color = SentinelMuted)
        }
    }
}

// ---------------- Settings ----------------

@Composable
fun SettingsScreen(vm: AcademyViewModel, reduced: Boolean) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    var promoCode by remember { mutableStateOf("") }
    var promoNote by remember { mutableStateOf<String?>(null) }
    var promoOk by remember { mutableStateOf(false) }
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var pinNote by remember { mutableStateOf<String?>(null) }
    var pinRemoved by remember { mutableStateOf(false) }
    val pinMismatch = stringResource(R.string.settings_pin_mismatch)
    val pinShort = stringResource(R.string.settings_pin_short)
    val pinSaved = stringResource(R.string.settings_pin_saved)

    // Account settings (G4 twins): the editable state seeds from
    // the server's /api/me values, keyed on them — a confirmed
    // save updates `me`, which re-seeds the fields with the
    // server's fresh read-back; edits in progress are untouched
    // by refreshes that change nothing.
    val user = vm.me?.user
    val netError = stringResource(R.string.error_network)
    val namesRequired = stringResource(R.string.settings_names_required)
    var displayName by remember(user?.displayName) { mutableStateOf(user?.displayName ?: "") }
    var certName by remember(user?.certName) { mutableStateOf(user?.certName ?: "") }
    var profileNote by remember { mutableStateOf<String?>(null) }
    var profileSaved by remember { mutableStateOf(false) }
    var registry by remember(user?.registry) { mutableStateOf(user?.registry ?: false) }
    var leaderboard by remember(user?.leaderboard) { mutableStateOf(user?.leaderboard ?: false) }
    var socialNote by remember { mutableStateOf<String?>(null) }
    var socialSaved by remember { mutableStateOf(false) }
    var anniversary by remember(user?.notifyCertAnniversary) { mutableStateOf(user?.notifyCertAnniversary ?: false) }
    var notifyNote by remember { mutableStateOf<String?>(null) }
    var notifySaved by remember { mutableStateOf(false) }
    var currentPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var pwNote by remember { mutableStateOf<String?>(null) }
    var pwChanged by remember { mutableStateOf(false) }
    val pwShort = stringResource(R.string.auth_error_password_short)
    val pwLong = stringResource(R.string.auth_error_password_long)
    val pwFill = stringResource(R.string.settings_password_fill)
    val pwNotConfirmed = stringResource(R.string.settings_password_not_confirmed)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(18.dp))
        SectionLabel(stringResource(R.string.settings_account))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_profile_body), style = MaterialTheme.typography.bodyMedium, color = SentinelMuted)
        Spacer(Modifier.height(10.dp))
        SentinelCard(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = capSettingsName(it); profileSaved = false; profileNote = null },
                label = { Text(stringResource(R.string.settings_name)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SentinelGreen, focusedLabelColor = SentinelGreen,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = certName,
                onValueChange = { certName = capSettingsName(it); profileSaved = false; profileNote = null },
                label = { Text(stringResource(R.string.settings_cert_name)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SentinelGreen, focusedLabelColor = SentinelGreen,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            vm.me?.user?.let { u ->
                Spacer(Modifier.height(10.dp))
                SettingsRow(stringResource(R.string.settings_email), u.email)
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            text = stringResource(R.string.settings_save),
            busy = vm.settingsBusy,
            enabled = user != null &&
                (displayName.trim() != user.displayName || certName.trim() != user.certName),
            reduced = reduced,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (profileInputProblem(displayName, certName) != null) {
                    profileNote = namesRequired
                    profileSaved = false
                } else {
                    vm.saveProfile(displayName, certName) { ok, msg ->
                        profileSaved = ok
                        profileNote = if (ok) null else msg
                    }
                }
            },
        )
        when {
            profileSaved -> { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.settings_saved), style = MaterialTheme.typography.bodyMedium, color = SentinelGreen) }
            profileNote != null -> { Spacer(Modifier.height(8.dp)); ErrorLine(profileNote?.let { if (it == AcademyViewModel.NETWORK_ERROR) netError else it } ?: "") }
        }
        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.settings_social_title))
        Spacer(Modifier.height(10.dp))
        SentinelCard(modifier = Modifier.fillMaxWidth()) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_registry),
                body = stringResource(R.string.settings_registry_body),
                checked = registry,
                enabled = user != null && !vm.settingsBusy,
                onCheckedChange = { registry = it; socialSaved = false; socialNote = null },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_leaderboard),
                body = stringResource(R.string.settings_leaderboard_body),
                checked = leaderboard,
                enabled = user != null && !vm.settingsBusy,
                onCheckedChange = { leaderboard = it; socialSaved = false; socialNote = null },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_social_note), style = MaterialTheme.typography.bodySmall, color = SentinelMuted)
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            text = stringResource(R.string.settings_save),
            busy = vm.settingsBusy,
            enabled = user != null && (registry != user.registry || leaderboard != user.leaderboard),
            reduced = reduced,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                // ONE save writes BOTH switches — the twin's
                // checkbox rule (an absent switch would read as
                // OFF), and the client states both values outright.
                vm.saveSocial(registry, leaderboard) { ok, msg ->
                    socialSaved = ok
                    socialNote = if (ok) null else msg
                }
            },
        )
        when {
            socialSaved -> { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.settings_saved), style = MaterialTheme.typography.bodyMedium, color = SentinelGreen) }
            socialNote != null -> { Spacer(Modifier.height(8.dp)); ErrorLine(socialNote?.let { if (it == AcademyViewModel.NETWORK_ERROR) netError else it } ?: "") }
        }
        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.settings_notifications_title))
        Spacer(Modifier.height(10.dp))
        SentinelCard(modifier = Modifier.fillMaxWidth()) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_cert_anniversary),
                body = stringResource(R.string.settings_cert_anniversary_body),
                checked = anniversary,
                enabled = user != null && !vm.settingsBusy,
                onCheckedChange = { anniversary = it; notifySaved = false; notifyNote = null },
            )
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            text = stringResource(R.string.settings_save),
            busy = vm.settingsBusy,
            enabled = user != null && anniversary != user.notifyCertAnniversary,
            reduced = reduced,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                vm.saveNotifications(anniversary) { ok, msg ->
                    notifySaved = ok
                    notifyNote = if (ok) null else msg
                }
            },
        )
        when {
            notifySaved -> { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.settings_saved), style = MaterialTheme.typography.bodyMedium, color = SentinelGreen) }
            notifyNote != null -> { Spacer(Modifier.height(8.dp)); ErrorLine(notifyNote?.let { if (it == AcademyViewModel.NETWORK_ERROR) netError else it } ?: "") }
        }
        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.settings_promo_title))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_promo_body), style = MaterialTheme.typography.bodyMedium, color = SentinelMuted)
        Spacer(Modifier.height(10.dp))
        if (vm.me?.user?.promo == true) {
            Text(stringResource(R.string.settings_promo_active), style = MaterialTheme.typography.bodyMedium, color = SentinelGreen)
        } else {
            OutlinedTextField(
                value = promoCode,
                onValueChange = { promoCode = it },
                label = { Text(stringResource(R.string.settings_promo_hint)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SentinelGreen, focusedLabelColor = SentinelGreen,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                text = stringResource(R.string.settings_promo_apply),
                reduced = reduced,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (promoCode.isNotBlank()) {
                        vm.applyPromo(promoCode) { ok, msg ->
                            promoOk = ok
                            // A failure with no server message
                            // (transport) still gets its sentence
                            // (audit 2026-11-11): the generic
                            // not-accepted line, never silence.
                            promoNote = if (ok) null else (msg ?: "")
                        }
                    }
                },
            )
            val applied = stringResource(R.string.settings_promo_applied)
            val failed = stringResource(R.string.settings_promo_failed)
            when {
                promoOk -> { Spacer(Modifier.height(8.dp)); Text(applied, style = MaterialTheme.typography.bodyMedium, color = SentinelGreen) }
                promoNote != null -> { Spacer(Modifier.height(8.dp)); ErrorLine(promoNote?.ifBlank { failed } ?: failed) }
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.settings_lock_title))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_lock_body), style = MaterialTheme.typography.bodyMedium, color = SentinelMuted)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(if (vm.store.hasPin()) R.string.settings_lock_on else R.string.settings_lock_off),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = pin1,
            onValueChange = { pin1 = it.filter { c -> c.isDigit() }.take(6) },
            label = { Text(stringResource(R.string.settings_pin_new)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SentinelGreen, focusedLabelColor = SentinelGreen,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = pin2,
            onValueChange = { pin2 = it.filter { c -> c.isDigit() }.take(6) },
            label = { Text(stringResource(R.string.settings_pin_confirm)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SentinelGreen, focusedLabelColor = SentinelGreen,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        pinNote?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = SentinelGreen)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            PrimaryButton(
                text = stringResource(
                    if (vm.store.hasPin()) R.string.settings_lock_change else R.string.settings_lock_set,
                ),
                reduced = reduced,
                modifier = Modifier.weight(1f),
                onClick = {
                    pinNote = when {
                        pin1.length !in 4..6 -> pinShort
                        pin1 != pin2 -> pinMismatch
                        else -> {
                            vm.store.setPin(pin1)
                            pin1 = ""; pin2 = ""
                            pinRemoved = false
                            pinSaved
                        }
                    }
                },
            )
            if (vm.store.hasPin()) {
                Spacer(Modifier.width(10.dp))
                GhostButton(
                    text = stringResource(R.string.settings_lock_remove),
                    reduced = reduced,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        vm.store.clearPin()
                        pinNote = null
                        pinRemoved = true
                        pin1 = ""; pin2 = ""
                    },
                )
            }
        }
        if (pinRemoved && !vm.store.hasPin()) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_pin_removed),
                style = MaterialTheme.typography.bodySmall,
                color = SentinelMuted,
            )
        }
        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.settings_password_title))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_password_body), style = MaterialTheme.typography.bodyMedium, color = SentinelMuted)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = currentPw,
            onValueChange = { currentPw = it; pwChanged = false; pwNote = null },
            label = { Text(stringResource(R.string.settings_password_current)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SentinelGreen, focusedLabelColor = SentinelGreen,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = newPw,
            onValueChange = { newPw = it; pwChanged = false; pwNote = null },
            label = { Text(stringResource(R.string.settings_password_new)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SentinelGreen, focusedLabelColor = SentinelGreen,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            text = stringResource(R.string.settings_password_change),
            busy = vm.settingsBusy,
            reduced = reduced,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                // Client pre-flight mirrors the server's own
                // rules (blank fields, then the 10..200 policy);
                // every other refusal is the server's to state,
                // and its words show verbatim below.
                pwNote = when {
                    currentPw.isEmpty() || newPw.isEmpty() -> pwFill
                    signupPasswordProblem(newPw) == "short" -> pwShort
                    signupPasswordProblem(newPw) == "long" -> pwLong
                    else -> null
                }
                if (pwNote == null) {
                    vm.changePassword(currentPw, newPw) { ok, msg ->
                        if (ok) {
                            pwChanged = true
                            currentPw = ""; newPw = ""
                        } else {
                            pwNote = msg
                        }
                    }
                }
            },
        )
        when {
            pwChanged -> { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.settings_password_changed), style = MaterialTheme.typography.bodyMedium, color = SentinelGreen) }
            pwNote != null -> { Spacer(Modifier.height(8.dp)); ErrorLine(pwNote?.let { when (it) { AcademyViewModel.NETWORK_ERROR -> netError; AcademyViewModel.PASSWORD_NOT_CONFIRMED -> pwNotConfirmed; else -> it } } ?: "") }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.settings_signout_body), style = MaterialTheme.typography.bodyMedium, color = SentinelMuted)
        Spacer(Modifier.height(10.dp))
        GhostButton(
            text = stringResource(R.string.settings_signout),
            reduced = reduced,
            modifier = Modifier.fillMaxWidth(),
            onClick = { vm.logout() },
        )
        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.settings_about))
        Spacer(Modifier.height(12.dp))
        SentinelCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.sentinel_logo),
                    contentDescription = stringResource(R.string.logo_description),
                    modifier = Modifier.width(110.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(stringResource(R.string.start_version, versionName), style = MaterialTheme.typography.labelMedium, color = SentinelMuted)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.settings_server), style = MaterialTheme.typography.bodySmall, color = SentinelMuted)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.settings_language_note), style = MaterialTheme.typography.bodySmall, color = SentinelMuted)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** One settings switch: label + honest description on the left,
 *  the switch on the right, the whole row at least 56dp tall
 *  (the family's 48dp touch floor, with margin). Emerald Depth
 *  tokens only — the switch checks in emerald, rests in tonal
 *  container; it never borrows Material's default purple. */
@Composable
private fun SettingsSwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(body, style = MaterialTheme.typography.bodySmall, color = SentinelMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SentinelOnGreen,
                checkedTrackColor = SentinelGreen,
                checkedBorderColor = SentinelGreen,
                uncheckedThumbColor = SentinelMuted,
                uncheckedTrackColor = SentinelContainer,
                uncheckedBorderColor = SentinelContainerHigh,
            ),
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SentinelMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}
