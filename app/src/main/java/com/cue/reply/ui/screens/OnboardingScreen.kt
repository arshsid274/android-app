package com.cue.reply.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cue.reply.data.settings.LlmProvider
import com.cue.reply.data.settings.ReplyMode
import com.cue.reply.llm.LlmCatalog
import com.cue.reply.system.Permissions
import com.cue.reply.ui.CueViewModel
import com.cue.reply.ui.LlmTestUi
import com.cue.reply.ui.components.StatusChip
import com.cue.reply.ui.components.rememberOnResume
import com.cue.reply.ui.theme.GridPatternBackground
import com.cue.reply.ui.theme.PrimaryFixed
import com.cue.reply.ui.theme.Secondary

private const val TOTAL_STEPS = 6

/**
 * First-run wizard. Steps: Welcome → Notification access → Accessibility →
 * Background & overlay → LLM setup → Mode choice. Mirrors the Stitch
 * "Welcome", "LLM Setup" and "Onboarding Mode Choice" screens; the permission
 * steps reuse the same card language.
 */
@Composable
fun OnboardingScreen(vm: CueViewModel, onFinished: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(1) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GridPatternBackground(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            OnboardingCard(step = step) {
                when (step) {
                    1 -> WelcomeStep(onNext = { step = 2 })
                    2 -> PermissionStep(
                        icon = Icons.Filled.NotificationsActive,
                        title = "Notification Access",
                        body = "Needed to read incoming WhatsApp messages. Cue only reads " +
                            "messaging notifications and never posts its own into your chats.",
                        optionalNote = null,
                        granted = { Permissions.notificationAccessGranted(it) },
                        grantIntent = { Permissions.notificationAccessIntent() },
                        actionLabel = "Grant notification access",
                        onBack = { step = 1 },
                        onNext = { step = 3 },
                    )
                    3 -> PermissionStep(
                        icon = Icons.Filled.Accessibility,
                        title = "Accessibility Service",
                        body = "Needed to read Tinder and Hinge chats. Honest note: this is " +
                            "detectable by those apps and can break when they update. " +
                            "Skip it if you only want WhatsApp.",
                        optionalNote = "Optional — skip if you only use WhatsApp",
                        granted = { Permissions.accessibilityEnabled(it) },
                        grantIntent = { Permissions.accessibilityIntent() },
                        actionLabel = "Open Accessibility settings",
                        onBack = { step = 2 },
                        onNext = { step = 4 },
                    )
                    4 -> BackgroundStep(onBack = { step = 3 }, onNext = { step = 5 })
                    5 -> LlmSetupStep(vm = vm, onBack = { step = 4 }, onNext = { step = 6 })
                    6 -> ModeChoiceStep(vm = vm, onBack = { step = 5 }, onFinish = {
                        vm.completeOnboarding()
                        onFinished()
                    })
                }
            }
            if (step == 1) {
                TextButton(onClick = {
                    vm.completeOnboarding()
                    onFinished()
                }) {
                    Text(
                        "SKIP TO HOME (NOT RECOMMENDED)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingCard(step: Int, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            CircleShape,
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        "STEP $step OF $TOTAL_STEPS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.10f),
                    RoundedCornerShape(16.dp),
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f),
                    RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Memory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("Sentience Core", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Your on-device AI messaging assistant. Reads chats locally to draft " +
                "perfect replies. Nothing sends without your approval unless you " +
                "enable auto-send.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("Get Started", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.size(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PermissionStep(
    icon: ImageVector,
    title: String,
    body: String,
    optionalNote: String?,
    granted: (Context) -> Boolean,
    grantIntent: () -> Intent,
    actionLabel: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val isGranted = rememberOnResume { granted(context) }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        StepHeader(icon = icon, title = title)
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        optionalNote?.let {
            Spacer(Modifier.height(8.dp))
            StatusChip(it, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "STATUS:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            if (isGranted) {
                StatusChip("GRANTED", Secondary, Icons.Filled.CheckCircle, outlined = true)
            } else {
                StatusChip("NOT GRANTED", MaterialTheme.colorScheme.error, Icons.Filled.Warning, outlined = true)
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { context.startActivitySafe(grantIntent()) },
            enabled = !isGranted,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                if (isGranted) "Already granted" else actionLabel,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        StepFooter(onBack = onBack, onNext = onNext, nextLabel = if (isGranted) "Next Step" else "Skip for now")
    }
}

@Composable
private fun BackgroundStep(onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val batteryOk = rememberOnResume { Permissions.batteryOptimizationIgnored(context) }
    val overlayOk = rememberOnResume { Permissions.overlayGranted(context) }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        StepHeader(icon = Icons.Filled.BatteryChargingFull, title = "Keep It Running")
        Spacer(Modifier.height(12.dp))
        Text(
            "Two system exemptions keep the reader alive in the background and let " +
                "draft cards float over your chat apps.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        GrantRow(
            title = "Battery optimization exemption",
            granted = batteryOk,
            onGrant = { context.startActivitySafe(Permissions.batteryOptimizationIntent(context)) },
        )
        Spacer(Modifier.height(8.dp))
        GrantRow(
            title = "Draw over other apps (overlay)",
            granted = overlayOk,
            onGrant = { context.startActivitySafe(Permissions.overlayIntent(context)) },
        )
        StepFooter(onBack = onBack, onNext = onNext, nextLabel = "Next Step")
    }
}

@Composable
private fun GrantRow(title: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
        if (granted) {
            StatusChip("GRANTED", Secondary, Icons.Filled.CheckCircle)
        } else {
            TextButton(onClick = onGrant) {
                Text("GRANT", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LlmSetupStep(vm: CueViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val testState by vm.llmTest.collectAsState()
    var apiKey by rememberSaveable(settings.provider, settings.activeApiKey) {
        mutableStateOf(settings.activeApiKey)
    }
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    val modelOptions = LlmCatalog.modelsFor(settings.provider)

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        StepHeader(icon = Icons.Filled.Key, title = "Configure AI Engine")
        Spacer(Modifier.height(12.dp))
        Text(
            "Sentience Core requires an LLM to generate reply drafts. Your API key " +
                "is stored on-device only.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Text("Provider", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
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

        Spacer(Modifier.height(16.dp))
        Text("Model", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
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
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
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

        Spacer(Modifier.height(16.dp))
        Text("API Key", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
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
            textStyle = MaterialTheme.typography.labelLarge,
            visualTransformation =
                if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (keyVisible) "Hide key" else "Show key",
                    )
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { vm.testLlmConnection() },
                enabled = testState !is LlmTestUi.Testing && apiKey.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Test Connection", style = MaterialTheme.typography.labelLarge)
            }
            when (val t = testState) {
                LlmTestUi.Testing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(6.dp))
                    Text("TESTING…", style = MaterialTheme.typography.labelSmall)
                }
                LlmTestUi.Verified ->
                    StatusChip("CONNECTION VERIFIED", Secondary, Icons.Filled.CheckCircle)
                is LlmTestUi.Failed ->
                    StatusChip("FAILED", MaterialTheme.colorScheme.error, Icons.Filled.Warning)
                LlmTestUi.Idle -> {}
            }
        }
        (testState as? LlmTestUi.Failed)?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it.reason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        StepFooter(onBack = onBack, onNext = onNext, nextLabel = "Next Step")
    }
}

@Composable
private fun ModeChoiceStep(vm: CueViewModel, onBack: () -> Unit, onFinish: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val suggestSelected = settings.mode == ReplyMode.SUGGEST

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Choose Your Mode", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Decide how Sentience Core should handle your replies. You can change " +
                "this anytime in settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        ModeCard(
            selected = suggestSelected,
            icon = Icons.Filled.Lightbulb,
            title = "SUGGEST",
            titleBadge = "(RECOMMENDED)",
            body = "Drafts appear in a floating overlay. You review, edit, and tap to " +
                "send. Safest way to use AI.",
            onClick = { vm.update { it.copy(mode = ReplyMode.SUGGEST) } },
        )
        Spacer(Modifier.height(12.dp))
        ModeCard(
            selected = !suggestSelected,
            icon = Icons.Filled.Bolt,
            title = "AUTO-SEND",
            titleBadge = "ADVANCED · REQUIRES ALLOWLIST",
            body = "AI sends replies automatically based on your allowlist. Higher risk " +
                "of detection or awkward phrasing. Nothing sends until you allowlist " +
                "someone.",
            onClick = { vm.update { it.copy(mode = ReplyMode.AUTO_SEND) } },
            badgeColor = MaterialTheme.colorScheme.onErrorContainer,
            badgeBg = MaterialTheme.colorScheme.errorContainer,
        )

        StepFooter(onBack = onBack, onNext = onFinish, nextLabel = "Finish Setup", nextIcon = Icons.Filled.Check)
    }
}

@Composable
private fun ModeCard(
    selected: Boolean,
    icon: ImageVector,
    title: String,
    titleBadge: String,
    body: String,
    onClick: () -> Unit,
    badgeColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    badgeBg: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
) {
    val borderColor =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val bg = if (selected) PrimaryFixed else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.size(8.dp))
                    Box(
                        Modifier
                            .background(badgeBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            titleBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                        )
                    }
                }
                if (selected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.10f),
                    RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun StepFooter(
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextLabel: String,
    nextIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowForward,
) {
    Spacer(Modifier.height(24.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                "Back",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onNext, shape = RoundedCornerShape(8.dp)) {
            Text(nextLabel, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.size(6.dp))
            Icon(nextIcon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

internal fun Context.startActivitySafe(intent: Intent) {
    runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
