package com.cue.reply.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role

/**
 * Motion vocabulary for the app.
 *
 * The built-in easings are too weak to read as intentional, so every curve here is
 * a stronger custom variant. Two rules the rest of the UI is held to:
 * `ease-in` is never used (it withholds movement at the exact moment the user is
 * watching), and nothing enters from `scale(0)` — real objects don't appear from
 * nothing.
 */
object CueEasing {
    /** Entering and exiting: starts fast, so the UI feels like it already heard you. */
    val Out: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** Moving or morphing on screen: accelerate out, decelerate in. */
    val InOut: Easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    /** Sheets and panels — the iOS drawer curve. */
    val Drawer: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
}

/** Durations, keyed to how much the element weighs on screen. Nothing over 300ms. */
object CueDuration {
    const val PRESS = 140
    const val CHIP = 180
    const val CARD = 220
    const val PANEL = 280
}

/**
 * True when the OS animator scale is 0 — the Android equivalent of
 * `prefers-reduced-motion`. Callers should drop movement but keep opacity and
 * color transitions, which aid comprehension rather than decorate.
 */
@Composable
@ReadOnlyComposable
fun reducedMotion(): Boolean {
    val context = LocalContext.current
    return Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
}

/**
 * Press feedback for any tappable surface: a subtle scale-down held while the
 * finger is down, springing back on release. This is the single highest-leverage
 * animation in the app — it's what makes a tap feel heard.
 *
 * @param pressedScale kept in the 0.95–0.98 range; deeper reads as a bug.
 */
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    role: Role? = Role.Button,
    indication: Indication? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val flat = reducedMotion()

    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !flat) pressedScale else 1f,
        // Press is a physical response, so it gets physics rather than a duration:
        // interrupting a spring keeps its velocity instead of restarting at zero.
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pressScale",
    )

    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = indication,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

/** Standard enter tween for content that appears in place. */
@Composable
fun <T> enterSpec(durationMillis: Int = CueDuration.CARD) =
    tween<T>(durationMillis = if (reducedMotion()) 0 else durationMillis, easing = CueEasing.Out)

/**
 * Stagger delay for the nth item of a list that animates in together.
 * Capped so a long list never makes the tail feel like it's lagging.
 */
fun staggerDelay(index: Int, stepMillis: Int = 45, maxMillis: Int = 270): Int =
    (index * stepMillis).coerceAtMost(maxMillis)
