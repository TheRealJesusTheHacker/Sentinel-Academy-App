package net.sentinelsafeguard.academyapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.sentinelsafeguard.academyapp.R
import net.sentinelsafeguard.academyapp.data.signupPasswordProblem
import net.sentinelsafeguard.academyapp.ui.theme.SentinelGreen
import net.sentinelsafeguard.academyapp.ui.theme.SentinelMuted

// ---------------------------------------------------------------------------
// Auth — sign-in, account creation, the pending-confirmation
// state, and the PIN lock screen.
//
// Honesty notes, all deliberate:
// - A failed sign-in shows the SERVER's message (the server's
//   generic 401 is identical for unknown email / wrong password /
//   pending account — the app adds no oracle of its own).
// - Signup's armed-mode shape (202 + pending_confirmation, no
//   token, duplicates indistinguishable) renders as "check your
//   email" and claims nothing more (contract §1.1/§1.3).
// - The lock screen says what the lock IS: a device gate over the
//   stored session, not an account credential.
// ---------------------------------------------------------------------------

@Composable
private fun AcademyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SentinelGreen,
            focusedLabelColor = SentinelGreen,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun AuthScreen(vm: AcademyViewModel, reduced: Boolean) {
    var signingUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var track by remember { mutableStateOf("best-practice") }
    var promo by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var shakeTrigger by remember { mutableStateOf(0) }

    val passwordShort = stringResource(R.string.auth_error_password_short)
    val passwordLong = stringResource(R.string.auth_error_password_long)
    val fillFields = stringResource(R.string.signup_fill_fields)

    val serverError = when (vm.authError) {
        AcademyViewModel.NETWORK_ERROR -> stringResource(R.string.error_network)
        AcademyViewModel.PASSWORD_SHORT -> passwordShort
        AcademyViewModel.PASSWORD_LONG -> passwordLong
        AcademyViewModel.DUPLICATE_EMAIL -> stringResource(R.string.auth_error_duplicate)
        else -> vm.authError
    }
    androidx.compose.runtime.LaunchedEffect(vm.authError) {
        if (vm.authError != null) shakeTrigger++
    }
    val shake = rememberShakeOffset(shakeTrigger, reduced)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.academy_mark),
            contentDescription = stringResource(R.string.academy_mark_description),
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(if (signingUp) R.string.auth_signup_title else R.string.auth_login_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.auth_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = SentinelMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))

        vm.authNotice?.let { notice ->
            SentinelCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        when (notice) {
                            AcademyViewModel.AuthNotice.SESSION_EXPIRED -> R.string.auth_notice_expired
                            AcademyViewModel.AuthNotice.SIGNED_OUT -> R.string.auth_notice_signed_out
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        Column(modifier = Modifier.graphicsLayer {
            // Reduced motion: shake.x carries the alpha factor
            // (Motion.kt's documented contract); full motion: it
            // carries pixel travel. (Audit 2026-11-11: both auth
            // call sites applied it as pixels, which made the
            // reduced-motion signal a sub-pixel drift.)
            if (reduced) alpha = shake.x else translationX = shake.x
        }) {
            if (signingUp) {
                AcademyTextField(displayName, { displayName = it }, stringResource(R.string.auth_display_name))
                Spacer(Modifier.height(12.dp))
            }
            AcademyTextField(
                email, { email = it }, stringResource(R.string.auth_email),
                keyboardType = KeyboardType.Email,
            )
            Spacer(Modifier.height(12.dp))
            AcademyTextField(
                password, { password = it }, stringResource(R.string.auth_password),
                keyboardType = KeyboardType.Password, isPassword = true,
            )
            if (signingUp) {
                Spacer(Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.auth_track_label))
                Spacer(Modifier.height(8.dp))
                val tracks = vm.catalog?.tracks?.filter { it.slug in SIGNUP_TRACKS } ?: emptyList()
                if (tracks.isEmpty()) {
                    TrackChoice(0, "Best Practice", track == "best-practice", reduced) { track = "best-practice" }
                    Spacer(Modifier.height(8.dp))
                    TrackChoice(1, "Expert", track == "expert", reduced) { track = "expert" }
                } else {
                    tracks.forEachIndexed { idx, t ->
                        TrackChoice(idx, t.name, track == t.slug, reduced) { track = t.slug }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                AcademyTextField(promo, { promo = it }, stringResource(R.string.auth_promo_optional))
            }
        }

        (localError ?: serverError)?.let {
            Spacer(Modifier.height(12.dp))
            ErrorLine(it)
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = stringResource(if (signingUp) R.string.auth_signup else R.string.auth_login),
            busy = vm.busy,
            reduced = reduced,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                localError = null
                val problem = if (signingUp) signupPasswordProblem(password) else null
                when {
                    problem == "short" -> { localError = passwordShort; shakeTrigger++ }
                    problem == "long" -> { localError = passwordLong; shakeTrigger++ }
                    // A shake alone doesn't say what is missing
                    // (audit 2026-11-11): name the fields.
                    signingUp && (displayName.isBlank() || email.isBlank()) -> {
                        localError = fillFields; shakeTrigger++
                    }
                    signingUp -> vm.signup(email, password, displayName, track, promo.ifBlank { null })
                    else -> vm.login(email, password)
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        GhostButton(
            text = stringResource(if (signingUp) R.string.auth_to_login else R.string.auth_to_signup),
            reduced = reduced,
            modifier = Modifier.fillMaxWidth(),
            onClick = { signingUp = !signingUp; localError = null },
        )
    }
}

private val SIGNUP_TRACKS = setOf("best-practice", "expert")

@Composable
private fun TrackChoice(index: Int, name: String, selected: Boolean, reduced: Boolean, onClick: () -> Unit) {
    // The badge index is the row's POSITION (audit 2026-11-11):
    // it used to be guessed from the track's name prefix, which
    // breaks the moment a track is renamed or localized.
    ChoiceRow(
        index = index,
        text = name,
        selected = selected,
        onClick = onClick,
        reduced = reduced,
    )
}

@Composable
fun PendingConfirmScreen(vm: AcademyViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.academy_mark),
            contentDescription = stringResource(R.string.academy_mark_description),
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.pending_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.pending_body),
            style = MaterialTheme.typography.bodyMedium,
            color = SentinelMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = stringResource(R.string.pending_back),
            modifier = Modifier.fillMaxWidth(),
            onClick = { vm.backToAuth() },
        )
    }
}

@Composable
fun LockScreen(vm: AcademyViewModel, reduced: Boolean) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var shakeTrigger by remember { mutableStateOf(0) }
    val shake = rememberShakeOffset(shakeTrigger, reduced)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Image(
            painter = painterResource(R.drawable.academy_mark),
            contentDescription = stringResource(R.string.academy_mark_description),
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.lock_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = SentinelMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Column(modifier = Modifier.graphicsLayer {
            if (reduced) alpha = shake.x else translationX = shake.x
        }) {
            AcademyTextField(
                pin, { pin = it.filter { c -> c.isDigit() }.take(6) },
                stringResource(R.string.lock_pin_hint),
                keyboardType = KeyboardType.NumberPassword, isPassword = true,
            )
        }
        if (wrong) {
            Spacer(Modifier.height(10.dp))
            ErrorLine(stringResource(R.string.lock_wrong))
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = stringResource(R.string.lock_unlock),
            reduced = reduced,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (vm.store.checkPin(pin)) {
                    vm.unlock()
                } else {
                    wrong = true
                    shakeTrigger++
                    pin = ""
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton(
                text = stringResource(R.string.lock_signout),
                reduced = reduced,
                onClick = { vm.logout() },
            )
        }
    }
}
