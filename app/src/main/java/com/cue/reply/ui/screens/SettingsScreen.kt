package com.cue.reply.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.cue.reply.data.settings.LlmProvider
import com.cue.reply.data.settings.ReplyMode
import com.cue.reply.llm.LlmCatalog
import com.cue.reply.llm.PersonaPreset
import com.cue.reply.system.Permissions
import com.cue.reply.ui.CueViewModel
import com.cue.reply.ui.LlmTestUi
import com.cue.reply.ui.components.StatusChip
import com.cue.reply.ui.components.rememberOnResume
import com.cue.reply.ui.theme.Secondary

/** Settings hub (F1–F6 condensed; the designed sub-screens can split out later). */
@Composable
fun SettingsScreen(vm: CueViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmAutoSend by rememberSaveable { mutableStateOf(false) }
    var personaDraft by rememberSaveable(settings.persona) { mutableStateOf(settings.persona) }

    val notifOk = rememberOnResume { Permissions.notificationAccessGranted(context) }
    val a11yOk = rememberOnResume { Permissions.accessibilityEnabled(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)

        SettingsCard("Reply Mode") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModePill(
                    label = "SUGGEST",
                    icon = Icons.Filled.Lightbulb,
                    selected = settings.mode == ReplyMode.SUGGEST,
                    modifier = Modifier.weight(1f),
                ) { vm.update { it.copy(mode = ReplyMode.SUGGEST) } }
                ModePill(
                    label = "AUTO-SEND",
                    icon = Icons.Filled.Bolt,
                    selected = settings.mode == ReplyMode.AUTO_SEND,
                    modifier = Modifier.weight(1f),
                ) { confirmAutoSend = true }
            }
            if (settings.mode == ReplyMode.AUTO_SEND) {
                Text(
                    "Auto-send only fires for allowlisted people. Everyone else stays SUGGEST.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsCard("Sources") {
            SourceRow(
                "WhatsApp",
                settings.whatsappEnabled,
                permissionOk = notifOk,
            ) { v -> vm.update { it.copy(whatsappEnabled = v) } }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SourceRow(
                "Tinder",
                settings.tinderEnabled,
                permissionOk = a11yOk,
            ) { v -> vm.update { it.copy(tinderEnabled = v) } }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SourceRow(
                "Hinge",
                settings.hingeEnabled,
                permissionOk = a11yOk,
            ) { v -> vm.update { it.copy(hingeEnabled = v) } }
        }

        SettingsCard("AI Engine") {
            AiEngineEditor(vm)
            Text("Persona", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PersonaPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = settings.persona == preset.prompt,
                        onClick = {
                            personaDraft = preset.prompt
                            vm.update { it.copy(persona = preset.prompt) }
                        },
                        label = { Text(preset.label, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }
            OutlinedTextField(
                value = personaDraft,
                onValueChange = { personaDraft = it },
                textStyle = MaterialTheme.typography.bodyMedium,
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            if (personaDraft != settings.persona) {
                Button(
                    onClick = { vm.update { it.copy(persona = personaDraft) } },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Save persona", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        SettingsCard("Guardrails") {
            AutoReplyDelaySlider(
                ceilMs = settings.cadenceCeilMs,
                onChange = { seconds ->
                    vm.update {
                        it.copy(
                            // Keep a jitter window below the ceiling so sends never
                            // land on a fixed, bot-looking interval.
                            cadenceCeilMs = seconds * 1000L,
                            cadenceFloorMs = (seconds * 600L),
                        )
                    }
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StepSlider(
                label = "Replies per hour",
                value = settings.repliesPerHour,
                range = 1f..100f,
                steps = 98,
                format = { "$it / hour" },
                help = "Hard ceiling on automated sends in any rolling hour. " +
                    "Hitting it logs RATE_LIMITED and pauses auto-send until the hour rolls on.",
                onChange = { v -> vm.update { it.copy(repliesPerHour = v) } },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StepSlider(
                label = "Loop-breaker",
                value = settings.loopBreakerStreak,
                range = 1f..20f,
                steps = 18,
                format = { if (it == 1) "1 unanswered reply" else "$it unanswered replies" },
                help = "Stops auto-sending in a chat after this many replies the other " +
                    "person hasn't answered. Resets the moment they write back.",
                onChange = { v -> vm.update { it.copy(loopBreakerStreak = v) } },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            KeyValueRow("Send-verify cap", "${settings.sendVerifyMax} (fixed)")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-drop to SUGGEST on a11y probe", style = MaterialTheme.typography.bodyMedium)
                }
                Switch(
                    checked = settings.autoDropOnDetection,
                    onCheckedChange = { v -> vm.update { it.copy(autoDropOnDetection = v) } },
                )
            }
        }

        SettingsCard("Privacy & Data") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Retention", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 30, 90).forEach { days ->
                        val selected = settings.retentionDays == days
                        TextButton(onClick = { vm.update { it.copy(retentionDays = days) } }) {
                            Text(
                                "${days}d",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Text(
                "All data lives on-device. Nothing leaves your phone except the LLM call.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { vm.clearAllowlist() },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear allowlist", style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Delete all data now", style = MaterialTheme.typography.labelLarge)
            }
        }

        SettingsCard("Honesty Note") {
            Text(
                "This app reads your chats on-device to draft replies. The dating-app " +
                    "path uses accessibility scraping that those apps can detect and " +
                    "that breaks when they update. Nothing sends without you unless " +
                    "you explicitly enable auto-send for a specific person.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmAutoSend) {
        AlertDialog(
            onDismissRequest = { confirmAutoSend = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Enable AUTO-SEND?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "AI will send replies without review for allowlisted people. Higher " +
                        "risk of detection by dating apps and of awkward phrasing. " +
                        "Nothing sends until you allowlist someone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.update { it.copy(mode = ReplyMode.AUTO_SEND) }
                    confirmAutoSend = false
                }) { Text("Enable", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAutoSend = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete all data?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "Every stored message, conversation and audit entry will be erased. " +
                        "This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAllData()
                    confirmDelete = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScopeAlias.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content(this)
    }
}

private typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

@Composable
private fun ModePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        modifier = modifier,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun SourceRow(
    name: String,
    enabled: Boolean,
    permissionOk: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (permissionOk) StatusChip("PERMISSION OK", Secondary, Icons.Filled.CheckCircle)
            else StatusChip("PERMISSION MISSING", MaterialTheme.colorScheme.tertiary, Icons.Filled.Warning)
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun KeyValueRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Editable provider/model/key block, mirroring the onboarding LLM step. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiEngineEditor(vm: CueViewModel) {
    val settings by vm.settings.collectAsState()
    val testState by vm.llmTest.collectAsState()
    var apiKey by rememberSaveable(settings.provider, settings.activeApiKey) {
        mutableStateOf(settings.activeApiKey)
    }
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    val modelOptions = LlmCatalog.modelsFor(settings.provider)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Provider", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LlmProvider.entries.forEach { p ->
                FilterChip(
                    selected = settings.provider == p,
                    onClick = {
                        vm.update { it.copy(provider = p) }
                        vm.resetLlmTest()
                    },
                    label = { Text(p.displayName, style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        Text("Model", style = MaterialTheme.typography.labelLarge)
        ExposedDropdownMenuBox(
            expanded = modelMenuOpen,
            onExpandedChange = { modelMenuOpen = it },
        ) {
            OutlinedTextField(
                value = modelOptions.firstOrNull { it.first == settings.activeModel }?.second
                    ?: settings.activeModel,
                onValueChange = {},
                readOnly = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuOpen) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = modelMenuOpen,
                onDismissRequest = { modelMenuOpen = false },
            ) {
                modelOptions.forEach { (id, label) ->
                    DropdownMenuItem(
                        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            vm.update {
                                it.withActiveModel(id)
                            }
                            vm.resetLlmTest()
                            modelMenuOpen = false
                        },
                    )
                }
            }
        }

        Text("API key", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                vm.update { s ->
                    s.withActiveApiKey(it.trim())
                }
                vm.resetLlmTest()
            },
            placeholder = {
                Text(settings.provider.keyHint, style = MaterialTheme.typography.bodyMedium)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.labelLarge,
            visualTransformation =
                if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (keyVisible) "Hide key" else "Show key",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { vm.testLlmConnection() },
                enabled = testState !is LlmTestUi.Testing && apiKey.isNotBlank(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Test connection", style = MaterialTheme.typography.labelLarge)
            }
            when (val t = testState) {
                LlmTestUi.Testing -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                )
                LlmTestUi.Verified ->
                    StatusChip("VERIFIED", Secondary, Icons.Filled.CheckCircle)
                is LlmTestUi.Failed ->
                    StatusChip("FAILED", MaterialTheme.colorScheme.error, Icons.Filled.Warning)
                LlmTestUi.Idle -> {}
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

/** Formats a delay for humans: "Immediate", "45s", "2m 30s". */
internal fun formatDelay(seconds: Int): String = when {
    seconds <= 0 -> "Immediate"
    seconds < 60 -> "${seconds}s"
    seconds % 60 == 0 -> "${seconds / 60}m"
    else -> "${seconds / 60}m ${seconds % 60}s"
}

/**
 * How long Cue waits before an auto-send. Zero fires immediately; anything
 * higher keeps a randomized window so replies don't land on a robotic beat.
 */
@Composable
private fun AutoReplyDelaySlider(ceilMs: Long, onChange: (Int) -> Unit) {
    val seconds = (ceilMs / 1000L).toInt().coerceIn(0, 300)
    var sliderValue by remember(seconds) { mutableStateOf(seconds.toFloat()) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Auto-reply delay", style = MaterialTheme.typography.bodyMedium)
            Text(
                formatDelay(sliderValue.toInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChange(sliderValue.toInt()) },
            valueRange = 0f..300f,
            steps = 59,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (sliderValue.toInt() == 0)
                "Replies send the moment a draft is ready. Fastest, but the most bot-like."
            else
                "Waits a random ${formatDelay((sliderValue.toInt() * 0.6f).toInt())}–" +
                    "${formatDelay(sliderValue.toInt())} so replies look typed, not triggered.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Integer slider with a live readout and a line explaining what the limit does. */
@Composable
private fun StepSlider(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: (Int) -> String,
    help: String,
    onChange: (Int) -> Unit,
) {
    var sliderValue by remember(value) { mutableStateOf(value.toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                format(sliderValue.toInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChange(sliderValue.toInt()) },
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            help,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
