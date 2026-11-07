package com.bettercallsoul.ui

import com.bettercallsoul.calllog.BirthdayNear
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Cake
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import com.bettercallsoul.calllog.Birthday
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import com.bettercallsoul.calllog.favoritesInOrder
import com.bettercallsoul.container
import kotlinx.coroutines.launch

/** A little over four tiles fit, so the cut-off one shows the row goes on. */
private val TileWidth = 84.dp
private const val MOST_TALKED = 12

/** Someone in the most-talked row: a contact, or a number outside the contacts. */
private class TopCaller(
    val key: String,
    val number: String,
    val name: String,
    val photoUri: String?,
    val seconds: Long,
    val contactId: Long? = null,
    val birthday: BirthdayNear? = null,
)

/** Whoever has a birthday today comes first, marked, whether or not they are talked to much. */
private fun withBirthdays(talked: List<TopCaller>, birthdays: List<Birthday>): List<TopCaller> {
    if (birthdays.isEmpty()) return talked
    val first = birthdays.map { person ->
        val known = talked.firstOrNull { it.key == person.name }
        val near = if (person.today) BirthdayNear.Today else BirthdayNear.Tomorrow
        TopCaller(person.name, person.number ?: known?.number.orEmpty(), person.name, person.photoUri, known?.seconds ?: 0, person.contactId, near)
    }
    val keys = first.mapTo(HashSet()) { it.key }
    return first + talked.filterNot { it.key in keys }
}

/** By total talk time in the calls given; a contact's numbers count together. */
private fun mostTalked(calls: List<CallEntry>, labels: Map<String, String>): List<TopCaller> =
    com.bettercallsoul.mostTalked(calls, labels, MOST_TALKED).map { TopCaller(it.key, it.number, it.name, it.photoUri, it.seconds) }

/** The first tab: favourites and the most talked to in sliding rows, the latest calls below. */
@Composable
fun HomeScreen(onCall: (String) -> Unit, onOpen: (Route) -> Unit, onScan: () -> Unit) {
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val contacts by remember { app.contacts.observe() }.collectAsStateWithLifecycle(emptyList())
    val favorites = remember(contacts) { contacts.favoritesInOrder() }
    val recents = rememberRecents(limit = 30)
    val calls by remember { app.observeCalls(limit = 1000) }.collectAsStateWithLifecycle(emptyList())
    val birthdays by remember { app.contacts.observeBirthdays() }.collectAsStateWithLifecycle(emptyList())
    val talked = remember(calls, recents.labels, birthdays) { withBirthdays(mostTalked(calls, recents.labels), birthdays) }
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
                    FavoritesRow(
                        favorites,
                        onCall = callFavorite,
                        onOpen = { onOpen(Route.Contact(it.id)) },
                        onReorder = { ids -> scope.launch { runCatching { app.contacts.setFavoriteOrder(ids) } } },
                    )
                }
            }
        }
        if (talked.isNotEmpty()) {
            item { DayHeader(stringResource(R.string.section_most_talked)) }
            item(key = "most-talked") {
                Card {
                    LazyRow(contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
                        items(talked, key = { it.key }) { caller ->
                            val open: () -> Unit = {
                                scope.launch {
                                    val id = caller.contactId ?: app.contacts.match(caller.number)?.id
                                    onOpen(id?.let { Route.Contact(it) } ?: Route.Number(caller.number))
                                }
                            }
                            PersonTile(
                                caller.name,
                                caller.photoUri,
                                onCall = { if (caller.number.isNotBlank()) onCall(caller.number) else open() },
                                onOpen = open,
                                birthday = caller.birthday,
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
private fun PersonTile(
    name: String,
    photoUri: String?,
    onCall: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** Touch handling that sees the touch before the taps do, so a drag it takes isn't also a tap. */
    gestures: Modifier = Modifier,
    /** A cake on the photo's corner: red on the birthday, yellow the day before. */
    birthday: BirthdayNear? = null,
) {
    Column(
        modifier = modifier
            .width(TileWidth)
            .stretchy()
            .combinedClickable(onClick = onOpen, onDoubleClick = onCall)
            .then(gestures)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Avatar(name, photoUri, 56.dp)
            if (birthday != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    BirthdayCake(birthday, Modifier.size(16.dp))
                }
            }
        }
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

/**
 * The favourites, which a long press picks up and a drag moves along the row. The row scrolls on
 * when the tile is held at its edge; the new order is kept when the tile is let go.
 */
@Composable
private fun FavoritesRow(
    favorites: List<ContactRow>,
    onCall: (ContactRow) -> Unit,
    onOpen: (ContactRow) -> Unit,
    onReorder: (List<Long>) -> Unit,
) {
    val state = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    var order by remember { mutableStateOf(favorites) }
    var dragged by remember { mutableStateOf<Long?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    // How far the finger has gone since the pick-up, which tells which way it is heading.
    var travel by remember { mutableFloatStateOf(0f) }
    // A list read again while a tile is held waits; after a drop the dropped order stays until the address book has it.
    LaunchedEffect(favorites) { if (dragged == null) order = favorites }
    val saved by rememberUpdatedState(favorites)
    val save by rememberUpdatedState(onReorder)

    // Past half a tile the held one changes places with its neighbour, and the shift is taken off.
    fun follow() {
        val id = dragged ?: return
        val pitch = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.toFloat()?.takeIf { it > 0 } ?: return
        while (true) {
            val from = order.indexOfFirst { it.id == id }
            val to = when {
                offset > pitch / 2 && from < order.lastIndex -> from + 1
                offset < -pitch / 2 && from > 0 -> from - 1
                else -> return
            }
            // A moved first item would otherwise pull the row along with it.
            if (from == state.firstVisibleItemIndex || to == state.firstVisibleItemIndex) {
                state.requestScrollToItem(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
            }
            order = order.toMutableList().apply { add(to, removeAt(from)) }
            offset -= (to - from) * pitch
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    LaunchedEffect(dragged) {
        val id = dragged ?: return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val layout = state.layoutInfo
            val held = layout.visibleItemsInfo.firstOrNull { it.key == id } ?: continue
            val start = held.offset + offset
            val edge = held.size / 3f
            // Only towards where the finger went, so a tile picked up at the edge doesn't set it off.
            val push = when {
                travel < 0 && start < layout.viewportStartOffset + edge -> start - layout.viewportStartOffset - edge
                travel > 0 && start + held.size > layout.viewportEndOffset - edge -> start + held.size - layout.viewportEndOffset + edge
                else -> 0f
            }.coerceIn(-MAX_EDGE_SCROLL, MAX_EDGE_SCROLL)
            if (push == 0f) continue
            val moved = state.scrollBy(push)
            offset += moved
            follow()
        }
    }

    LazyRow(state = state, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
        items(order, key = { it.id }) { contact ->
            val held = contact.id == dragged
            val lift by animateFloatAsState(if (held) 1.08f else 1f, label = "lift")
            PersonTile(
                contact.name,
                contact.photoUri,
                onCall = { onCall(contact) },
                onOpen = { onOpen(contact) },
                modifier = Modifier
                    .then(if (held) Modifier.zIndex(1f) else Modifier.animateItem())
                    .graphicsLayer {
                        translationX = if (held) offset else 0f
                        scaleX = lift
                        scaleY = lift
                    },
                gestures = Modifier
                    .pointerInput(contact.id) {
                        fun drop() {
                            dragged = null
                            offset = 0f
                            val ids = order.map { it.id }
                            if (ids != saved.map { it.id }) save(ids)
                        }
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragged = contact.id
                                offset = 0f
                                travel = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                offset += amount.x
                                travel += amount.x
                                follow()
                            },
                            onDragEnd = { drop() },
                            onDragCancel = { drop() },
                        )
                    },
            )
        }
    }
}

/** How far the row scrolls in a frame while a tile is held at its edge, in pixels. */
private const val MAX_EDGE_SCROLL = 18f
