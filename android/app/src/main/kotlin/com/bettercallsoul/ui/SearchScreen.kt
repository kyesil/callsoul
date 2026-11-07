package com.bettercallsoul.ui

import android.content.Context
import android.widget.Toast
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettercallsoul.R
import com.bettercallsoul.calllog.CallEntry
import com.bettercallsoul.calllog.SearchableContact
import com.bettercallsoul.container
import com.bettercallsoul.spam.SearchText
import com.bettercallsoul.telecom.CallerDirectory

private val Corner = 22.dp

/** A contact that matched, with the line to show under the name: what matched, else its number. */
private class ContactResult(val contact: SearchableContact, val line: String?)

private fun matchContacts(contacts: List<SearchableContact>, text: String): List<ContactResult> =
    contacts.mapNotNull { contact ->
        val other = (contact.numbers + contact.details).firstOrNull { SearchText.find(it, text) != null }
        when {
            SearchText.find(contact.name, text) != null -> ContactResult(contact, contact.numbers.firstOrNull())
            other != null -> ContactResult(contact, other)
            else -> null
        }
    }.sortedBy { SearchText.find(it.contact.name, text)?.first != 0 }

/**
 * One search over everything: contacts first, then calls, each in its own section with a count and
 * a way to fold it, the matching letters coloured. [query] lives in the main screen, so it is still
 * there on the way back from a result.
 */
@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    focusField: Boolean,
    onFieldFocused: () -> Unit,
    onCall: (String) -> Unit,
    onOpen: (Route) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.container
    val scope = rememberCoroutineScope()
    val contacts by remember { app.contacts.observeSearchable() }.collectAsStateWithLifecycle(emptyList())
    val calls by remember { app.observeCalls(limit = 1000) }.collectAsStateWithLifecycle(emptyList())
    val tags by remember { app.store.observeTags() }.collectAsStateWithLifecycle(emptyList())
    val labels = remember(tags) { tags.associate { it.number to it.label } }

    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val list = rememberLazyListState()
    LaunchedEffect(focusField) {
        if (!focusField) return@LaunchedEffect
        withFrameNanos { }
        runCatching { focus.requestFocus() }
        keyboard?.show()
        onFieldFocused()
    }
    LaunchedEffect(list.isScrollInProgress) {
        if (list.isScrollInProgress) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    BackHandler(onBack = onBack)

    val text = query.trim()
    // Only the accounts the contact list is narrowed to, so what is hidden there stays hidden here.
    var inSources by remember { mutableStateOf<Set<Long>?>(null) }
    LaunchedEffect(contacts) {
        val keys = app.contactsSources
        val chosen = app.contacts.sources().filter { it.key in keys }
        inSources = if (chosen.isEmpty()) null else chosen.flatMapTo(HashSet()) { app.contacts.contactIdsIn(it) }
    }
    val people = remember(contacts, inSources, text) {
        val pool = inSources?.let { ids -> contacts.filter { it.id in ids } } ?: contacts
        if (text.isEmpty()) emptyList() else matchContacts(pool, text)
    }
    val rows = remember(calls, labels, text) {
        if (text.isEmpty()) {
            emptyList()
        } else {
            collapseRepeats(
                calls.filter { call ->
                    listOfNotNull(call.name, labels[CallerDirectory.keyOf(call.number)], call.number)
                        .any { SearchText.find(it, text) != null }
                },
            )
        }
    }
    var peopleOpen by rememberSaveable { mutableStateOf(true) }
    var callsOpen by rememberSaveable { mutableStateOf(true) }

    // A long press starts picking results: contacts go to the recycle bin, calls out of the log.
    var pickedContacts by remember { mutableStateOf(emptySet<Long>()) }
    var pickedCalls by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = pickedContacts.isNotEmpty() || pickedCalls.isNotEmpty()
    val pickContact: (Long) -> Unit = { id -> pickedContacts = if (id in pickedContacts) pickedContacts - id else pickedContacts + id }
    val pickCall: (Long) -> Unit = { key -> pickedCalls = if (key in pickedCalls) pickedCalls - key else pickedCalls + key }

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            SelectionBar(
                count = pickedContacts.size + pickedCalls.size,
                onClear = {
                    pickedContacts = emptySet()
                    pickedCalls = emptySet()
                },
                onSelectAll = {
                    pickedContacts = people.mapTo(HashSet()) { it.contact.id }
                    pickedCalls = rows.mapTo(HashSet()) { it.key }
                },
                onDelete = { confirmDelete = true },
            )
        } else Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).focusRequester(focus),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.search_all_hint),
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        field()
                    }
                },
            )
            if (query.isNotEmpty()) {
                ToolbarIcon(Icons.Filled.Close, stringResource(R.string.action_cancel)) {
                    onQueryChange("")
                    runCatching { focus.requestFocus() }
                    keyboard?.show()
                }
            }
        }
        val keyboardRoom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = keyboardRoom + 12.dp)) {
            if (text.isEmpty() && app.recentSearches.isNotEmpty()) {
                item(key = "recent") {
                    Text(
                        stringResource(R.string.search_recent),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 22.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
                    )
                }
                itemsIndexed(app.recentSearches, key = { _, it -> "recent-$it" }) { index, search ->
                    Grouped(index, app.recentSearches.size) {
                        RecentSearchRow(search, onClick = { onQueryChange(search) }, onRemove = { app.forgetSearch(search) })
                    }
                }
            }
            if (text.isNotEmpty() && people.isEmpty() && rows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.search_none),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    )
                }
            }
            if (people.isNotEmpty()) {
                item(key = "people") {
                    SectionHeader(stringResource(R.string.tab_contacts), people.size, peopleOpen) { peopleOpen = !peopleOpen }
                }
                if (peopleOpen) {
                    itemsIndexed(people, key = { _, it -> "contact-${it.contact.id}" }) { index, result ->
                        Grouped(index, people.size) {
                            val number = result.contact.numbers.firstOrNull()
                            SwipeRow(
                                onCall = {
                                    app.rememberSearch(text)
                                    number?.let(onCall)
                                },
                                onMessage = { number?.let { sendSwipeMessage(context, it) } },
                                enabled = number != null && !selecting,
                            ) {
                                ContactResultRow(
                                    result, text,
                                    selected = result.contact.id in pickedContacts,
                                    onLongClick = { pickContact(result.contact.id) },
                                ) {
                                    if (selecting) {
                                        pickContact(result.contact.id)
                                    } else {
                                        app.rememberSearch(text)
                                        onOpen(Route.Contact(result.contact.id))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (rows.isNotEmpty()) {
                item(key = "calls") {
                    SectionHeader(stringResource(R.string.tab_recents), rows.size, callsOpen) { callsOpen = !callsOpen }
                }
                if (callsOpen) {
                    itemsIndexed(rows, key = { _, it -> "call-${it.key}" }) { index, row ->
                        Grouped(index, rows.size) {
                            val hasNumber = row.entry.number.isNotBlank()
                            SwipeRow(
                                onCall = {
                                    app.rememberSearch(text)
                                    scope.launch { callBack(context, row.entry, onCall) }
                                },
                                onMessage = { sendSwipeMessage(context, row.entry.number) },
                                enabled = hasNumber && !selecting,
                            ) {
                                CallResultRow(
                                    row, labels[CallerDirectory.keyOf(row.entry.number)], text,
                                    selected = row.key in pickedCalls,
                                    onLongClick = { pickCall(row.key) },
                                ) {
                                    if (selecting) {
                                        pickCall(row.key)
                                    } else if (hasNumber) {
                                        app.rememberSearch(text)
                                        onOpen(Route.Number(row.entry.number))
                                    }
                                }
                            }
                            if (index < rows.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(start = 46.dp, end = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        val contactIds = pickedContacts
        val callIds = rows.filter { it.key in pickedCalls }.flatMap { it.ids }
        DeleteResultsDialog(contactIds, callIds, onDismiss = { confirmDelete = false }) {
            confirmDelete = false
            pickedContacts = emptySet()
            pickedCalls = emptySet()
            scope.launch {
                if (callIds.isNotEmpty()) app.deleteCalls(callIds)
                if (contactIds.isNotEmpty()) {
                    val moved = app.trash.trash(contactIds)
                    val message = context.resources.getQuantityString(R.plurals.moved_to_trash, moved, moved)
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
private fun DeleteResultsDialog(contacts: Set<Long>, calls: List<Long>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ConfirmDialog(
        message = when {
            contacts.isEmpty() -> pluralStringResource(R.plurals.delete_calls_question, calls.size, calls.size)
            calls.isEmpty() -> pluralStringResource(R.plurals.delete_contacts_question, contacts.size, contacts.size)
            else -> stringResource(R.string.search_delete_both, contacts.size, calls.size)
        },
        icon = Icons.Outlined.Delete,
        action = stringResource(R.string.action_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** A past search: tapping it searches again, the cross drops it from the list. */
@Composable
private fun RecentSearchRow(search: String, onClick: () -> Unit, onRemove: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.History, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            search,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, stringResource(R.string.action_remove_row), tint = ink, modifier = Modifier.size(18.dp))
        }
    }
}

/** The title on one side; on the other how many were found and an arrow to fold the section. */
@Composable
private fun SectionHeader(title: String, count: Int, open: Boolean, onToggle: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 22.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 14.sp, color = ink, modifier = Modifier.weight(1f))
        Text(pluralStringResource(R.plurals.search_found, count, count), fontSize = 14.sp, color = ink)
        Spacer(Modifier.width(4.dp))
        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
    }
}

/** One row of a rounded card. Rows are separate list items, so a long result list stays lazy. */
@Composable
private fun Grouped(index: Int, size: Int, content: @Composable () -> Unit) {
    val top = if (index == 0) Corner else 0.dp
    val bottom = if (index == size - 1) Corner else 0.dp
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactResultRow(result: ContactResult, text: String, selected: Boolean, onLongClick: () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) SelectedMark(44.dp) else Avatar(result.contact.name, result.contact.photoUri, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                highlighted(result.contact.name, text),
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            result.line?.let {
                Text(
                    highlighted(it, text),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallResultRow(row: RecentRow, label: String?, text: String, selected: Boolean, onLongClick: () -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    val entry = row.entry
    val title = entry.name ?: label ?: entry.number
    // When the name isn't what matched, the number shows why this call is here.
    val line = entry.number.takeIf { title != it && SearchText.find(title, text) == null }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) SelectedMark(30.dp) else CallKindIcon(entry)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                highlighted(title, text).let { if (row.count > 1) it + AnnotatedString(" (${row.count})") else it },
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            line?.let {
                Text(highlighted(it, text), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(10.dp))
        CallTimes(entry, callDate(context, entry))
    }
}

/** The time for a call today, else the date; the year only when it isn't this one. */
private fun callDate(context: Context, entry: CallEntry): String =
    if (DateUtils.isToday(entry.date)) {
        timeOfDay(context, entry.date)
    } else {
        DateUtils.formatDateTime(context, entry.date, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    }

/** [value] with the part [text] matched in the accent colour. */
@Composable
private fun highlighted(value: String, text: String): AnnotatedString {
    val range = SearchText.find(value, text) ?: return AnnotatedString(value)
    val accent = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        append(value)
        addStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold), range.first, range.last + 1)
    }
}
