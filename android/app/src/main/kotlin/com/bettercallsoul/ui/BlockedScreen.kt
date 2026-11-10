package com.bettercallsoul.ui

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.BlockedNumberContract.BlockedNumbers
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bettercallsoul.R
import com.bettercallsoul.container
import com.bettercallsoul.spam.SearchText
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A number in Android's block list, with the contact it belongs to when there is one. */
private data class BlockedEntry(val id: Long, val number: String, val e164: String?, val name: String?, val photoUri: String?)

/** Android's block list, which only the default phone app (or SMS app) may read. Null when it can't be read. */
private suspend fun blockedNumbers(context: Context): List<BlockedEntry>? = withContext(Dispatchers.IO) {
    val rows = runCatching {
        context.contentResolver.query(
            BlockedNumbers.CONTENT_URI,
            arrayOf(BlockedNumbers.COLUMN_ID, BlockedNumbers.COLUMN_ORIGINAL_NUMBER, BlockedNumbers.COLUMN_E164_NUMBER),
            null, null, null,
        )?.use { buildList { while (it.moveToNext()) add(Triple(it.getLong(0), it.getString(1).orEmpty(), it.getString(2))) } }
    }.getOrNull() ?: return@withContext null
    val contacts = context.container.contacts
    rows.map { (id, number, e164) ->
        val match = runCatching { contacts.match(number) }.getOrNull()
        BlockedEntry(id, number, e164?.takeIf(String::isNotBlank), match?.name?.takeIf(String::isNotBlank), match?.photoUri)
    }.sortedBy { (it.name ?: it.number).lowercase() }
}

/** The list as CSV for a spreadsheet: a BOM so Excel reads the names as UTF-8, then number, E.164 and contact. */
private fun blockedCsv(entries: List<BlockedEntry>): String = buildString {
    append('\uFEFF')
    append("number,e164,contact\r\n")
    entries.forEach { append(listOf(it.number, it.e164.orEmpty(), it.name.orEmpty()).joinToString(",", transform = ::csvField)).append("\r\n") }
}

private fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

/**
 * The numbers the user blocked, in Android's own list that every phone app shares: search them,
 * save them as CSV, unblock one, or add one. Long lists, such as one restored from an old phone, scroll lazily.
 */
@Composable
fun BlockedScreen(onOpen: (Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<BlockedEntry>?>(null) }
    var readable by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val unblocked = stringResource(R.string.blocked_removed)
    LaunchedEffect(reload) {
        val found = blockedNumbers(context)
        readable = found != null
        entries = found.orEmpty()
    }

    fun change(action: (Context) -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { action(context) } }
            reload++
        }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        val all = entries ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(blockedCsv(all).toByteArray(Charsets.UTF_8)) }
                }.isSuccess
            }
            val message = if (saved) resources.getString(R.string.blocked_exported, all.size) else resources.getString(R.string.blocked_export_failed)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val all = entries
    val text = query.trim()
    val shown = when {
        all == null -> null
        text.isEmpty() -> all
        else -> all.filter { entry -> listOfNotNull(entry.number, entry.e164, entry.name).any { SearchText.find(it, text) != null } }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        DetailToolbar(onBack) {
            if (readable && !all.isNullOrEmpty()) {
                ToolbarIcon(Icons.Filled.FileDownload, stringResource(R.string.blocked_export)) {
                    export.launch("blocked-numbers-${LocalDate.now()}.csv")
                }
            }
            if (readable) ToolbarIcon(Icons.Filled.Add, stringResource(R.string.blocked_add)) { adding = true }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                LargeTitle(
                    stringResource(R.string.blocked_title),
                    all?.takeIf { readable && it.isNotEmpty() }?.let { stringResource(R.string.blocked_count, it.size) },
                )
            }
            if (shown == null) return@LazyColumn
            when {
                !readable -> item { Note(stringResource(R.string.blocked_needs_default)) }
                all.isNullOrEmpty() -> item { Note(stringResource(R.string.blocked_empty)) }
                else -> {
                    item {
                        CompactTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = stringResource(R.string.blocked_search_hint),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (shown.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.search_none),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    itemsIndexed(shown, key = { _, entry -> entry.id }) { index, entry ->
                        BlockedRow(
                            entry = entry,
                            first = index == 0,
                            last = index == shown.lastIndex,
                            onOpen = { onOpen(Route.Number(entry.number)) },
                            onUnblock = {
                                change { it.contentResolver.delete(ContentUris.withAppendedId(BlockedNumbers.CONTENT_URI, entry.id), null, null) }
                                Toast.makeText(context, unblocked, Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
        }
    }

    if (adding) {
        var number by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.blocked_add)) },
            text = {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it.filter { c -> c.isDigit() || c in "+*#" } },
                    label = { Text(stringResource(R.string.field_phone)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().focusOnOpen(),
                )
            },
            confirmButton = {
                TextButton(enabled = number.isNotBlank(), onClick = {
                    adding = false
                    val value = number.trim()
                    change { it.contentResolver.insert(BlockedNumbers.CONTENT_URI, ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, value) }) }
                }) { Text(stringResource(R.string.action_block)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** One row of the list, drawn as a slice of a rounded card so the lazy list looks like the other pages' cards. */
@Composable
private fun BlockedRow(entry: BlockedEntry, first: Boolean, last: Boolean, onOpen: () -> Unit, onUnblock: () -> Unit) {
    val corner = 22.dp
    val shape = RoundedCornerShape(
        topStart = if (first) corner else 0.dp, topEnd = if (first) corner else 0.dp,
        bottomStart = if (last) corner else 0.dp, bottomEnd = if (last) corner else 0.dp,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        if (!first) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(start = 66.dp))
        }
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(entry.name ?: entry.number, entry.photoUri, 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name ?: entry.number, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                if (entry.name != null) Text(entry.number, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onUnblock) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_unblock), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun Note(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
    )
}
