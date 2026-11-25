package com.bettercallsoul.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettercallsoul.R
import com.bettercallsoul.Reminders
import com.bettercallsoul.clockTime
import com.bettercallsoul.container

private const val LONGEST_NOTE = 200

@Composable
private fun remindersAbout(numbers: List<String>): List<Reminders.Reminder> {
    val all by LocalContext.current.container.reminders.all.collectAsStateWithLifecycle()
    return remember(all, numbers) { Reminders.about(all, numbers) }
}

/** The clock in a person's toolbar: coloured while there is a reminder to call them. */
@Composable
internal fun RemindToolbarIcon(numbers: List<String>) {
    if (numbers.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val set = remindersAbout(numbers).isNotEmpty()
    IconButton(onClick = { open = true }, modifier = Modifier.stretchy()) {
        Icon(
            if (set) Icons.Filled.Alarm else Icons.Outlined.Alarm,
            stringResource(R.string.remind_title),
            tint = if (set) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        )
    }
    if (open) RemindDialog(numbers, onDismiss = { open = false })
}

/** Under the name: when the next reminder to call them comes. */
@Composable
internal fun RemindBadge(numbers: List<String>) {
    val next = remindersAbout(numbers).firstOrNull() ?: return
    Row(Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(line(next), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun line(reminder: Reminders.Reminder): String =
    listOfNotNull(stringResource(R.string.remind_at, clockTime(LocalContext.current, reminder.at)), reminder.note).joinToString(" · ")

@Composable
private fun RemindDialog(numbers: List<String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val reminders = context.container.reminders
    var note by remember { mutableStateOf("") }
    val exact = remember { reminders.exact }
    DurationDialog(
        title = stringResource(R.string.remind_title),
        intro = {
            Column {
                remindersAbout(numbers).forEach { reminder ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(line(reminder), Modifier.weight(1f), fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { reminders.remove(reminder.id) }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.action_remove_row), modifier = Modifier.size(18.dp))
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(LONGEST_NOTE) },
                    label = { Text(stringResource(R.string.remind_note)) },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null)),
                            )
                        }
                    }) { Text(stringResource(R.string.remind_exact), fontSize = 13.sp) }
                }
            }
        },
        action = stringResource(R.string.remind_action),
        onPick = { length ->
            reminders.add(numbers, note, length)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}
