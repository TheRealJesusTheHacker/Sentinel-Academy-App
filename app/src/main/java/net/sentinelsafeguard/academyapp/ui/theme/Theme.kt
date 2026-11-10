package net.sentinelsafeguard.academyapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// SENTINEL "EMERALD DEPTH" — the visual identity of the Sentinel
// phone-app family (Command App, Sentinel Mail App, and this app),
// set by the owner (2026-11-07) and ported here in full.
//
// THE RULE THAT MAKES THIS FILE WHAT IT IS: EVERY ColorScheme role
// is set explicitly. A role left unset is not "default dark" — it
// is Material3 BASELINE (deep purple containers) waiting for any
// widget that reads it, and it renders purple on screen. That is
// how a previous family release shipped a purple button: the
// scheme set `primary` but not `primaryContainer`, and a floating
// action button reads `primaryContainer`. There is no purple
// anywhere in the Sentinel identity, ever — so this scheme names
// all 40 roles by hand, and the discipline is: a new surface reads
// a token from THIS file, never a Material3 default.
//
// Palette anatomy (Emerald Depth):
//  - Near-black green-tinted grounds. Depth in this app comes
//    from LAYERING these grounds, never from a flat fill:
//      #060806 base -> #090C09 surface -> #0D120D raised ->
//      #162016 tonal container -> #1C2B1C top container.
//  - Emerald is the signal: #00E676 primary, #00B248 dim for
//    quiet weight. Text rides #E6EDE6 primary / #9DB89D muted on
//    the grounds; on emerald itself, near-black #001A0A text —
//    white on this emerald washes out.
//  - Amber #E8B13C is an ATTENTION color only (an expiring streak
//    window, a warning) — never decoration. Danger #B03A2E is a
//    muted brick, not UI-library red; it reads serious on the
//    dark grounds without alarming the whole screen.
//
// Chrome paints loudest, data quietest: the brighter emerald
// (#00E676) is for CHROME — primary actions, active states, key
// glyphs. Content wearing the signal color at chrome volume stops
// reading as content (that is how a previous family version's
// data rows read as buttons). Rows and bodies ride the text
// tones; color enters content only where the color IS the data.
// ---------------------------------------------------------------------------

/** Base app background — near-black with a green breath. */
val SentinelBlack = Color(0xFF060806)

/** Cards / flat surfaces resting on the base. */
val SentinelSurface = Color(0xFF090C09)

/** Raised surfaces: cards on cards, bars. */
val SentinelSurfaceVariant = Color(0xFF0D120D)

/** Tonal containers: chips, quiet fills. */
val SentinelContainer = Color(0xFF162016)

/** Highest container: active pills, top-tier surfaces. */
val SentinelContainerHigh = Color(0xFF1C2B1C)

/** Primary action / accent emerald. */
val SentinelGreen = Color(0xFF00E676)

/** Dimmer emerald for secondary emphasis. */
val SentinelGreenDim = Color(0xFF00B248)

/** Soft emerald wash for selected-row and avatar surfaces. */
val SentinelGreenSoft = Color(0xFF1E4028)

/** Attention amber — warnings ONLY, never decoration. */
val SentinelAmber = Color(0xFFE8B13C)

/** Primary text on the dark grounds. */
val SentinelText = Color(0xFFE6EDE6)

/** Secondary / muted text. */
val SentinelMuted = Color(0xFF9DB89D)

/** Danger — a muted brick that reads serious, not alarm-red. */
val SentinelDanger = Color(0xFFB03A2E)

/** Text/icon color on top of [SentinelGreen] filled surfaces. */
val SentinelOnGreen = Color(0xFF001A0A)

private val SentinelScheme = darkColorScheme(
    primary = SentinelGreen,
    onPrimary = SentinelOnGreen,
    primaryContainer = SentinelContainer,
    onPrimaryContainer = SentinelText,
    inversePrimary = SentinelGreenDim,
    secondary = SentinelGreenDim,
    onSecondary = SentinelOnGreen,
    secondaryContainer = SentinelContainer,
    onSecondaryContainer = SentinelText,
    tertiary = SentinelMuted,
    onTertiary = SentinelBlack,
    tertiaryContainer = SentinelSurfaceVariant,
    onTertiaryContainer = SentinelText,
    background = SentinelBlack,
    onBackground = SentinelText,
    surface = SentinelSurface,
    onSurface = SentinelText,
    surfaceVariant = SentinelSurfaceVariant,
    onSurfaceVariant = SentinelMuted,
    surfaceTint = SentinelSurface,
    inverseSurface = SentinelText,
    inverseOnSurface = SentinelBlack,
    error = SentinelDanger,
    onError = SentinelText,
    errorContainer = Color(0xFF4A1710),
    onErrorContainer = Color(0xFFFFDAD4),
    outline = SentinelGreenDim,
    outlineVariant = Color(0xFF1E2B1E),
    scrim = Color(0xFF000000),
    surfaceBright = SentinelContainerHigh,
    surfaceContainer = SentinelContainer,
    surfaceContainerHigh = SentinelContainerHigh,
    surfaceContainerHighest = SentinelContainerHigh,
    surfaceContainerLow = SentinelSurface,
    surfaceContainerLowest = SentinelBlack,
    surfaceDim = SentinelBlack,
)

// Type: the platform families on purpose (no downloaded fonts —
// nothing to license, nothing to fetch, works offline). Roles map
// to the app's visual jobs: headlines carry the screens' weight,
// titles head cards and sections, bodies are the working text,
// labels are the quiet uppercase system voice (rendered uppercase
// by the call sites), and MONOSPACE labels are the system's data
// voice — codes, counts, and stamps set in the fixed-width face so
// digits align like an instrument readout.
private val SentinelTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.5.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.6.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
        letterSpacing = 0.8.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 9.5.sp,
        letterSpacing = 0.6.sp,
    ),
)

// Corner scale: small = chips/fields, medium = cards/panels,
// large = sheets and dialogs. One scale, used everywhere — a
// surface's radius says its rank in the layer order.
private val SentinelShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun SentinelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SentinelScheme,
        typography = SentinelTypography,
        shapes = SentinelShapes,
        content = content,
    )
}
