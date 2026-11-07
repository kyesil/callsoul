package com.bettercallsoul.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bettercallsoul.calllog.WhatsAppKind
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.BarChart
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettercallsoul.R
import com.bettercallsoul.call.TagDialog
import com.bettercallsoul.calllog.CallEntry
import com.bettercallsoul.calllog.CallType
import com.bettercallsoul.container
import com.bettercallsoul.telecom.CallerDirectory

@Composable
fun RecentsScreen(onCall: (String) -> Unit, onOpen: (Route) -> Unit) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(CallFilter.All) }
    val recents = rememberRecents(query = query, kind = kind)
    var tagging by remember { mutableStateOf<String?>(null) }
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(searching) {
        if (searching) runCatching { focus.requestFocus() }
    }
    Column(Modifier.fillMaxSize()) {
        if (picked.isNotEmpty()) {
            SelectionBar(
                count = picked.size,
                onClear = { picked = emptySet() },
                onSelectAll = { picked = recents.rows.map { it.key }.toSet() },
                onDelete = { confirmDelete = true },
            )
        } else ToolbarRow {
            if (searching) {
                CompactTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.search_calls_hint),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.weight(1f).padding(start = 8.dp).focusRequester(focus),
                )
                ToolbarIcon(Icons.Filled.Close, stringResource(R.string.action_cancel)) {
                    query = ""
                    searching = false
                }
            } else {
                ToolbarIcon(Icons.Outlined.BarChart, stringResource(R.string.stats_title)) { onOpen(Route.Stats) }
                ToolbarIcon(Icons.Filled.Search, stringResource(R.string.search_calls)) { searching = true }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (!searching) item { LargeTitle(stringResource(R.string.tab_recents), null) }
            item {
                // Narrow to one kind of call; tapping the chosen chip again shows them all. The row
                // slides sideways when the chips don't fit.
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CallFilter.entries
                        .filter { it != CallFilter.All && (it != CallFilter.WhatsApp || app.whatsAppCallsEnabled || kind == it) }
                        .forEach { filter ->
                        FilterChip(
                            selected = kind == filter,
                            onClick = { kind = if (kind == filter) CallFilter.All else filter },
                            label = { Text(stringResource(filter.label)) },
                        )
                    }
                }
            }
            recentCalls(recents, onCall, onOpen, onTag = { tagging = it }, picked = picked, onPick = { row ->
                picked = if (row.key in picked) picked - row.key else picked + row.key
            })
        }
    }
    tagging?.let { TagDialog(number = it, onDismiss = { tagging = null }) }
    if (confirmDelete) {
        DeleteCallsDialog(recents.callIds(picked).size, onDismiss = { confirmDelete = false }) {
            val ids = recents.callIds(picked)
            confirmDelete = false
            picked = emptySet()
            scope.launch { app.deleteCalls(ids) }
        }
    }
}

@Composable
fun DeleteCallsDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ConfirmDialog(
        message = pluralStringResource(R.plurals.delete_calls_question, count, count),
        icon = Icons.Outlined.Delete,
        action = stringResource(R.string.action_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

enum class CallFilter(val label: Int) {
    All(R.string.filter_all),
    Missed(R.string.filter_missed),
    Incoming(R.string.filter_incoming),
    Outgoing(R.string.filter_outgoing),
    WhatsApp(R.string.whatsapp),
    ;

    fun matches(call: CallEntry): Boolean = when (this) {
        All -> true
        Missed -> call.type == CallType.Missed || call.type == CallType.Rejected
        Incoming -> call.type == CallType.Incoming
        Outgoing -> call.type == CallType.Outgoing
        WhatsApp -> call.whatsApp != null
    }
}

/** One row of the list: back-to-back calls with one number, newest first, shown as [entry]. */
class RecentRow(val entry: CallEntry, val ids: List<Long>) {
    val count: Int get() = ids.size
    val key: Long get() = entry.id
}

/** Calls grouped by day, repeats collapsed, and the labels given to numbers outside the contacts. */
class RecentsState(val days: List<Pair<String, List<RecentRow>>>, val labels: Map<String, String>) {
    val rows: List<RecentRow> get() = days.flatMap { it.second }

    /** The call log ids behind the picked rows. */
    fun callIds(picked: Set<Long>): List<Long> = rows.filter { it.key in picked }.flatMap { it.ids }
}

@Composable
fun rememberRecents(limit: Int? = null, query: String = "", kind: CallFilter = CallFilter.All): RecentsState {
    val context = LocalContext.current
    val app = context.container
    val calls by remember { app.observeCalls() }.collectAsStateWithLifecycle(emptyList())
    val tags by remember { app.store.observeTags() }.collectAsStateWithLifecycle(emptyList())
    val labels = remember(tags) { tags.associate { it.number to it.label } }
    val days = remember(calls, limit, query, kind, labels) {
        // A search matches the name, the label given to a number, or digits anywhere in the number.
        val text = query.trim()
        val digits = text.filter(Char::isDigit)
        val shown = calls.filter { entry ->
            kind.matches(entry) && (
                text.isEmpty() ||
                    entry.name?.contains(text, ignoreCase = true) == true ||
                    labels[CallerDirectory.keyOf(entry.number)]?.contains(text, ignoreCase = true) == true ||
                    (digits.isNotEmpty() && entry.number.filter(Char::isDigit).contains(digits))
                )
        }
        val rows = collapseRepeats(shown).let { if (limit == null) it else it.take(limit) }
        rows.groupBy { dayHeader(context, it.entry.date) }.toList()
    }
    return RecentsState(days, labels)
}

fun LazyListScope.recentCalls(
    state: RecentsState,
    onCall: (String) -> Unit,
    onOpen: (Route) -> Unit,
    onTag: (String) -> Unit,
    picked: Set<Long> = emptySet(),
    onPick: (RecentRow) -> Unit = {},
) {
    if (state.days.isEmpty()) {
        item { EmptyRecents() }
    }
    state.days.forEach { (day, rows) ->
        item(key = "header-$day") { DayHeader(day) }
        item(key = "group-$day") {
            Card {
                rows.forEach { row ->
                    CallRow(
                        entry = row.entry,
                        count = row.count,
                        label = state.labels[CallerDirectory.keyOf(row.entry.number)],
                        onCall = onCall,
                        onOpen = onOpen,
                        onTag = onTag,
                        selecting = picked.isNotEmpty(),
                        selected = row.key in picked,
                        onPick = { onPick(row) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRecents() {
    Text(
        stringResource(R.string.recents_empty),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

/** Back-to-back calls with one number become a single row with a count. */
internal fun collapseRepeats(entries: List<CallEntry>): List<RecentRow> {
    val rows = mutableListOf<RecentRow>()
    for (entry in entries) {
        val last = rows.lastOrNull()
        val same = last != null && CallerDirectory.keyOf(last.entry.number) == CallerDirectory.keyOf(entry.number) &&
            last.entry.type == entry.type && last.entry.whatsApp == entry.whatsApp && last.entry.name == entry.name
        if (last != null && same) {
            rows[rows.lastIndex] = RecentRow(last.entry, last.ids + entry.id)
        } else {
            rows += RecentRow(entry, listOf(entry.id))
        }
    }
    return rows
}

@Composable
fun DayHeader(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 22.dp, top = 12.dp, bottom = 6.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallRow(
    entry: CallEntry,
    count: Int,
    label: String?,
    onCall: (String) -> Unit,
    onOpen: (Route) -> Unit,
    onTag: (String) -> Unit,
    selecting: Boolean,
    selected: Boolean,
    onPick: () -> Unit,
) {
    val context = LocalContext.current

    val hasNumber = entry.number.isNotBlank()
    val scope = rememberCoroutineScope()
    SwipeRow(
        onCall = { scope.launch { callBack(context, entry, onCall) } },
        onMessage = { sendSwipeMessage(context, entry.number) },
        enabled = !selecting && hasNumber,
    ) {
        val name = entry.name ?: label ?: entry.number
        val title = if (count > 1) "$name ($count)" else name
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .stretchy(strength = 0.35f, follow = false)
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                // A long press starts picking rows; while picking, a tap picks or drops one.
                .combinedClickable(
                    onClick = { if (selecting) onPick() else if (hasNumber) onOpen(Route.Number(entry.number)) },
                    onLongClick = onPick,
                )
                .heightIn(min = 54.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CallKindIcon(entry)
            Spacer(Modifier.width(10.dp))
            // A saved contact shows its photo (or initial); any other number gets a button to label it.
            val contactName = entry.name
            if (selected) {
                SelectedMark(36.dp)
            } else if (contactName != null) {
                Avatar(contactName, entry.photoUri, 36.dp)
            } else {
                Surface(
                    onClick = { if (selecting) onPick() else onTag(entry.number) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(36.dp).stretchy(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Label,
                        contentDescription = stringResource(if (label != null) R.string.tag_edit else R.string.tag_add),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ink = if (entry.type == CallType.Missed) EndRed else MaterialTheme.colorScheme.onSurface
                    Text(
                        title,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = ink,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (entry.name == null && label != null) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.AutoMirrored.Outlined.Label,
                            contentDescription = stringResource(R.string.tag),
                            tint = ink.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                if (entry.name == null && label != null) {
                    Text(entry.number, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(10.dp))
            CallTimes(entry)
        }
    }
}

/**
 * What starts a call's row: the direction arrow, or for WhatsApp a phone or a camera in its green
 * (red when missed), with the arrow small in the corner.
 */
@Composable
fun CallKindIcon(entry: CallEntry) {
    val (arrow, tint) = typeIcon(entry.type)
    val kind = entry.whatsApp
    if (kind == null) {
        Icon(arrow, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        return
    }
    val missed = entry.type == CallType.Missed
    Box(Modifier.size(22.dp)) {
        Icon(
            if (kind == WhatsAppKind.Video) Icons.Filled.Videocam else Icons.Filled.Call,
            stringResource(if (kind == WhatsAppKind.Video) R.string.whatsapp_video else R.string.whatsapp_voice),
            tint = if (missed) EndRed else WhatsAppGreen,
            modifier = Modifier.size(18.dp),
        )
        Icon(arrow, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp).align(Alignment.BottomEnd))
    }
}

@Composable
fun typeIcon(type: CallType): Pair<ImageVector, Color> {
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    return when (type) {
        CallType.Incoming -> Icons.AutoMirrored.Filled.CallReceived to neutral
        CallType.Outgoing -> Icons.AutoMirrored.Filled.CallMade to neutral
        CallType.Missed, CallType.Rejected -> Icons.AutoMirrored.Filled.CallMissed to EndRed
        CallType.Blocked -> Icons.Filled.Block to EndRed
        CallType.Other -> Icons.Filled.Call to neutral
    }
}
