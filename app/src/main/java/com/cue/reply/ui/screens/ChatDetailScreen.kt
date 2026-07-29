package com.cue.reply.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cue.reply.data.db.entity.MessageEntity
import com.cue.reply.data.settings.ReplyMode
import com.cue.reply.domain.model.DeliveryResult
import com.cue.reply.domain.model.MessageDirection
import com.cue.reply.domain.model.ServiceType
import com.cue.reply.llm.PersonaPreset
import com.cue.reply.ui.CueViewModel
import com.cue.reply.ui.DraftsUi
import com.cue.reply.ui.components.ContactAvatar
import com.cue.reply.ui.components.PulsingDot
import com.cue.reply.ui.components.timeAgo
import com.cue.reply.ui.theme.CueDuration
import com.cue.reply.ui.theme.CueEasing
import com.cue.reply.ui.theme.Secondary
import com.cue.reply.ui.theme.SecondaryContainer
import com.cue.reply.ui.theme.pressable
import com.cue.reply.ui.theme.reducedMotion
import com.cue.reply.ui.theme.staggerDelay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fades and lifts a child into place after a stagger delay. Entry starts at 0.96
 * scale rather than 0 — nothing in the real world appears from nothing — and the
 * whole effect collapses to an instant show when the OS asks for reduced motion.
 */
@Composable
private fun StaggeredEntry(
    index: Int,
    key: Any,
    content: @Composable () -> Unit,
) {
    val flat = reducedMotion()
    var shown by remember(key) { mutableStateOf(flat) }
    LaunchedEffect(key) {
        if (!flat) {
            delay(staggerDelay(index).toLong())
            shown = true
        }
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(CueDuration.CARD, easing = CueEasing.Out),
        label = "entry",
    )
    Box(
        Modifier
            .graphicsLayer {
                alpha = progress
                scaleX = 0.96f + 0.04f * progress
                scaleY = 0.96f + 0.04f * progress
                translationY = (1f - progress) * 12.dp.toPx()
            }
    ) { content() }
}

/** Conversation detail: reconstructed thread + draft panel (Stitch: Conversation with Sarah). */
@Composable
fun ChatDetailScreen(vm: CueViewModel, threadKey: String, onBack: () -> Unit) {
    val messages by vm.messages(threadKey).collectAsState(initial = emptyList())
    val conversation by vm.conversation(threadKey).collectAsState(initial = null)
    val settings by vm.settings.collectAsState()
    val draftsMap by vm.drafts.collectAsState()
    val draftsUi = draftsMap[threadKey] ?: DraftsUi.Idle

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(
            contactName = conversation?.contactName ?: conversation?.contactKey ?: "…",
            service = conversation?.service,
            allowlisted = conversation?.allowlisted == true,
            muted = conversation?.muted == true,
            killed = settings.killed,
            persona = PersonaPreset.byName(conversation?.personaPreset),
            onBack = onBack,
            onToggleKill = { vm.toggleKill() },
            onToggleAllowlist = { vm.setAllowlisted(threadKey, it) },
            onToggleMute = { vm.setMuted(threadKey, it) },
            onSelectPersona = { vm.setThreadPersona(threadKey, it) },
            onClearHistory = {
                vm.deleteThread(threadKey)
                onBack()
            },
        )

        if (settings.mode == ReplyMode.AUTO_SEND && conversation?.allowlisted != true) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "Auto-send is on, but this contact isn't allowlisted — replies stay drafts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.setAllowlisted(threadKey, true) }) {
                    Text("ALLOW", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    conversation?.service?.let { service ->
                        if (service in ServiceType.accessibilityBacked) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Row(
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape,
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Sync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        "Scraped from ${service.displayName} — order may differ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                items(messages, key = { it.id }) { m -> MessageBubble(m) }
                if (messages.isEmpty()) {
                    item {
                        Text(
                            "No messages captured for this thread yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }

        DraftPanel(
            mode = settings.mode,
            allowlisted = conversation?.allowlisted == true,
            draftsUi = draftsUi,
            hasHistory = messages.isNotEmpty(),
            onRegenerate = { steer -> vm.generateDrafts(threadKey, steer) },
            onCopy = { text ->
                clipboard.setText(AnnotatedString(text))
                scope.launch { snackbar.showSnackbar("Copied to clipboard") }
            },
            onInsert = { text ->
                val service = conversation?.service ?: return@DraftPanel
                vm.insertDraft(threadKey, service, text) { result ->
                    scope.launch {
                        snackbar.showSnackbar(
                            when (result) {
                                is DeliveryResult.Inserted -> "Inserted into ${service.displayName}"
                                is DeliveryResult.Sent -> "Sent"
                                is DeliveryResult.Failed -> "Insert failed: ${result.reason}"
                                is DeliveryResult.Blocked -> "Blocked: ${result.reason}"
                            }
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun DetailTopBar(
    contactName: String,
    service: ServiceType?,
    allowlisted: Boolean,
    muted: Boolean,
    killed: Boolean,
    persona: PersonaPreset?,
    onBack: () -> Unit,
    onToggleKill: () -> Unit,
    onToggleAllowlist: (Boolean) -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onSelectPersona: (PersonaPreset?) -> Unit,
    onClearHistory: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            if (service != null) {
                ContactAvatar(name = contactName, service = service, size = 40)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    contactName,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                )
                service?.let {
                    Text(
                        it.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onToggleKill) {
                Icon(
                    Icons.Filled.PowerSettingsNew,
                    contentDescription = if (killed) "Resume" else "Kill switch",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Allowlist contact", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.width(12.dp))
                                Switch(checked = allowlisted, onCheckedChange = onToggleAllowlist)
                            }
                        },
                        leadingIcon = { Icon(Icons.Filled.VerifiedUser, contentDescription = null) },
                        onClick = { onToggleAllowlist(!allowlisted) },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (muted) "Unmute this thread" else "Mute this thread",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.NotificationsOff, contentDescription = null) },
                        onClick = {
                            onToggleMute(!muted)
                            menuOpen = false
                        },
                    )
                    HorizontalDivider()
                    Text(
                        "PERSONA FOR THIS CHAT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Follow global setting",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (persona == null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        trailingIcon = {
                            if (persona == null) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = {
                            onSelectPersona(null)
                            menuOpen = false
                        },
                    )
                    PersonaPreset.entries.forEach { preset ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    preset.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (persona == preset) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            trailingIcon = {
                                if (persona == preset) Icon(Icons.Filled.Check, contentDescription = null)
                            },
                            onClick = {
                                onSelectPersona(preset)
                                menuOpen = false
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Clear history",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onClearHistory()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(m: MessageEntity) {
    val inbound = m.direction == MessageDirection.INCOMING
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (inbound) Alignment.Start else Alignment.End,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (inbound) MaterialTheme.colorScheme.surfaceContainerHigh
                    else MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(
                        topStart = if (inbound) 4.dp else 16.dp,
                        topEnd = if (inbound) 16.dp else 4.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp,
                    ),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                m.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (inbound) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            if (!inbound) {
                Icon(
                    Icons.Filled.DoneAll,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                timeAgo(m.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val TONES = listOf(
    Triple("Warmer", Icons.Filled.Mood, "make it warmer and more affectionate"),
    Triple("Shorter", Icons.Filled.ShortText, "make it much shorter"),
    Triple("Funnier", Icons.Filled.Mood, "make it funnier and more playful"),
)

@Composable
private fun DraftPanel(
    mode: ReplyMode,
    allowlisted: Boolean,
    draftsUi: DraftsUi,
    hasHistory: Boolean,
    onRegenerate: (String?) -> Unit,
    onCopy: (String) -> Unit,
    onInsert: (String) -> Unit,
) {
    var selectedTone by rememberSaveable { mutableStateOf<String?>(null) }

    Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth()) {
            // Mode bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SecondaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PulsingDot(color = Secondary, size = 8)
                Text(
                    if (mode == ReplyMode.SUGGEST)
                        "SUGGEST: NOTHING SENDS UNTIL YOU TAP"
                    else if (allowlisted) "AUTO-SEND ON FOR THIS MATCH"
                    else "AUTO-SEND MODE — THIS THREAD NOT ALLOWLISTED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            // Tone chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Tone:", style = MaterialTheme.typography.labelLarge)
                TONES.forEach { (label, icon, steer) ->
                    val selected = selectedTone == label
                    Row(
                        modifier = Modifier
                            .background(
                                if (selected) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.surface,
                                CircleShape,
                            )
                            .border(
                                1.dp,
                                if (selected) androidx.compose.ui.graphics.Color.Transparent
                                else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            )
                            .pressable {
                                selectedTone = if (selected) null else label
                                if (hasHistory) onRegenerate(if (selected) null else steer)
                            }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Drafts
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "SUGGESTED REPLIES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .pressable(enabled = hasHistory) {
                                onRegenerate(TONES.firstOrNull { it.first == selectedTone }?.third)
                            }
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "Regenerate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                when (draftsUi) {
                    DraftsUi.Idle -> Text(
                        if (hasHistory) "Tap regenerate to draft replies."
                        else "Waiting for their reply.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DraftsUi.Loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            "Drafting…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is DraftsUi.Error -> Text(
                        "Couldn't draft — ${draftsUi.reason}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    is DraftsUi.Ready -> draftsUi.drafts.forEachIndexed { index, draft ->
                        // Drafts land together after the model returns, so they
                        // cascade rather than pop in as a block. Keyed on the draft
                        // text so a regenerate replays the entrance for new content.
                        StaggeredEntry(index = index, key = draft.id + draft.text) {
                            DraftCard(
                                text = draft.text,
                                highlighted = index == 0,
                                onCopy = { onCopy(draft.text) },
                                onInsert = { onInsert(draft.text) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftCard(
    text: String,
    highlighted: Boolean,
    onCopy: () -> Unit,
    onInsert: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    if (highlighted) MaterialTheme.colorScheme.primary
                    else androidx.compose.ui.graphics.Color.Transparent,
                    RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                )
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
        IconButton(onClick = onCopy) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Copy",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Row(
            modifier = Modifier
                .padding(end = 8.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .pressable(onClick = onInsert)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Insert",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
