package net.sentinelsafeguard.academyapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import net.sentinelsafeguard.academyapp.data.ConfettiPiece
import net.sentinelsafeguard.academyapp.data.confettiBurst
import net.sentinelsafeguard.academyapp.data.confettiStep
import net.sentinelsafeguard.academyapp.ui.theme.SentinelGreen
import net.sentinelsafeguard.academyapp.ui.theme.SentinelGreenDim
import net.sentinelsafeguard.academyapp.ui.theme.SentinelGreenSoft
import net.sentinelsafeguard.academyapp.ui.theme.SentinelText
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Certificate-celebration confetti (owner ruling 2026-11-08,
// estate-wide, applied exactly):
// - permitted ONLY outside trust moments — in this app it fires on
//   the post-issue certificates screen, NEVER on the issue action
//   itself (that surface stays morph/tick);
// - at most 18 pieces (CONFETTI_MAX_PIECES, enforced in the pure
//   burst plan in AcademyLogic);
// - estate colors only (emerald family + the parchment text tone —
//   no amber, which is attention-only in this theme; no purple,
//   ever);
// - real gravity (the sim in AcademyLogic: gravity + drag stepped
//   per frame, no easing-curve fake);
// - NONE under reduced-motion: the composable renders nothing
//   when [reduced] is true.
// One burst, then it settles and is gone — a celebration, not a
// decoration loop.
// ---------------------------------------------------------------------------

private val CONFETTI_COLORS: List<Color> = listOf(
    SentinelGreen, SentinelGreenDim, SentinelGreenSoft, SentinelText,
)

@Composable
fun CertificateConfetti(triggerKey: Any?, reduced: Boolean) {
    if (reduced || triggerKey == null) return
    var pieces by remember(triggerKey) { mutableStateOf<List<ConfettiPiece>>(emptyList()) }
    var seeded by remember(triggerKey) { mutableStateOf(false) }

    LaunchedEffect(triggerKey, seeded) {
        if (!seeded) return@LaunchedEffect
        var last = -1L
        var elapsed = 0f
        while (elapsed < 3.2f) {
            withFrameMillis { frame ->
                val dt = if (last < 0L) 0.016f
                else ((frame - last) / 1000f).coerceIn(0f, 0.05f)
                last = frame
                elapsed += dt
                pieces = pieces.map { confettiStep(it, dt) }
            }
        }
        pieces = emptyList()
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { sz ->
                if (!seeded && sz.width > 0) {
                    pieces = confettiBurst(sz.width / 2f, sz.height * 0.18f, 18, Random)
                    seeded = true
                }
            },
    ) {
        for (p in pieces) {
            rotate(p.angleDeg, pivot = Offset(p.x, p.y)) {
                drawRect(
                    color = CONFETTI_COLORS[p.colorIndex % CONFETTI_COLORS.size],
                    topLeft = Offset(p.x - p.widthPx / 2f, p.y - p.heightPx / 2f),
                    size = Size(p.widthPx, p.heightPx),
                )
            }
        }
    }
}
