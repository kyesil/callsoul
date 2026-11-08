package com.bettercallsoul.ui

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract.BlockedNumbers
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bettercallsoul.R
import com.bettercallsoul.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A number in Android's block list, with the contact it belongs to when there is one. */
private data class BlockedEntry(val id: Long, val number: String, val name: String?, val photoUri: String?)

/** Android's block list, which only the default phone app (or SMS app) may read. Null when it can't be read. */
private suspend fun blockedNumbers(context: Context): List<BlockedEntry>? = withContext(Dispatchers.IO) {
    val rows = runCatching {
        context.contentResolver.query(
            BlockedNumbers.CONTENT_URI,
            arrayOf(BlockedNumbers.COLUMN_ID, BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
            null, null, null,
        )?.use { buildList { while (it.moveToNext()) add(it.getLong(0) to it.getString(1).orEmpty()) } }
    }.getOrNull() ?: return@withContext null
    val contacts = context.container.contacts
    rows.map { (id, number) ->
        val match = runCatching { contacts.match(number) }.getOrNull()
        BlockedEntry(id, number, match?.name?.takeIf(String::isNotBlank), match?.photoUri)
    }.sortedBy { (it.name ?: it.number).lowercase() }
}

/** The numbers the user blocked, in Android's own list that every phone app shares: unblock one, or add one. */
@Composable
fun BlockedScreen(onOpen: (Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<BlockedEntry>?>(null) }
    var readable by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
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

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        DetailToolbar(onBack) {
            if (readable) ToolbarIcon(Icons.Filled.Add, stringResource(R.string.blocked_add)) { adding = true }
        }
        LargeTitle(stringResource(R.string.blocked_title), null)
        val shown = entries ?: return@Column
        when {
            !readable -> Note(stringResource(R.string.blocked_needs_default))
            shown.isEmpty() -> Note(stringResource(R.string.blocked_empty))
            else -> Card {
                shown.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(start = 66.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(Route.Number(entry.number)) }.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(entry.name ?: entry.number, entry.photoUri, 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.name ?: entry.number, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                            if (entry.name != null) Text(entry.number, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            change { it.contentResolver.delete(ContentUris.withAppendedId(BlockedNumbers.CONTENT_URI, entry.id), null, null) }
                            Toast.makeText(context, unblocked, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.action_unblock), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
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

@Composable
internal fun Note(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
    )
}
