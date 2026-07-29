package com.cue.reply.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.cue.reply.data.db.ActivityType
import com.cue.reply.data.db.entity.ActivityLogEntity
import com.cue.reply.ui.CueViewModel
import com.cue.reply.ui.theme.CueDuration
import com.cue.reply.ui.theme.CueEasing
import com.cue.reply.ui.theme.pressable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FILTERS: List<Pair<String, (ActivityLogEntity) -> Boolean>> = listOf(
    "ALL" to { _: ActivityLogEntity -> true },
    "RECEIVED" to { it.type == ActivityType.RECEIVED },
    "SUGGESTED" to { it.type == ActivityType.SUGGESTED },
    "INSERTED" to { it.type == ActivityType.INSERTED },
    "AUTO_SENT" to { it.type == ActivityType.AUTO_SENT },
    "BLOCKED" to {
        it.type in setOf(
            ActivityType.BLOCKED_NOT_ALLOWLISTED,
            ActivityType.RATE_LIMITED,
            ActivityType.SEND_FAILED,
            ActivityType.KILLED,
        )
    },
)

/** Activity / audit log — the trust surface (Stitch: Activity Log). */
@Composable
fun ActivityScreen(vm: CueViewModel) {
    val log by vm.activity.collectAsState(initial = emptyList())
    var filter by rememberSaveable { mutableStateOf("ALL") }

    val predicate = FILTERS.first { it.first == filter }.second
    val filtered = log.filter(predicate)

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
            Text("Audit Log", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Append-only record of everything the system did.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FILTERS.forEach { (name, _) ->
                val selected = filter == name
                Box(
                    modifier = Modifier
                        .background(
                            if (selected) com.cue.reply.ui.theme.PrimaryFixed
                            else MaterialTheme.colorScheme.surface,
                            CircleShape,
                        )
                        .border(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        )
                        .pressable { filter = name }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (log.isEmpty()) "No activity yet." else "Nothing matches this filter.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filtered, key = { it.id }) { entry -> TimelineRow(entry) }
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

@Composable
private fun TimelineRow(entry: ActivityLogEntity) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val (icon, tint) = entry.type.iconAndTint()
    // The chevron rotates rather than snapping — it's the affordance telling you
    // the row is open, so it should travel the same distance the row does.
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(CueDuration.CARD, easing = CueEasing.InOut),
        label = "chevron",
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(tint.copy(alpha = 0.12f), CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(8.dp))
                .border(
                    1.dp,
                    if (entry.type == ActivityType.BLOCKED_NOT_ALLOWLISTED ||
                        entry.type == ActivityType.SEND_FAILED
                    ) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(8.dp),
                )
                .pressable(role = null) { expanded = !expanded }
                // The row grows in place rather than jumping, so the timeline
                // below it slides instead of teleporting.
                .animateContentSize(tween(CueDuration.CARD, easing = CueEasing.InOut)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .background(tint.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                entry.type.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                            )
                        }
                        Text(
                            timeFormat.format(Date(entry.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        listOfNotNull(
                            entry.contactName,
                            entry.service?.displayName?.let { "($it)" },
                        ).joinToString(" ").ifBlank { entry.headline() },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(chevron),
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Filled.FormatQuote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            entry.detail ?: "No further detail recorded.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = if (entry.detail == null) FontStyle.Italic else FontStyle.Normal,
                        )
                    }
                    entry.threadKey?.let {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainer,
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(8.dp),
                        ) {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
