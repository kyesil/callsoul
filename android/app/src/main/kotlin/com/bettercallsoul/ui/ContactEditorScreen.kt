package com.bettercallsoul.ui

import com.bettercallsoul.myCardAccount
import androidx.compose.material3.OutlinedTextField
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DialerSip
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.io.File
import androidx.core.net.toUri
import androidx.core.content.FileProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PhotoCamera
import android.content.ActivityNotFoundException
import com.bettercallsoul.AppContainer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.FilledIconButton
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import android.widget.Toast
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bettercallsoul.R
import com.bettercallsoul.calllog.ContactAccount
import com.bettercallsoul.calllog.ContactDraft
import com.bettercallsoul.calllog.ContactField
import com.bettercallsoul.calllog.ContactGroup
import com.bettercallsoul.calllog.CustomField
import com.bettercallsoul.calllog.EditableValue
import com.bettercallsoul.calllog.PhotoEdit
import com.bettercallsoul.calllog.StoredContact
import com.bettercallsoul.container
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/** The parts of the editor below name, phone and e-mail, shown once they hold something or are asked for. */
private enum class Section(val chip: Int) {
    Address(R.string.chip_address),
    Event(R.string.chip_event),
    Relation(R.string.chip_relation),
    Website(R.string.chip_website),
    Sip(R.string.chip_sip),
    Labels(R.string.chip_labels),
    Custom(R.string.chip_custom),
    Note(R.string.chip_note),
}

private fun ContactDraft.has(section: Section): Boolean = when (section) {
    Section.Address -> addresses.isNotEmpty()
    Section.Event -> events.isNotEmpty()
    Section.Relation -> relations.isNotEmpty()
    Section.Website -> websites.isNotEmpty()
    Section.Sip -> sips.isNotEmpty()
    Section.Labels -> groups.isNotEmpty()
    Section.Custom -> customFields.isNotEmpty()
    Section.Note -> note.isNotBlank()
}

/**
 * Edits [contactId], or starts a new contact: blank, holding [number], or filled from the business
 * card in a scanned QR code ([scanned]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactEditorScreen(
    contactId: Long?,
    number: String?,
    scanned: String? = null,
    profile: Boolean = false,
    onDone: () -> Unit,
    onMoved: (newContactId: Long) -> Unit = { onDone() },
) {
    // The owner's own card lives in Android's profile, on the phone: no account, no labels.
    val isProfile = profile || (contactId != null && ContactsContract.isProfileId(contactId))
    val context = LocalContext.current
    val app = context.container
    val editor = app.editor
    val scope = rememberCoroutineScope()

    val accounts = remember { editor.accounts() }
    // The account used last time, else a Google account when there is one: the storage that syncs.
    val defaultAccount = remember { defaultAccount(app, accounts) }
    var account by remember { mutableStateOf(defaultAccount) }
    var stored by remember { mutableStateOf<StoredContact?>(null) }
    // Another account picked for a saved contact: saving moves it there.
    var moveTo by remember { mutableStateOf<ContactAccount?>(null) }
    var choosingExisting by remember { mutableStateOf(false) }
    val blank = remember {
        scanned?.let(::draftFromCode)
            ?: ContactDraft(phones = listOfNotNull(number?.let { EditableValue(it, ContactField.Phone.defaultType) }))
    }
    var draft by remember { mutableStateOf(if (contactId == null) blank else null) }
    var photo by remember { mutableStateOf<PhotoEdit>(PhotoEdit.Keep) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf(emptySet<Section>()) }
    var groups by remember { mutableStateOf(emptyList<ContactGroup>()) }
    // Typing the name of a new label.
    var naming by remember { mutableStateOf(false) }

    LaunchedEffect(contactId) {
        if (contactId == null) return@LaunchedEffect
        val loaded = editor.load(contactId)
        if (loaded == null) {
            onDone()
        } else {
            stored = loaded
            draft = loaded.draft
        }
    }
    val target = moveTo ?: stored?.account ?: account
    LaunchedEffect(target) { groups = editor.groups(target) }

    val current = draft ?: return
    if (naming) {
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(R.string.label_new)) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().focusOnOpen(),
                )
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    naming = false
                    val name = title.trim()
                    val account = target
                    scope.launch {
                        // One with that name already: it is picked rather than made twice.
                        val id = groups.firstOrNull { it.title.equals(name, ignoreCase = true) }?.id
                            ?: runCatching { editor.createGroup(account, name) }.getOrNull()
                        groups = editor.groups(account)
                        val latest = draft
                        if (id != null && latest != null) draft = latest.copy(groups = latest.groups + id)
                    }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    // A scanned card counts as unsaved work from the start; a new contact can be saved as it came.
    val changed = (scanned != null && stored == null) || current != (stored?.draft ?: blank) || photo != PhotoEdit.Keep || moveTo != null
    val canSave = !saving && (changed || stored == null) && (
        current.displayName.isNotBlank() || current.company.isNotBlank() ||
            current.phones.any { it.value.isNotBlank() } || current.emails.any { it.value.isNotBlank() }
        )

    /** Runs [action] off the screen; what it returns is called once it is done, to leave the editor. */
    fun run(action: suspend () -> (() -> Unit)) {
        saving = true
        failed = false
        scope.launch {
            try {
                val next = action()
                next()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                saving = false
                failed = true
            }
        }
    }

    fun save() {
        val existing = stored
        val destination = moveTo
        run {
            when {
                existing == null && isProfile && app.contacts.profileHidden -> {
                    // The phone keeps its profile to itself: My card is a contact in phone storage.
                    editor.create(current, myCardAccount(app), photo)?.let { app.myCardKey = app.contacts.lookupKeyOf(it) }
                    onDone
                }
                existing == null && isProfile -> {
                    editor.create(current, account, photo, profile = true)
                    onDone
                }
                existing == null -> {
                    app.newContactAccount = accountKey(account)
                    editor.create(current, account, photo)
                    onDone
                }
                destination != null -> {
                    val id = editor.move(existing, current, photo, destination)
                    if (id != null && existing.contactId == contactId) ({ onMoved(id) }) else onDone
                }
                else -> {
                    editor.update(existing, current, photo)
                    onDone
                }
            }
        }
    }

    // The phone's own storage goes by several names (Samsung's, plain local); they count as one.
    fun sameStorage(a: ContactAccount, b: ContactAccount) = a == b || (!a.isGoogle && !b.isGoogle)

    BackHandler(enabled = changed) { confirmDiscard = true }

    fun shown(section: Section) = section in opened || current.has(section)
    // A Google account can take new labels, so its label section shows even before it has any.
    val labelsOffered = (groups.isNotEmpty() || target.isGoogle) && !isProfile
    val sections = Section.entries.filter { it != Section.Labels || labelsOffered }
        .filter { it != Section.Custom || (target.isGoogle && !isProfile) }

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (changed) confirmDiscard = true else onDone() }) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_cancel), tint = MaterialTheme.colorScheme.onBackground)
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        stringResource(
                            when {
                                isProfile -> R.string.my_card
                                stored == null -> R.string.contact_new
                                else -> R.string.contact_edit
                            },
                        ),
                        fontSize = 19.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (!isProfile) AccountPicker(
                        accounts = accounts,
                        selected = if (target.isGoogle) target else accounts.firstOrNull { !it.isGoogle } ?: target,
                        editable = true,
                        defaultAccount = defaultAccount,
                        onPick = { picked ->
                            val saved = stored
                            if (saved == null) {
                                account = picked
                                draft = current.copy(groups = emptySet(), customFields = if (picked.isGoogle) current.customFields else emptyList())
                            } else {
                                moveTo = picked.takeUnless { sameStorage(it, saved.account) }
                                // Labels belong to their account: kept only when staying.
                                draft = current.copy(groups = if (moveTo == null) saved.draft.groups else emptySet())
                            }
                        },
                    )
                }
                FilledIconButton(onClick = ::save, enabled = canSave, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Check, stringResource(R.string.action_save))
                }
            }
            moveTo?.let { destination ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.contact_move_hint, accountLabel(destination)),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (failed) {
                Text(
                    stringResource(R.string.contact_save_failed),
                    color = EndRed,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (stored == null && contactId == null && !isProfile) {
                    AssistChip(
                        onClick = { choosingExisting = true },
                        label = { Text(stringResource(R.string.contact_add_to_existing)) },
                        leadingIcon = { Icon(Icons.Outlined.PersonSearch, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                PhotoPicker(
                    name = current.displayName,
                    savedPhoto = stored?.photoUri,
                    edit = photo,
                    onEdit = { photo = it },
                )
                EditorSection(Icons.Outlined.Person) {
                    NameFields(current) { draft = it }
                }
                EditorSection(Icons.Outlined.Business) {
                    TextEntry(R.string.field_company, current.company) { draft = current.copy(company = it) }
                    TextEntry(R.string.field_job_title, current.jobTitle) { draft = current.copy(jobTitle = it) }
                    TextEntry(R.string.field_department, current.department) { draft = current.copy(department = it) }
                }
                EditorSection(Icons.Outlined.Phone) {
                    ValueRows(ContactField.Phone, current.phones, R.string.field_phone, R.string.add_phone, KeyboardType.Phone) {
                        draft = current.copy(phones = it)
                    }
                }
                EditorSection(Icons.Outlined.Email) {
                    ValueRows(ContactField.Email, current.emails, R.string.field_email, R.string.add_email, KeyboardType.Email) {
                        draft = current.copy(emails = it)
                    }
                }
                if (shown(Section.Address)) {
                    EditorSection(Icons.Outlined.Place) {
                        ValueRows(ContactField.Address, current.addresses, R.string.field_address, R.string.add_address, KeyboardType.Text, singleLine = false) {
                            draft = current.copy(addresses = it)
                        }
                    }
                }
                if (shown(Section.Event)) {
                    EditorSection(Icons.Outlined.Event) {
                        DateRows(current.events) { draft = current.copy(events = it) }
                    }
                }
                if (shown(Section.Relation)) {
                    EditorSection(Icons.Outlined.People) {
                        ValueRows(
                            ContactField.Relation, current.relations, R.string.field_relation, R.string.add_relation,
                            KeyboardType.Text, capitalization = KeyboardCapitalization.Words,
                        ) { draft = current.copy(relations = it) }
                    }
                }
                if (shown(Section.Website)) {
                    EditorSection(Icons.Outlined.Public) {
                        ValueRows(ContactField.Website, current.websites, R.string.field_website, R.string.add_website, KeyboardType.Uri) {
                            draft = current.copy(websites = it)
                        }
                    }
                }
                if (shown(Section.Sip)) {
                    EditorSection(Icons.Outlined.DialerSip) {
                        ValueRows(ContactField.Sip, current.sips, R.string.field_sip, R.string.add_sip, KeyboardType.Email) {
                            draft = current.copy(sips = it)
                        }
                    }
                }
                if (shown(Section.Labels) && labelsOffered) {
                    EditorSection(Icons.AutoMirrored.Outlined.Label) {
                        Text(stringResource(R.string.section_labels), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            groups.forEach { group ->
                                val on = group.id in current.groups
                                FilterChip(
                                    selected = on,
                                    onClick = { draft = current.copy(groups = if (on) current.groups - group.id else current.groups + group.id) },
                                    label = { Text(group.title) },
                                    leadingIcon = if (on) {
                                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else {
                                        null
                                    },
                                )
                            }
                            AssistChip(
                                onClick = { naming = true },
                                label = { Text(stringResource(R.string.label_new)) },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
                if (shown(Section.Custom) && target.isGoogle) {
                    EditorSection(Icons.Outlined.Tune) {
                        CustomRows(current.customFields) { draft = current.copy(customFields = it) }
                    }
                }
                if (shown(Section.Note)) {
                    EditorSection(Icons.Outlined.Notes) {
                        TextEntry(R.string.field_note, current.note, singleLine = false) { draft = current.copy(note = it) }
                    }
                }

                val hidden = sections.filterNot(::shown)
                if (hidden.isNotEmpty()) {
                    Text(
                        stringResource(R.string.section_add_field),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        hidden.forEach { section ->
                            AssistChip(
                                onClick = {
                                    opened = opened + section
                                    draft = current.withFirstRow(section)
                                },
                                label = { Text(stringResource(section.chip)) },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                            )
                        }
                    }
                }
                if (stored != null && contactId != null) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        enabled = !saving,
                        border = BorderStroke(1.dp, EndRed.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = EndRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.contact_delete), color = EndRed)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (confirmDiscard) {
        Confirm(
            message = R.string.contact_discard_question,
            action = R.string.contact_discard,
            onConfirm = onDone,
            onDismiss = { confirmDiscard = false },
        )
    }
    if (choosingExisting) {
        ExistingContactPicker(onDismiss = { choosingExisting = false }) { id ->
            choosingExisting = false
            scope.launch {
                val loaded = editor.load(id)
                if (loaded == null) {
                    Toast.makeText(context, R.string.contact_not_editable, Toast.LENGTH_SHORT).show()
                } else {
                    // The contact keeps what it has; what was typed or scanned here is added to it.
                    stored = loaded
                    moveTo = null
                    draft = loaded.draft.mergedWith(current)
                }
            }
        }
    }
    if (confirmDelete) {
        Confirm(
            message = R.string.contact_delete_question,
            action = R.string.contact_delete,
            onConfirm = {
                confirmDelete = false
                // Into the recycle bin, so it can be put back.
                stored?.let { contact ->
                    run {
                        app.trash.trash(listOf(contact.contactId))
                        onDone
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * [extra] added to this contact: its empty name and work fields filled in, values it lacks appended,
 * notes joined. Phone numbers count as the same when their last ten digits match.
 */
private fun ContactDraft.mergedWith(extra: ContactDraft): ContactDraft {
    fun pick(own: String, other: String) = own.ifBlank { other.trim() }
    fun key(field: ContactField, value: String) =
        if (field == ContactField.Phone) value.filter(Char::isDigit).takeLast(10) else value.trim().lowercase()
    var merged = copy(
        prefix = pick(prefix, extra.prefix), given = pick(given, extra.given), middle = pick(middle, extra.middle),
        family = pick(family, extra.family), suffix = pick(suffix, extra.suffix), nickname = pick(nickname, extra.nickname),
        company = pick(company, extra.company), jobTitle = pick(jobTitle, extra.jobTitle), department = pick(department, extra.department),
        note = listOf(note.trim(), extra.note.trim()).filter(String::isNotEmpty).distinct().joinToString("\n"),
        customFields = customFields + extra.customFields
            .filter { new -> new.value.isNotBlank() && customFields.none { it.label == new.label && it.value == new.value } }
            .map { it.copy(dataId = null) },
    )
    ContactField.entries.forEach { field ->
        val have = merged.values(field)
        val known = have.map { key(field, it.value) }.toSet()
        val added = extra.values(field).filter { it.value.isNotBlank() && key(field, it.value) !in known }.map { it.copy(dataId = null) }
        merged = merged.with(field, have + added)
    }
    return merged
}

/** Picks a contact to add the new details to: a search field over the whole list. */
@Composable
private fun ExistingContactPicker(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val app = LocalContext.current.container
    val contacts by remember { app.contacts.observe() }.collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    val shown = remember(contacts, query) {
        val text = query.trim()
        if (text.isEmpty()) contacts else contacts.filter { it.name.contains(text, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contact_add_to_existing)) },
        text = {
            Column {
                CompactTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.search_hint),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth().focusOnOpen(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.id }) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(row.id) }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(row.name, row.photoUri, 34.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(row.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A section opened from its chip starts with one empty row, so there is something to type into. */
private fun ContactDraft.withFirstRow(section: Section): ContactDraft {
    fun add(field: ContactField) = if (values(field).isEmpty()) with(field, listOf(EditableValue("", field.defaultType))) else this
    return when (section) {
        Section.Address -> add(ContactField.Address)
        Section.Event -> add(ContactField.Event)
        Section.Relation -> add(ContactField.Relation)
        Section.Website -> add(ContactField.Website)
        Section.Sip -> add(ContactField.Sip)
        Section.Custom -> if (customFields.isEmpty()) copy(customFields = listOf(CustomField("", ""))) else this
        else -> this
    }
}

/**
 * Where a new contact goes unless the user picks otherwise: the account used last in this app, else
 * the one Android is set to use for new contacts, else the Google account.
 */
internal fun defaultAccount(app: AppContainer, accounts: List<ContactAccount>): ContactAccount =
    accounts.firstOrNull { accountKey(it) == app.newContactAccount }
        ?: app.editor.systemDefault()?.let { system -> accounts.firstOrNull { it == system } }
        ?: accounts.last()

internal fun accountKey(account: ContactAccount): String = "${account.type.orEmpty()}|${account.name.orEmpty()}"

@Composable
internal fun accountLabel(account: ContactAccount): String =
    account.name.takeIf { account.isGoogle } ?: stringResource(R.string.storage_phone)

/** A rounded card with the kind's icon on the leading side and its fields beside it. */
@Composable
private fun EditorSection(icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 14.dp).size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun NameFields(draft: ContactDraft, onChange: (ContactDraft) -> Unit) {
    val extras = listOf(draft.prefix, draft.middle, draft.suffix, draft.phoneticGiven, draft.phoneticMiddle, draft.phoneticFamily, draft.nickname)
    var more by remember { mutableStateOf(extras.any(String::isNotBlank)) }
    val words = KeyboardCapitalization.Words
    if (more) TextEntry(R.string.field_prefix, draft.prefix, capitalization = words) { onChange(draft.copy(prefix = it)) }
    TextEntry(R.string.field_first_name, draft.given, capitalization = words) { onChange(draft.copy(given = it)) }
    if (more) TextEntry(R.string.field_middle_name, draft.middle, capitalization = words) { onChange(draft.copy(middle = it)) }
    TextEntry(R.string.field_last_name, draft.family, capitalization = words) { onChange(draft.copy(family = it)) }
    if (more) {
        TextEntry(R.string.field_suffix, draft.suffix, capitalization = words) { onChange(draft.copy(suffix = it)) }
        TextEntry(R.string.field_phonetic_first, draft.phoneticGiven, capitalization = words) { onChange(draft.copy(phoneticGiven = it)) }
        TextEntry(R.string.field_phonetic_middle, draft.phoneticMiddle, capitalization = words) { onChange(draft.copy(phoneticMiddle = it)) }
        TextEntry(R.string.field_phonetic_last, draft.phoneticFamily, capitalization = words) { onChange(draft.copy(phoneticFamily = it)) }
        TextEntry(R.string.field_nickname, draft.nickname, capitalization = words) { onChange(draft.copy(nickname = it)) }
    }
    SmallTextButton(onClick = { more = !more }) {
        Icon(if (more) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(if (more) R.string.name_less else R.string.name_more))
    }
}

@Composable
private fun PhotoPicker(name: String, savedPhoto: String?, edit: PhotoEdit, onEdit: (PhotoEdit) -> Unit) {
    // A picked or taken photo goes through the round crop first.
    var cropping by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cropping = uri.toString()
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    // The camera app takes the picture, so this app needs no camera permission.
    val context = LocalContext.current
    var photoFile by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) cropping = photoFile
    }
    val takePhoto = {
        val folder = File(context.cacheDir, "camera").apply { mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", File(folder, "photo-${System.currentTimeMillis()}.jpg"))
        photoFile = uri.toString()
        try {
            camera.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pick()
        }
    }
    cropping?.let { source ->
        PhotoCropDialog(
            source.toUri(),
            onCropped = {
                cropping = null
                onEdit(PhotoEdit.Replace(it))
            },
            onDismiss = { cropping = null },
        )
    }
    var choosing by remember { mutableStateOf(false) }
    val shown = when (edit) {
        PhotoEdit.Keep -> savedPhoto
        PhotoEdit.Remove -> null
        is PhotoEdit.Replace -> edit.source.toString()
        is PhotoEdit.Bytes -> savedPhoto
    }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(100.dp).stretchy().clickable { choosing = true }) {
            Avatar(name.ifBlank { "?" }, shown, 100.dp)
            DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.photo_camera)) },
                    leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                    onClick = {
                        choosing = false
                        takePhoto()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.photo_gallery)) },
                    leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                    onClick = {
                        choosing = false
                        pick()
                    },
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 3.dp,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-1).dp, y = (-1).dp).size(32.dp),
            ) {
                Icon(
                    Icons.Filled.CameraAlt,
                    contentDescription = stringResource(if (shown == null) R.string.photo_add else R.string.photo_change),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(7.dp),
                )
            }
        }
        if (shown != null) {
            IconButton(onClick = { onEdit(if (savedPhoto == null) PhotoEdit.Keep else PhotoEdit.Remove) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.photo_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Where the contact is saved, small under the title; for a new contact it opens a menu of accounts. */
@Composable
internal fun AccountPicker(
    accounts: List<ContactAccount>,
    selected: ContactAccount,
    editable: Boolean,
    description: String? = null,
    fontSize: Int = 13,
    defaultAccount: ContactAccount? = null,
    onPick: (ContactAccount) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val saveTo = description ?: stringResource(if (editable) R.string.save_to else R.string.saved_in)
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = editable, onClickLabel = saveTo) { open = true }
                .padding(end = 2.dp, top = 1.dp, bottom = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (selected.isGoogle) Icons.Outlined.Cloud else Icons.Outlined.Smartphone,
                contentDescription = saveTo,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size((fontSize + 2).dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                accountLabel(selected) + if (selected == defaultAccount) " · " + stringResource(R.string.storage_default) else "",
                fontSize = fontSize.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (editable) Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            accounts.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(accountLabel(option))
                            if (option == defaultAccount) {
                                Text(stringResource(R.string.storage_default), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(if (option.isGoogle) Icons.Outlined.Cloud else Icons.Outlined.Smartphone, contentDescription = null)
                    },
                    trailingIcon = if (option == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    onClick = {
                        onPick(option)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ValueRows(
    field: ContactField,
    values: List<EditableValue>,
    label: Int,
    addLabel: Int,
    keyboard: KeyboardType,
    singleLine: Boolean = true,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    onChange: (List<EditableValue>) -> Unit,
) {
    fun replace(index: Int, value: EditableValue) = onChange(values.toMutableList().also { it[index] = value })

    values.forEachIndexed { index, value ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompactTextField(
                value = value.value,
                onValueChange = { replace(index, value.copy(value = it)) },
                label = stringResource(label),
                singleLine = singleLine,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = capitalization),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            TypePicker(field, value, onPick = { replace(index, value.copy(type = it, label = null)) })
            RemoveButton { onChange(values.filterIndexed { i, _ -> i != index }) }
        }
    }
    AddButton(addLabel) { onChange(values + EditableValue("", nextType(field, values))) }
}

/** A second birthday makes no sense, so the next date added is an anniversary, and so on. */
private fun nextType(field: ContactField, values: List<EditableValue>): Int =
    field.types.firstOrNull { type -> values.none { it.type == type } } ?: field.defaultType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRows(values: List<EditableValue>, onChange: (List<EditableValue>) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf<Int?>(null) }
    fun replace(index: Int, value: EditableValue) = onChange(values.toMutableList().also { it[index] = value })

    values.forEachIndexed { index, value ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.weight(1f).heightIn(min = 40.dp).padding(top = 6.dp).clickable { picking = index },
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (value.value.isBlank()) stringResource(R.string.event_pick) else formatContactDate(context, value.value),
                        fontSize = 15.sp,
                        color = if (value.value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            TypePicker(ContactField.Event, value, onPick = { replace(index, value.copy(type = it, label = null)) })
            RemoveButton { onChange(values.filterIndexed { i, _ -> i != index }) }
        }
    }
    AddButton(R.string.add_event) { onChange(values + EditableValue("", nextType(ContactField.Event, values))) }
    // A date row is only useful with a date, so a new empty one goes straight to the picker.
    LaunchedEffect(values.size) {
        val empty = values.indexOfLast { it.value.isBlank() }
        if (empty >= 0 && picking == null) picking = empty
    }

    val index = picking ?: return
    val value = values.getOrNull(index) ?: return
    val yearless = value.value.startsWith("--")
    val initial = runCatching {
        if (yearless) LocalDate.parse("2000" + value.value.drop(1)) else LocalDate.parse(value.value.take(10))
    }.getOrNull()
    val state = rememberDatePickerState(initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
    var noYear by remember(index) { mutableStateOf(yearless) }
    DatePickerDialog(
        onDismissRequest = {
            picking = null
            if (value.value.isBlank()) onChange(values.filterIndexed { i, _ -> i != index })
        },
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    val text = if (noYear) "--%02d-%02d".format(day.monthValue, day.dayOfMonth) else day.toString()
                    replace(index, value.copy(value = text))
                }
                picking = null
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        // The calendar fills the dialog, so the year switch sits in its title line.
        DatePicker(
            state = state,
            showModeToggle = true,
            title = {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.event_pick), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = noYear,
                        onClick = { noYear = !noYear },
                        label = { Text(stringResource(R.string.event_no_year)) },
                        leadingIcon = if (noYear) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun CustomRows(values: List<CustomField>, onChange: (List<CustomField>) -> Unit) {
    fun replace(index: Int, value: CustomField) = onChange(values.toMutableList().also { it[index] = value })
    values.forEachIndexed { index, value ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompactTextField(
                value = value.label,
                onValueChange = { replace(index, value.copy(label = it)) },
                label = stringResource(R.string.field_custom_label),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(0.42f),
            )
            Spacer(Modifier.width(6.dp))
            CompactTextField(
                value = value.value,
                onValueChange = { replace(index, value.copy(value = it)) },
                label = stringResource(R.string.field_custom_value),
                modifier = Modifier.weight(0.58f),
            )
            RemoveButton { onChange(values.filterIndexed { i, _ -> i != index }) }
        }
    }
    AddButton(R.string.add_custom) { onChange(values + CustomField("", "")) }
}

@Composable
private fun TypePicker(field: ContactField, value: EditableValue, onPick: (Int) -> Unit) {
    val editor = LocalContext.current.container.editor
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(top = 6.dp)) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { open = true },
        ) {
            Row(Modifier.padding(start = 10.dp, end = 2.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    editor.typeLabel(field, value.type, value.label),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.widthIn(max = 92.dp),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            field.types.forEach { type ->
                DropdownMenuItem(
                    text = { Text(editor.typeLabel(field, type, null)) },
                    onClick = {
                        onPick(type)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.padding(top = 6.dp).size(34.dp)) {
        Icon(Icons.Filled.Close, stringResource(R.string.action_remove_row), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AddButton(label: Int, onClick: () -> Unit) {
    SmallTextButton(onClick = onClick) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(label))
    }
}

@Composable
private fun TextEntry(
    label: Int,
    value: String,
    singleLine: Boolean = true,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    onChange: (String) -> Unit,
) {
    CompactTextField(
        value = value,
        onValueChange = onChange,
        label = stringResource(label),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(capitalization = capitalization),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SmallTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 6.dp),
        modifier = modifier.height(32.dp),
        content = content,
    )
}

@Composable
private fun Confirm(message: Int, action: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(action), color = EndRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
