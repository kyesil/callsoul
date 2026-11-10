package com.bettercallsoul.ui

import androidx.compose.material.icons.outlined.QrCode2
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.runtime.produceState
import androidx.core.content.IntentCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.provider.Settings
import android.media.RingtoneManager
import android.app.Activity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.bettercallsoul.calllog.birthdayNear
import com.bettercallsoul.calllog.BirthdayNear
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.foundation.shape.CircleShape
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.DialerSip
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalResources
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettercallsoul.R
import com.bettercallsoul.calllog.CallEntry
import com.bettercallsoul.calllog.ContactDetail
import com.bettercallsoul.calllog.ContactRow
import com.bettercallsoul.calllog.favoritesInOrder
import com.bettercallsoul.calllog.ContactSource
import com.bettercallsoul.calllog.WhatsAppAction
import com.bettercallsoul.calllog.WhatsAppKind
import com.bettercallsoul.container
import kotlinx.coroutines.launch

@Composable
fun ContactsScreen(
    onCall: (String) -> Unit,
    onOpen: (Route) -> Unit,
    toTop: Int = 0,
) {
    val context = LocalContext.current
    val app = context.container
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val contacts by app.contactRows.collectAsStateWithLifecycle()
    // Scrolling the list puts the keyboard away.
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val list = rememberLazyListState()
    LaunchedEffect(toTop) { if (toTop > 0) list.animateScrollToItem(0) }
    LaunchedEffect(list.isScrollInProgress) {
        if (list.isScrollInProgress) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }

    // Which accounts the list is narrowed to, kept between visits.
    var sources by remember { mutableStateOf(emptyList<ContactSource>()) }
    var sourceKeys by remember { mutableStateOf(app.contactsSources) }
    var inSource by remember { mutableStateOf<Set<Long>?>(null) }
    LaunchedEffect(contacts) { sources = app.contacts.sources() }
    val chosen = remember(sources, sourceKeys) { sources.filter { it.key in sourceKeys } }
    LaunchedEffect(chosen, contacts) {
        inSource = if (chosen.isEmpty()) null else chosen.flatMapTo(HashSet()) { app.contacts.contactIdsIn(it) }
    }

    val profile by app.profileRow.collectAsStateWithLifecycle()

    val pool = remember(contacts, inSource) { inSource?.let { ids -> contacts.filter { it.id in ids } } ?: contacts }
    val shown = remember(pool, query) {
        val text = query.trim()
        if (text.isEmpty()) pool else pool.filter { it.name.contains(text, ignoreCase = true) }
    }
    val favorites = remember(shown, query) { if (query.isBlank()) shown.favoritesInOrder() else emptyList() }
    val groups = remember(shown) {
        shown.groupBy { it.name.firstOrNull()?.uppercase() ?: "#" }.toList()
    }

    // A long press starts picking contacts; picked ones go to the recycle bin together.
    var picked by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val pick: (ContactRow) -> Unit = { row -> picked = if (row.id in picked) picked - row.id else picked + row.id }
    val tap: (ContactRow) -> Unit = { row -> if (picked.isNotEmpty()) pick(row) else onOpen(Route.Contact(row.id)) }

    fun withNumber(row: ContactRow, action: (String) -> Unit) {
        scope.launch {
            app.contacts.numbers(row.id).firstOrNull()?.let(action) ?: onOpen(Route.Contact(row.id))
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (picked.isNotEmpty()) {
            SelectionBar(
                count = picked.size,
                onClear = { picked = emptySet() },
                onSelectAll = { picked = shown.map { it.id }.toSet() },
                onDelete = { confirmDelete = true },
            )
        } else ToolbarRow {
            CompactTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.search_hint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            if (query.isNotEmpty()) {
                ToolbarIcon(Icons.Filled.Close, stringResource(R.string.action_cancel)) { query = "" }
            }
            SourceButton(sources, chosen) { keys ->
                sourceKeys = keys
                app.contactsSources = keys
            }
            ToolbarIcon(Icons.Filled.Add, stringResource(R.string.action_add_contact)) { onOpen(Route.NewContact(null)) }
            MoreMenu(onTrash = { onOpen(Route.Trash) }, onSettings = { onOpen(Route.Settings) })
        }
        // The letter strip finds a letter's header by counting the items above the groups.
        val showCard = query.isBlank() && picked.isEmpty()
        val itemsBefore = (if (showCard) 1 else 0) + (if (favorites.isNotEmpty()) 2 else 0) + (if (query.isBlank() && shown.isNotEmpty()) 1 else 0) + (if (shown.isEmpty()) 1 else 0)
        Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = list) {
            if (showCard) {
                item(key = "my-card") {
                    Spacer(Modifier.height(8.dp))
                    Card {
                        MyCardRow(profile) { onOpen(profile?.let { Route.Contact(it.id) } ?: Route.NewProfile) }
                    }
                }
            }
            if (favorites.isNotEmpty()) {
                item(key = "favorites-header") { DayHeader(stringResource(R.string.favorites)) }
                item(key = "favorites") {
                    Card {
                        favorites.forEach { row ->
                            SwipeRow(onCall = { withNumber(row, onCall) }, onMessage = { withNumber(row) { sendSwipeMessage(context, it) } }, enabled = picked.isEmpty()) {
                                ContactListRow(row, selected = row.id in picked, onLongClick = { pick(row) }) { tap(row) }
                            }
                        }
                    }
                }
            }
            if (query.isBlank() && shown.isNotEmpty()) {
                item { DayHeader(pluralStringResource(R.plurals.contacts_count, shown.size, shown.size)) }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.contacts_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            groups.forEach { (letter, rows) ->
                item(key = "header-$letter") { DayHeader(letter) }
                item(key = "group-$letter") {
                    Card {
                        rows.forEach { row ->
                            // The default number, or the first one when none is marked.
                            SwipeRow(onCall = { withNumber(row, onCall) }, onMessage = { withNumber(row) { sendSwipeMessage(context, it) } }, enabled = picked.isEmpty()) {
                                ContactListRow(row, selected = row.id in picked, onLongClick = { pick(row) }) { tap(row) }
                            }
                        }
                    }
                }
            }
        }
        if (query.isBlank() && picked.isEmpty() && groups.size > 1) {
            LetterStrip(
                letters = groups.map { it.first },
                modifier = Modifier.align(Alignment.CenterEnd),
            ) { index -> scope.launch { list.scrollToItem(itemsBefore + 2 * index) } }
        }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            message = pluralStringResource(R.plurals.delete_contacts_question, picked.size, picked.size),
            icon = Icons.Outlined.Delete,
            action = stringResource(R.string.action_delete),
            onConfirm = {
                val ids = picked
                confirmDelete = false
                picked = emptySet()
                scope.launch {
                    val moved = app.trash.trash(ids)
                    val text = if (moved == ids.size) {
                        resources.getQuantityString(R.plurals.moved_to_trash, moved, moved)
                    } else {
                        resources.getString(R.string.trash_partial, moved, ids.size - moved)
                    }
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Narrows the list to some accounts: Google accounts, the phone, the SIM, WhatsApp. Stays open while choosing. */
@Composable
private fun SourceButton(sources: List<ContactSource>, chosen: List<ContactSource>, onChange: (Set<String>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val keys = chosen.mapTo(HashSet()) { it.key }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.stretchy()) {
            Icon(
                Icons.Filled.FilterList,
                stringResource(R.string.contacts_source),
                tint = if (chosen.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.contacts_all)) },
                trailingIcon = if (chosen.isEmpty()) checkMark else null,
                onClick = { onChange(emptySet()) },
            )
            if (sources.isNotEmpty()) HorizontalDivider()
            sources.forEach { source ->
                DropdownMenuItem(
                    text = { Text(source.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = if (source.key in keys) checkMark else null,
                    onClick = { onChange(if (source.key in keys) keys - source.key else keys + source.key) },
                )
            }
        }
    }
}

private val checkMark: @Composable () -> Unit = {
    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
}

@Composable
private fun MoreMenu(onTrash: () -> Unit, onSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolbarIcon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.trash_title)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = {
                    open = false
                    onTrash()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_settings)) },
                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                onClick = {
                    open = false
                    onSettings()
                },
            )
        }
    }
}

/** A contact label as a chip with a label's icon. */
@Composable
private fun LabelChip(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The owner's own card at the top of the list, or a way to make one. */
@Composable
private fun MyCardRow(profile: ContactRow?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (profile != null) {
            Avatar(profile.name.ifBlank { stringResource(R.string.my_card) }, profile.photoUri, 48.dp)
        } else {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile?.name?.ifBlank { null } ?: stringResource(if (profile == null) R.string.my_card_create else R.string.my_card),
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(if (profile == null) R.string.my_card_create_body else R.string.my_card),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactListRow(row: ContactRow, selected: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .stretchy(strength = 0.35f, follow = false)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 54.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) SelectedMark(38.dp) else Avatar(row.name, row.photoUri, 38.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            row.name,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (row.starred) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun ContactDetailScreen(contactId: Long, onCall: (String) -> Unit, onOpen: (Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.container
    val scope = rememberCoroutineScope()
    var contact by remember { mutableStateOf<ContactDetail?>(null) }
    var history by remember { mutableStateOf(emptyList<CallEntry>()) }
    val shareTitle = stringResource(R.string.action_share_contact)
    val shareFailed = stringResource(R.string.share_failed)
    val cantDelete = stringResource(R.string.contact_not_editable)
    var confirmDelete by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var talked by remember { mutableLongStateOf(0L) }
    val ringtoneTitle = stringResource(R.string.ringtone_title)
    // The system's own picker; its "Default" takes the contact's ringtone away again.
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val shown = contact ?: return@rememberLauncherForActivityResult
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        val picked = IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        val chosen = picked?.takeUnless(RingtoneManager::isDefault)?.toString()
        scope.launch {
            app.contacts.setRingtone(shown.id, chosen)
            contact = shown.copy(ringtone = chosen)
        }
    }
    LaunchedEffect(contactId) {
        val loaded = app.contacts.detail(contactId)
        contact = loaded
        if (loaded == null) {
            onBack()
            return@LaunchedEffect
        }
        talked = app.history.talkSeconds(loaded.phones.map { it.number })
        // The calls with any of the contact's numbers.
        history = app.callHistory(loaded.phones.map { it.number }, loaded.name)
    }
    // Until the contact is read, the bar with the way back, so the page isn't blank.
    val detail = contact ?: run {
        Column(Modifier.fillMaxSize().statusBarsPadding()) { DetailToolbar(onBack) }
        return
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        val own = ContactsContract.isProfileId(detail.id)
        DetailToolbar(onBack) {
            if (!own) ToolbarIcon(
                if (detail.starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                stringResource(if (detail.starred) R.string.action_unfavorite else R.string.action_favorite),
            ) {
                scope.launch {
                    app.contacts.setStarred(detail.id, !detail.starred)
                    contact = detail.copy(starred = !detail.starred)
                }
            }
            ToolbarIcon(Icons.Filled.Share, shareTitle) {
                scope.launch {
                    if (!shareContact(context, detail.lookupKey, detail.name, shareTitle)) {
                        Toast.makeText(context, shareFailed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            ToolbarIcon(Icons.Outlined.QrCode2, stringResource(R.string.qr_title)) { showQr = true }
            if (!own) ToolbarIcon(Icons.Outlined.Delete, stringResource(R.string.contact_delete)) { confirmDelete = true }
            ToolbarIcon(Icons.Filled.Edit, stringResource(R.string.action_edit)) {
                scope.launch {
                    if (app.editor.load(detail.id) != null) {
                        onOpen(Route.EditContact(detail.id))
                    } else {
                        // Only SIM or app-owned copies: the system editor knows how to handle those.
                        val uri = ContactsContract.Contacts.getLookupUri(detail.id, detail.lookupKey)
                        context.startActivity(Intent(Intent.ACTION_EDIT).setDataAndType(uri, ContactsContract.Contacts.CONTENT_ITEM_TYPE))
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(detail.name, detail.photoUri, 132.dp)
            Spacer(Modifier.height(12.dp))
            Text(detail.name, fontSize = 27.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 16.dp))
            detail.nickname?.let {
                Text("“$it”", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val work = listOfNotNull(detail.company, detail.jobTitle, detail.department).joinToString(" · ")
            if (work.isNotEmpty()) {
                Text(work, fontSize = 15.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
            birthdayNear(detail.birthday)?.let { near ->
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BirthdayCake(near, Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        stringResource(if (near == BirthdayNear.Today) R.string.birthday_today else R.string.birthday_tomorrow),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (talked > 0) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.profile_talk_total, talkTime(talked)), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val main = detail.phones.firstOrNull()?.number
            if (main != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RoundAction(Icons.Filled.Call, R.string.action_call, CallGreen) { onCall(main) }
                    RoundAction(Icons.AutoMirrored.Filled.Message, R.string.action_message, MessageBlue) { sendSms(context, main) }
                    detail.whatsApp.firstOrNull { it.kind == WhatsAppKind.Video }?.let { video ->
                        RoundAction(Icons.Filled.Videocam, R.string.whatsapp_video, WhatsAppGreen) { openWhatsApp(context, video) }
                    }
                }
            }
        }
        if (detail.phones.isNotEmpty()) {
            val haptics = LocalHapticFeedback.current
            val copiedNote = stringResource(R.string.number_copied)
            Card {
                detail.phones.forEach { phone ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // A long press copies the number.
                        Column(
                            Modifier
                                .weight(1f)
                                .combinedClickable(onClick = {}, onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    copyText(context, phone.number, copiedNote)
                                })
                                .padding(vertical = 6.dp),
                        ) {
                            Text(phone.number, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                            phone.label?.let {
                                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { sendSms(context, phone.number) }) {
                            Icon(Icons.AutoMirrored.Filled.Message, stringResource(R.string.action_message), tint = MessageBlue)
                        }
                        IconButton(onClick = { onCall(phone.number) }) {
                            Icon(Icons.Filled.Call, stringResource(R.string.action_call), tint = CallGreen)
                        }
                    }
                }
            }
        }
        if (detail.whatsApp.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Card {
                detail.whatsApp.groupBy { it.account }.forEach { (account, actions) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.whatsapp), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                            if (account.isNotEmpty()) {
                                Text(account, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        // A message first, then the calls: the chat with this WhatsApp number.
                        if (account.isNotEmpty()) {
                            IconButton(onClick = {
                                if (!openWhatsAppChat(context, account)) Toast.makeText(context, R.string.whatsapp_missing, Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Message, stringResource(R.string.widget_action_whatsapp_message), tint = WhatsAppGreen)
                            }
                        }
                        actions.distinctBy { it.kind }.sortedBy { it.kind }.forEach { action ->
                            IconButton(onClick = { openWhatsApp(context, action) }) {
                                Icon(
                                    if (action.kind == WhatsAppKind.Video) Icons.Filled.Videocam else Icons.Filled.Call,
                                    stringResource(if (action.kind == WhatsAppKind.Video) R.string.whatsapp_video else R.string.whatsapp_voice),
                                    tint = WhatsAppGreen,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (detail.labels.isNotEmpty()) {
            // Each label a chip; a tap searches for everyone with it.
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                detail.labels.forEach { label ->
                    LabelChip(label) { onOpen(Route.SearchLabel(label)) }
                }
            }
        }
        val info = buildList<@Composable () -> Unit> {
            detail.emails.forEach { add { InfoRow(Icons.Outlined.Email, it.value, it.label) { open(context, "mailto:${it.value}") } } }
            detail.addresses.forEach { add { InfoRow(Icons.Outlined.Place, it.value, it.label) { open(context, "geo:0,0?q=${Uri.encode(it.value)}") } } }
            detail.events.forEach { add { InfoRow(Icons.Outlined.Event, formatContactDate(context, it.value), it.label) } }
            detail.relations.forEach { add { InfoRow(Icons.Outlined.People, it.value, it.label) } }
            detail.websites.forEach { site ->
                add { InfoRow(Icons.Outlined.Public, site.value, site.label) { open(context, if ("://" in site.value) site.value else "https://${site.value}") } }
            }
            detail.sips.forEach { add { InfoRow(Icons.Outlined.DialerSip, it.value, it.label) } }
            detail.customFields.forEach { add { InfoRow(Icons.Outlined.Tune, it.value, it.label) } }
            detail.note?.let { add { InfoRow(Icons.Outlined.Notes, it, stringResource(R.string.field_note)) } }
            // Telecom rings with the contact's own ringtone; nobody calls one's own card.
            if (!own) add {
                InfoRow(Icons.Outlined.MusicNote, ringtoneName(detail.ringtone), ringtoneTitle) {
                    ringtonePicker.launch(ringtonePickerIntent(detail.ringtone, ringtoneTitle))
                }
            }
        }
        if (info.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Card(Modifier.padding(vertical = 0.dp)) {
                Spacer(Modifier.height(4.dp))
                info.forEach { it() }
                Spacer(Modifier.height(4.dp))
            }
        }
        if (history.isNotEmpty()) {
            DayHeader(stringResource(R.string.section_history))
            Card {
                HistoryRows(history, showNumber = detail.phones.size > 1)
            }
        }
        if (detail.sources.isNotEmpty()) {
            Text(
                stringResource(R.string.saved_in) + ": " + detail.sources.joinToString(", "),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showQr) ContactQrDialog(detail) { showQr = false }
    if (confirmDelete) {
        ConfirmDialog(
            message = pluralStringResource(R.plurals.delete_contacts_question, 1, 1),
            icon = Icons.Outlined.Delete,
            action = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    if (app.trash.trash(listOf(detail.id)) > 0) {
                        onBack()
                    } else {
                        // Only SIM or app-owned copies, which can't be removed from here.
                        Toast.makeText(context, cantDelete, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

internal fun openWhatsApp(context: Context, action: WhatsAppAction) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(action.dataUri.toUri(), action.mimeType)
        .setPackage(action.packageName)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.whatsapp_missing, Toast.LENGTH_SHORT).show()
    }
}

/** Hands a mailto:, geo: or web address to whichever app the user has for it. */
internal fun open(context: Context, uri: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Nothing on the phone opens it; the value is still there to read and copy.
    }
}

private fun ringtonePickerIntent(current: String?, title: String): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        // Telecom reads an empty ringtone as the default, so silence could not be kept.
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_RINGTONE_URI)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current?.toUri() ?: Settings.System.DEFAULT_RINGTONE_URI)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)

/** The ringtone's name as the system gives it, or the phone's default when the contact has none. */
@Composable
private fun ringtoneName(ringtone: String?): String {
    val context = LocalContext.current
    val fallback = stringResource(R.string.ringtone_default)
    // The state outlives a change of ringtone, so each change sets it, the default included.
    val name by produceState(if (ringtone == null) fallback else "", ringtone) {
        value = if (ringtone == null) fallback else withContext(Dispatchers.IO) {
            runCatching { RingtoneManager.getRingtone(context, ringtone.toUri())?.getTitle(context) }.getOrNull()
        } ?: fallback
    }
    return name
}

@Composable
private fun InfoRow(icon: ImageVector, value: String, label: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(value, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            if (label.isNotBlank()) Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DetailToolbar(onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack)
        Spacer(Modifier.weight(1f))
        actions()
    }
}
