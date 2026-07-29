package com.cue.reply.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Diversity1
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cue.reply.domain.model.ServiceType
import com.cue.reply.ui.theme.CueDuration
import com.cue.reply.ui.theme.CueEasing
import com.cue.reply.ui.theme.Secondary
import com.cue.reply.ui.theme.TinderPink
import com.cue.reply.ui.theme.pressable

/** Per-service accent color used for card accent bars and avatar badges. */
val ServiceType.accentColor: Color
    get() = when (this) {
        ServiceType.WHATSAPP -> Secondary
        ServiceType.TINDER -> TinderPink
        ServiceType.HINGE -> Color(0xFF6E56CF)
    }

val ServiceType.badgeIcon: ImageVector
    get() = when (this) {
        ServiceType.WHATSAPP -> Icons.AutoMirrored.Filled.Chat
        ServiceType.TINDER -> Icons.Filled.Favorite
        ServiceType.HINGE -> Icons.Filled.Diversity1
    }

/**
 * Sticky brand header from the designs: memory glyph + "Sentience Core" on the
 * left, the always-visible red KILL SWITCH on the right.
 */
@Composable
fun CueTopBar(
    killed: Boolean,
    onToggleKill: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Icon(
                    Icons.Filled.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Sentience Core",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            KillSwitchButton(killed = killed, onClick = onToggleKill)
        }
    }
}

@Composable
fun KillSwitchButton(killed: Boolean, onClick: () -> Unit) {
    // Killing the app is rare and consequential, so the state change is worth
    // animating: the colour crossfade makes it read as a deliberate transition
    // rather than a repaint. Colour is kept even under reduced motion.
    val bg by animateColorAsState(
        targetValue = if (killed) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.error,
        animationSpec = tween(CueDuration.CHIP, easing = CueEasing.Out),
        label = "killBg",
    )
    val fg by animateColorAsState(
        targetValue = if (killed) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.onError,
        animationSpec = tween(CueDuration.CHIP, easing = CueEasing.Out),
        label = "killFg",
    )
    Row(
        modifier = Modifier
            .background(bg, CircleShape)
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Filled.PowerSettingsNew,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(16.dp),
        )
        Text(
            if (killed) "KILLED · RESUME" else "KILL SWITCH",
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            maxLines = 1,
        )
    }
}

/** Small status pill: `Connected ✓` / `Permission missing` / mode badges. */
@Composable
fun StatusChip(
    text: String,
    color: Color,
    icon: ImageVector? = null,
    outlined: Boolean = false,
) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.10f), CircleShape)
            .then(
                if (outlined) Modifier.border(1.dp, color.copy(alpha = 0.25f), CircleShape)
                else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** The pulsing "system live" dot from the status hero and mode bar. */
@Composable
fun PulsingDot(color: Color, size: Int = 12) {
    // Honour the system "remove animations" accessibility setting.
    if (animationsDisabled()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size((size * 2.2f).dp)) {
            Box(Modifier.size(size.dp).background(color, CircleShape))
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "alpha",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size((size * 2.2f).dp)) {
        Box(
            Modifier
                .size(size.dp)
                .scale(scale)
                .alpha(alpha)
                .background(color, CircleShape)
        )
        Box(
            Modifier
                .size(size.dp)
                .background(color, CircleShape)
        )
    }
}

/** Circular initials avatar with the service badge pinned bottom-right. */
@Composable
fun ContactAvatar(name: String?, service: ServiceType, size: Int = 48) {
    Box {
        Box(
            modifier = Modifier
                .size(size.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (name ?: "?").trim().take(1).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size((size * 0.42f).dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .padding(2.dp)
                .background(service.accentColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                service.badgeIcon,
                contentDescription = service.displayName,
                tint = Color.White,
                modifier = Modifier.size((size * 0.24f).dp),
            )
        }
    }
}

/** Left accent bar drawn on cards, per the conversation-list design. */
@Composable
fun AccentBar(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(4.dp)
            .background(color.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
    )
}

/** Re-runs [check] whenever the screen resumes — keeps permission chips live. */
@Composable
fun rememberOnResume(check: () -> Boolean): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return remember(tick) { check() }
}

/** Sub-screen header: back arrow + mono overline + title (settings sub-pages). */
@Composable
fun SubScreenHeader(
    title: String,
    overline: String? = null,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column {
            if (overline != null) {
                Text(
                    overline,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

/** "10m ago" style relative timestamps used across lists. */
fun timeAgo(ts: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - ts
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}

/** True when the user has turned animations off in system accessibility settings. */
@Composable
private fun animationsDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
