package com.bettercallsoul.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bettercallsoul.R
import com.bettercallsoul.calllog.ContactAccount
import com.bettercallsoul.calllog.LabelInfo
import com.bettercallsoul.container
import kotlinx.coroutines.launch

/**
 * The contact labels, by account, with how many people each holds. A tap shows the people in the
 * search; the menu renames or deletes; the plus makes a new one.
 */
@Composable
fun LabelsScreen(onOpen: (Route) -> Unit, onBack: () -> Unit) {
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var labels by remember { mutableStateOf<List<LabelInfo>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<LabelInfo?>(null) }
    var deleting by remember { mutableStateOf<LabelInfo?>(null) }
    LaunchedEffect(reload) { labels = runCatching { app.editor.allLabels() }.getOrDefault(emptyList()) }

    fun change(action: suspend () -> Unit) {
        scope.launch {
            runCatching { action() }
            reload++
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        DetailToolbar(onBack) {
            ToolbarIcon(Icons.Filled.Add, stringResource(R.string.label_new)) { creating = true }
        }
        LargeTitle(stringResource(R.string.section_labels), null)
        val shown = labels ?: return@Column
        if (shown.isEmpty()) {
            Text(
                stringResource(R.string.labels_empty),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            )
        }
        val accounts = shown.groupBy { it.account }
        accounts.forEach { (account, list) ->
            // One account needs no heading of its own.
            if (accounts.size > 1) DayHeader(accountLabel(account))
            Card {
                list.forEachIndexed { index, label ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(start = 52.dp))
                    }
                    LabelRow(
                        label,
                        onClick = { onOpen(Route.SearchLabel(label.title)) },
                        onRename = { renaming = label },
                        onDelete = { deleting = label },
                    )
                }
            }
            Spacer(Modifier.padding(4.dp))
        }
        Spacer(Modifier.padding(12.dp))
    }

    if (creating) {
        val choices = remember { app.editor.accounts().filter { it.isGoogle }.ifEmpty { listOf(ContactAccount(null, null)) } }
        NameDialog(
            title = stringResource(R.string.label_new),
            start = "",
            accounts = choices,
            onDismiss = { creating = false },
        ) { name, account ->
            creating = false
            change { app.editor.createGroup(account ?: choices.first(), name) }
        }
    }
    renaming?.let { label ->
        NameDialog(title = stringResource(R.string.label_rename), start = label.title, accounts = emptyList(), onDismiss = { renaming = null }) { name, _ ->
            renaming = null
            change { app.editor.renameGroup(label, name) }
        }
    }
    deleting?.let { label ->
        ConfirmDialog(
            message = stringResource(R.string.label_delete_question, label.title),
            icon = Icons.Outlined.Delete,
            action = stringResource(R.string.action_delete),
            onConfirm = {
                deleting = null
                change { app.editor.deleteGroup(label) }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun LabelRow(label: LabelInfo, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label.title, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            Text(pluralStringResource(R.plurals.label_members, label.count, label.count), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.label_rename)) },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = {
                        menu = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.label_delete)) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/** A name to type, and for a new label the account it goes in when there is more than one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameDialog(
    title: String,
    start: String,
    accounts: List<ContactAccount>,
    onDismiss: () -> Unit,
    onSave: (String, ContactAccount?) -> Unit,
) {
    var name by remember { mutableStateOf(start) }
    var account by remember { mutableStateOf(accounts.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().focusOnOpen(),
                )
                if (accounts.size > 1) {
                    Text(stringResource(R.string.label_account), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        accounts.forEach { choice ->
                            FilterChip(selected = choice == account, onClick = { account = choice }, label = { Text(accountLabel(choice)) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && name.trim() != start, onClick = { onSave(name.trim(), account) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
