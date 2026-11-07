package com.bettercallsoul.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Restore
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettercallsoul.R
import com.bettercallsoul.calllog.ContactAccount
import com.bettercallsoul.calllog.ContactTrash
import com.bettercallsoul.calllog.TrashedContact
import com.bettercallsoul.container
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/**
 * The contacts deleted in this app, one storage at a time: the phone or a Google account, the
 * default one first. Each can be put back where it came from or deleted for good.
 */
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.container
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val accounts = remember { app.editor.accounts() }
    val defaultStorage = remember { defaultAccount(app, accounts) }
    var storage by remember { mutableStateOf(defaultStorage) }
    val all by remember { app.trash.observe() }.collectAsStateWithLifecycle(emptyList())
    LaunchedEffect(Unit) { app.trash.purgeOld() }
    // Anything not in a Google account was on the phone itself, whatever the maker calls that storage.
    val shown = remember(all, storage) { all.filter { bucketOf(it.account) == storage } }
    var forget by remember { mutableStateOf<TrashedContact?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ToolbarRow {
            ToolbarIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(stringResource(R.string.trash_title), fontSize = 19.sp, color = MaterialTheme.colorScheme.onBackground)
                AccountPicker(
                    accounts = accounts,
                    selected = storage,
                    editable = accounts.size > 1,
                    description = stringResource(R.string.trash_storage),
                    defaultAccount = defaultStorage,
                    onPick = { storage = it },
                )
            }
            if (shown.isNotEmpty()) {
                ToolbarIcon(Icons.Outlined.DeleteSweep, stringResource(R.string.trash_empty_all)) { confirmEmpty = true }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    pluralStringResource(R.plurals.trash_note, ContactTrash.KEEP_DAYS, ContactTrash.KEEP_DAYS),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                )
            }
            if (storage.isGoogle) {
                item { GoogleTrashCard(storage) }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.trash_none),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                    )
                }
            } else {
                item {
                    Card {
                        shown.forEach { entry ->
                            TrashRow(
                                entry = entry,
                                photo = app.trash.photoUri(entry),
                                onRestore = {
                                    scope.launch {
                                        // A Google account removed from the phone since: back onto the phone instead.
                                        val to = if (entry.account.isGoogle && entry.account !in accounts) PHONE else entry.account
                                        val ok = runCatching { app.trash.restore(entry, to) }.isSuccess
                                        Toast.makeText(
                                            context,
                                            if (ok) resources.getString(R.string.trash_restored, entry.name) else resources.getString(R.string.contact_save_failed),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                                onForget = { forget = entry },
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.padding(bottom = 24.dp)) }
        }
    }

    forget?.let { entry ->
        ConfirmDialog(
            message = stringResource(R.string.trash_forget_question, entry.name),
            icon = Icons.Outlined.DeleteForever,
            action = stringResource(R.string.trash_forget),
            onConfirm = {
                forget = null
                scope.launch { app.trash.remove(entry) }
            },
            onDismiss = { forget = null },
        )
    }
    if (confirmEmpty) {
        ConfirmDialog(
            message = pluralStringResource(R.plurals.trash_empty_question, shown.size, shown.size),
            icon = Icons.Outlined.DeleteSweep,
            action = stringResource(R.string.trash_forget),
            onConfirm = {
                val entries = shown
                confirmEmpty = false
                scope.launch { entries.forEach { app.trash.remove(it) } }
            },
            onDismiss = { confirmEmpty = false },
        )
    }
}

private val PHONE = ContactAccount(null, null)

private fun bucketOf(account: ContactAccount): ContactAccount = if (account.isGoogle) account else PHONE

@Composable
private fun TrashRow(entry: TrashedContact, photo: String?, onRestore: () -> Unit, onForget: () -> Unit) {
    val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - entry.deletedAt).toInt()
    val left = (ContactTrash.KEEP_DAYS - days).coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(entry.name.ifBlank { "?" }, photo, 38.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name.ifBlank { stringResource(R.string.contact_unnamed) },
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                pluralStringResource(R.plurals.trash_days_left, left, left),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRestore) {
            Icon(Icons.Outlined.Restore, stringResource(R.string.trash_restore), tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onForget) {
            Icon(Icons.Outlined.DeleteForever, stringResource(R.string.trash_forget), tint = EndRed)
        }
    }
}

/**
 * Google keeps its own trash for contacts synced to the account, also for 30 days. Android has no
 * way to read it, so this opens it in Google Contacts on the web, signed in as that account.
 */
@Composable
private fun GoogleTrashCard(account: ContactAccount) {
    val context = LocalContext.current
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open(context, "https://contacts.google.com/trash?authuser=" + Uri.encode(account.name.orEmpty())) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.trash_google_title), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.trash_google_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
