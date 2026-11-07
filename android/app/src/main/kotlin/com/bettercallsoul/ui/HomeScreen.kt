package com.bettercallsoul.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bettercallsoul.telecom.CallerDirectory
import com.bettercallsoul.calllog.CallEntry
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettercallsoul.R
import com.bettercallsoul.call.TagDialog
import com.bettercallsoul.calllog.ContactRow
import com.bettercallsoul.container
import kotlinx.coroutines.launch

/** A little over four tiles fit, so the cut-off one shows the row goes on. */
private val TileWidth = 84.dp
private const val MOST_TALKED = 12

/** Someone in the most-talked row: a contact, or a number outside the contacts. */
private class TopCaller(val key: String, val number: String, val name: String, val photoUri: String?, val seconds: Long)

/** By total talk time in the calls given; a contact's numbers count together. */
private fun mostTalked(calls: List<CallEntry>, labels: Map<String, String>): List<TopCaller> =
    calls.filter { it.durationSeconds > 0 && it.number.isNotBlank() }
        .groupBy { it.name ?: CallerDirectory.keyOf(it.number) }
        .map { (key, entries) ->
            val latest = entries.first()
            val name = latest.name ?: labels[CallerDirectory.keyOf(latest.number)] ?: latest.number
            TopCaller(key, latest.number, name, latest.photoUri, entries.sumOf { it.durationSeconds })
        }
        .sortedByDescending { it.seconds }
        .take(MOST_TALKED)

/** The first tab: favourites and the most talked to in sliding rows, the latest calls below. */
@Composable
fun HomeScreen(onCall: (String) -> Unit, onOpen: (Route) -> Unit, onScan: () -> Unit) {
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val contacts by remember { app.contacts.observe() }.collectAsStateWithLifecycle(emptyList())
    val favorites = remember(contacts) { contacts.filter { it.starred } }
    val recents = rememberRecents(limit = 30)
    val calls by remember { app.observeCalls(limit = 1000) }.collectAsStateWithLifecycle(emptyList())
    val talked = remember(calls, recents.labels) { mostTalked(calls, recents.labels) }
    var tagging by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }

    // On the tiles a tap opens the profile and a double tap calls the default number.
    val callFavorite: (ContactRow) -> Unit = { row ->
        scope.launch {
            app.contacts.numbers(row.id).firstOrNull()?.let(onCall) ?: onOpen(Route.Contact(row.id))
        }
    }

    BackHandler(enabled = picked.isNotEmpty()) { picked = emptySet() }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            if (picked.isNotEmpty()) {
                SelectionBar(
                    count = picked.size,
                    onClear = { picked = emptySet() },
                    onSelectAll = { picked = recents.rows.map { it.key }.toSet() },
                    onDelete = { confirmDelete = true },
                )
            } else ToolbarRow {
                MainToolbarActions(onScan, onOpen)
            }
        }
        item { LargeTitle(stringResource(R.string.title_phone), null) }
        item { DayHeader(stringResource(R.string.favorites)) }
        if (favorites.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.favorites_empty),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                )
            }
        } else {
            item(key = "favorites") {
                Card {
                    LazyRow(contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
                        items(favorites, key = { it.id }) { contact ->
                            PersonTile(
                                contact.name,
                                contact.photoUri,
                                onCall = { callFavorite(contact) },
                                onOpen = { onOpen(Route.Contact(contact.id)) },
                            )
                        }
                    }
                }
            }
        }
        if (talked.isNotEmpty()) {
            item { DayHeader(stringResource(R.string.section_most_talked)) }
            item(key = "most-talked") {
                Card {
                    LazyRow(contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
                        items(talked, key = { it.key }) { caller ->
                            PersonTile(
                                caller.name,
                                caller.photoUri,
                                onCall = { onCall(caller.number) },
                                onOpen = {
                                    scope.launch {
                                        val contact = app.contacts.match(caller.number)
                                        onOpen(contact?.let { Route.Contact(it.id) } ?: Route.Number(caller.number))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        recentCalls(recents, onCall, onOpen, onTag = { tagging = it }, picked = picked, onPick = { row ->
            picked = if (row.key in picked) picked - row.key else picked + row.key
        })
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonTile(name: String, photoUri: String?, onCall: () -> Unit, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .width(TileWidth)
            .stretchy()
            .combinedClickable(onClick = onOpen, onDoubleClick = onCall)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(name, photoUri, 56.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
