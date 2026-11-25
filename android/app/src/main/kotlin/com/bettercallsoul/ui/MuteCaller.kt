package com.bettercallsoul.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettercallsoul.R
import com.bettercallsoul.clockTime
import com.bettercallsoul.container
import kotlinx.coroutines.delay

private enum class MuteUnit(val minutes: Int, val label: Int, val plural: Int) {
    Minutes(1, R.string.mute_unit_minutes, R.plurals.mute_minutes),
    Hours(60, R.string.mute_unit_hours, R.plurals.mute_hours),
    Days(24 * 60, R.string.mute_unit_days, R.plurals.mute_days),
}

private val CHOICES = listOf(10 to MuteUnit.Minutes, 30 to MuteUnit.Minutes, 8 to MuteUnit.Hours, 1 to MuteUnit.Days)
private const val LONGEST_MINUTES = 30 * 24 * 60

/** When the muting of these numbers ends, kept current: it goes back to null by itself once it does. */
@Composable
internal fun mutedUntil(numbers: List<String>): Long? {
    val muted = LocalContext.current.container.muted
    val entries by muted.entries.collectAsStateWithLifecycle()
    val until by produceState(muted.until(numbers), entries, numbers) {
        while (true) {
            value = muted.until(numbers)
            val left = (value ?: return@produceState) - System.currentTimeMillis()
            delay(left.coerceIn(500L, 60_000L))
        }
    }
    return until
}

/** The bell in a person's toolbar: crossed out and coloured while their calls ring without sound. */
@Composable
internal fun MuteToolbarIcon(
    numbers: List<String>,
    name: String?,
    active: Color = MaterialTheme.colorScheme.primary,
    idle: Color = MaterialTheme.colorScheme.onBackground,
    onChanged: () -> Unit = {},
) {
    if (numbers.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val until = mutedUntil(numbers)
    IconButton(onClick = { open = true }, modifier = Modifier.stretchy()) {
        Icon(
            if (until != null) Icons.Filled.NotificationsOff else Icons.Outlined.Notifications,
            stringResource(R.string.mute_title),
            tint = if (until != null) active else idle,
        )
    }
    if (open) MuteDialog(numbers, name, until, onDismiss = { open = false }, onChanged = onChanged)
}

/** Under the name, while the person's calls ring without sound. */
@Composable
internal fun MutedBadge(numbers: List<String>) {
    val until = mutedUntil(numbers) ?: return
    val context = LocalContext.current
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.muted_until, clockTime(context, until)), fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MuteDialog(numbers: List<String>, name: String?, until: Long?, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val muted = context.container.muted
    DurationDialog(
        title = stringResource(R.string.mute_title),
        intro = {
            Text(
                if (until != null) stringResource(R.string.muted_until, clockTime(context, until)) else stringResource(R.string.mute_explain),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        action = stringResource(R.string.mute_action),
        onPick = { length ->
            muted.mute(numbers, name, length)
            onChanged()
            onDismiss()
        },
        onDismiss = onDismiss,
        end = {
            if (until != null) {
                TextButton(onClick = {
                    muted.unmute(numbers)
                    onChanged()
                    onDismiss()
                }) { Text(stringResource(R.string.mute_end)) }
            }
        },
    )
}

/** A few lengths to tap, and one of the user's own, which starts at an hour. */
@Composable
internal fun DurationDialog(
    title: String,
    intro: @Composable () -> Unit,
    action: String,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    end: @Composable () -> Unit = {},
) {
    var custom by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf(MuteUnit.Hours) }
    val minutes = custom.toIntOrNull()?.let { it * unit.minutes }?.takeIf { it in 1..LONGEST_MINUTES }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                intro()
                Spacer(Modifier.padding(top = 4.dp))
                CHOICES.forEach { (count, choiceUnit) ->
                    Text(
                        pluralStringResource(choiceUnit.plural, count, count),
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(count * choiceUnit.minutes) }.padding(vertical = 11.dp, horizontal = 4.dp),
                    )
                }
                Spacer(Modifier.padding(top = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { typed -> custom = typed.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.mute_custom)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(enabled = minutes != null, onClick = { minutes?.let(onPick) }) { Text(action) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuteUnit.entries.forEach { option ->
                        FilterChip(
                            selected = unit == option,
                            onClick = { unit = option },
                            label = { Text(stringResource(option.label)) },
                        )
                    }
                }
            }
        },
        confirmButton = end,
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
