package com.cue.reply.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cue.reply.data.db.ActivityType
import com.cue.reply.data.db.entity.ActivityLogEntity
import com.cue.reply.data.settings.LlmProvider
import com.cue.reply.data.settings.ReplyMode
import com.cue.reply.domain.model.ServiceType
import com.cue.reply.service.OrchestratorService
import com.cue.reply.system.Permissions
import com.cue.reply.ui.CueViewModel
import com.cue.reply.ui.LlmTestUi
import com.cue.reply.ui.components.PulsingDot
import com.cue.reply.ui.components.StatusChip
import com.cue.reply.ui.components.rememberOnResume
import com.cue.reply.ui.components.timeAgo
import com.cue.reply.ui.theme.Secondary
import com.cue.reply.ui.theme.pressable

/** Home / Dashboard — "is it working right now?" (Stitch: Home Dashboard). */
@Composable
fun HomeScreen(vm: CueViewModel, onViewAllActivity: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val activity by vm.activity.collectAsState(initial = emptyList())

    val notifOk = rememberOnResume {
        // Self-heal the binding Android drops on every app update.
        Permissions.rebindNotificationListener(context)
        Permissions.notificationAccessGranted(context)
    }
    val a11yOk = rememberOnResume { Permissions.accessibilityEnabled(context) }
    val llmTest by vm.llmTest.collectAsState()

    val hourAgo = System.currentTimeMillis() - 3_600_000
    val repliesThisHour = activity.count {
        it.timestamp >= hourAgo &&
            (it.type == ActivityType.AUTO_SENT || it.type == ActivityType.INSERTED)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatusHero(
            killed = settings.killed,
            active = settings.masterEnabled && !settings.killed,
            mode = settings.mode,
            onStartEngine = {
                vm.update { it.copy(masterEnabled = true, killed = false) }
                OrchestratorService.start(context)
            },
        )

        SectionTitle("Monitored Services")
        ServicePill(
            service = ServiceType.WHATSAPP,
            enabled = settings.whatsappEnabled,
            permissionOk = notifOk,
            fix = { context.startActivitySafe(Permissions.notificationAccessIntent()) },
        )
        ServicePill(
            service = ServiceType.TINDER,
            enabled = settings.tinderEnabled,
            permissionOk = a11yOk,
            fix = { context.startActivitySafe(Permissions.accessibilityIntent()) },
        )
        ServicePill(
            service = ServiceType.HINGE,
            enabled = settings.hingeEnabled,
            permissionOk = a11yOk,
            fix = { context.startActivitySafe(Permissions.accessibilityIntent()) },
        )

        SectionTitle("AI Engine")
        AiEngineCard(
            provider = settings.provider,
            model = settings.activeModel,
            hasKey = settings.activeApiKey.isNotBlank(),
            testState = llmTest,
            onTest = { vm.testLlmConnection() },
        )

        SectionTitle("Guardrails")
        GuardrailsCard(
            repliesThisHour = repliesThisHour,
            repliesPerHour = settings.repliesPerHour,
            autoDrop = settings.autoDropOnDetection,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            SectionTitle("Event Log")
            Text(
                "VIEW ALL",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        onClick = onViewAllActivity,
                        onClickLabel = "View all activity",
                        role = Role.Button,
                    )
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )
        }
        EventLogPreview(entries = activity.take(4))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatusHero(
    killed: Boolean,
    active: Boolean,
    mode: ReplyMode,
    onStartEngine: () -> Unit,
) {
    val (label, color, description) = when {
        killed -> Triple(
            "SYSTEM KILLED",
            MaterialTheme.colorScheme.error,
            "All reading and sending stopped. Toggle the kill switch to resume.",
        )
        active -> Triple(
            "SYSTEM ACTIVE",
            Secondary,
            "All local processes running nominally. Watching for new messages.",
        )
        else -> Triple(
            "SYSTEM PAUSED",
            MaterialTheme.colorScheme.outline,
            "Master switch is off. Nothing is being read.",
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "SYSTEM STATUS",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PulsingDot(color = color, size = 14)
            Text(label, style = MaterialTheme.typography.headlineLarge)
        }
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip(
                text = if (mode == ReplyMode.SUGGEST) "MODE: SUGGEST" else "MODE: AUTO-SEND (ALLOWLIST)",
                color = if (mode == ReplyMode.SUGGEST) Secondary else MaterialTheme.colorScheme.error,
                outlined = true,
            )
        }
        if (!active) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onStartEngine, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Start engine", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ServicePill(
    service: ServiceType,
    enabled: Boolean,
    permissionOk: Boolean,
    fix: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .pressable(enabled = !permissionOk, onClick = fix)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(service.displayName, style = MaterialTheme.typography.labelLarge)
        when {
            !enabled -> StatusChip("DISABLED", MaterialTheme.colorScheme.outline)
            permissionOk -> StatusChip("CONNECTED", Secondary, Icons.Filled.CheckCircle)
            else -> StatusChip(
                "PERMISSION MISSING",
                MaterialTheme.colorScheme.tertiary,
                Icons.Filled.Warning,
            )
        }
    }
}

@Composable
private fun GuardrailsCard(repliesThisHour: Int, repliesPerHour: Int, autoDrop: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Rate Limit", style = MaterialTheme.typography.labelLarge)
                Text(
                    "$repliesThisHour/$repliesPerHour msgs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = {
                    if (repliesPerHour == 0) 0f
                    else (repliesThisHour.toFloat() / repliesPerHour).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Human Cadence", style = MaterialTheme.typography.labelLarge)
            StatusChip("ON", Secondary, outlined = true)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("A11y Check", style = MaterialTheme.typography.labelLarge)
            if (autoDrop) StatusChip("OK", Secondary, outlined = true)
            else StatusChip("OFF", MaterialTheme.colorScheme.outline, outlined = true)
        }
    }
}

@Composable
private fun EventLogPreview(entries: List<ActivityLogEntity>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
    ) {
        if (entries.isEmpty()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Waiting for your first message.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Open a chat in WhatsApp or Tinder to test.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        entries.forEachIndexed { index, entry ->
            EventRow(entry)
            if (index != entries.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun EventRow(entry: ActivityLogEntity) {
    val (icon, tint) = entry.type.iconAndTint()
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(tint.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(entry.headline(), style = MaterialTheme.typography.bodyMedium)
            Text(
                "${timeAgo(entry.timestamp)} • ${entry.service?.displayName ?: "Core Logic"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun ActivityLogEntity.headline(): String {
    val who = contactName ?: "Unknown"
    return when (type) {
        ActivityType.RECEIVED -> "Message received from $who"
        ActivityType.SUGGESTED -> "Drafts generated for $who"
        ActivityType.INSERTED -> "Draft inserted"
        ActivityType.AUTO_SENT -> "Auto-sent reply"
        ActivityType.SEND_RETRY -> "Retrying send"
        ActivityType.SEND_FAILED -> "Send could not be verified"
        ActivityType.BLOCKED_NOT_ALLOWLISTED -> "Blocked — not allowlisted"
        ActivityType.RATE_LIMITED -> "Rate limit reached"
        ActivityType.CADENCE_HELD -> "Human-cadence hold"
        ActivityType.A11Y_DETECTED -> "Accessibility probe detected"
        ActivityType.SELECTOR_BROKEN -> "Selectors broken"
        ActivityType.KILLED -> "Kill switch engaged"
        ActivityType.RESUMED -> "System resumed"
        ActivityType.ERROR -> "Error"
    }
}

@Composable
internal fun ActivityType.iconAndTint(): Pair<ImageVector, Color> = when (this) {
    ActivityType.RECEIVED -> Icons.Filled.MarkChatUnread to MaterialTheme.colorScheme.primaryContainer
    ActivityType.SUGGESTED -> Icons.Filled.AutoAwesome to MaterialTheme.colorScheme.primary
    ActivityType.INSERTED -> Icons.AutoMirrored.Filled.Input to Secondary
    ActivityType.AUTO_SENT -> Icons.AutoMirrored.Filled.Send to com.cue.reply.ui.theme.SuccessGreen
    ActivityType.SEND_RETRY -> Icons.Filled.EditNote to MaterialTheme.colorScheme.outline
    ActivityType.SEND_FAILED -> Icons.Filled.Warning to MaterialTheme.colorScheme.error
    ActivityType.BLOCKED_NOT_ALLOWLISTED -> Icons.Filled.Block to MaterialTheme.colorScheme.error
    ActivityType.RATE_LIMITED -> Icons.Filled.Block to MaterialTheme.colorScheme.tertiary
    ActivityType.CADENCE_HELD -> Icons.Filled.EditNote to MaterialTheme.colorScheme.outline
    ActivityType.A11Y_DETECTED -> Icons.Filled.Warning to MaterialTheme.colorScheme.tertiary
    ActivityType.SELECTOR_BROKEN -> Icons.Filled.Warning to MaterialTheme.colorScheme.tertiary
    ActivityType.KILLED -> Icons.Filled.PowerSettingsNew to MaterialTheme.colorScheme.error
    ActivityType.RESUMED -> Icons.Filled.CheckCircle to Secondary
    ActivityType.ERROR -> Icons.Filled.Warning to MaterialTheme.colorScheme.error
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall)
}

/**
 * Shows which provider/model will actually answer, and lets the user prove it
 * with a live round-trip instead of trusting the label.
 */
@Composable
private fun AiEngineCard(
    provider: LlmProvider,
    model: String,
    hasKey: Boolean,
    testState: LlmTestUi,
    onTest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(provider.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    model,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasKey) {
                StatusChip("KEY SET", Secondary, Icons.Filled.CheckCircle)
            } else {
                StatusChip("NO KEY", MaterialTheme.colorScheme.error, Icons.Filled.Warning)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onTest,
                enabled = hasKey && testState !is LlmTestUi.Testing,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Test this model", style = MaterialTheme.typography.labelLarge)
            }
            when (testState) {
                LlmTestUi.Testing -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("TESTING…", style = MaterialTheme.typography.labelSmall)
                }
                LlmTestUi.Verified ->
                    StatusChip("REPLIED OK", Secondary, Icons.Filled.CheckCircle)
                is LlmTestUi.Failed ->
                    StatusChip("FAILED", MaterialTheme.colorScheme.error, Icons.Filled.Warning)
                LlmTestUi.Idle -> Text(
                    if (hasKey) "Not tested yet" else "Add a key in Settings",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        (testState as? LlmTestUi.Failed)?.let {
            Text(
                it.reason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
