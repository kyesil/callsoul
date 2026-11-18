package com.bettercallsoul.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bettercallsoul.R
import com.bettercallsoul.calllog.SearchHit
import com.bettercallsoul.container
import com.bettercallsoul.spam.SearchText
import com.bettercallsoul.spam.T9

/** The keys that can hold a speed dial. */
internal val SpeedDialKeys = ('1'..'9').toList()

/**
 * Picks who a key calls: a contact found by name or number, or the number as typed. With a number
 * already on the key, it can also be freed.
 */
@Composable
internal fun SpeedDialDialog(key: Char, onDismiss: () -> Unit) {
    val app = LocalContext.current.container
    val current = app.speedDials[key]
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf(emptyList<SearchHit>()) }
    var calls by remember { mutableStateOf<Map<String, Int>?>(null) }
    LaunchedEffect(Unit) { calls = app.history.outgoingCounts() }
    LaunchedEffect(query, calls) {
        val typed = query.trim()
        hits = if (typed.isEmpty()) {
            emptyList()
        } else {
            // Letters are looked up on the keys like the keypad does, then kept only where the name holds them.
            val letters = typed.any(Char::isLetter)
            app.contacts.keypadSearch(T9.encode(typed), limit = 40, calls = calls.orEmpty())
                .filter { !letters || SearchText.fold(it.name).contains(SearchText.fold(typed), ignoreCase = true) }
                .take(MAX_HITS)
        }
    }
    fun pick(number: String?) {
        app.setSpeedDial(key, number)
        onDismiss()
    }
    val typedNumber = query.filter { it.isDigit() || it == '+' }.takeIf { it.count(Char::isDigit) >= 3 && query.none(Char::isLetter) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.speed_dial_key, key.toString())) },
        text = {
            Column {
                if (current != null) {
                    Text(stringResource(R.string.speed_dial_now, current), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    label = { Text(stringResource(R.string.speed_dial_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth().focusOnOpen(),
                )
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    if (typedNumber != null && hits.none { sameDigits(it.number, typedNumber) }) {
                        Text(
                            stringResource(R.string.speed_dial_use_number, typedNumber),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().clickable { pick(typedNumber) }.padding(vertical = 12.dp),
                        )
                    }
                    hits.forEach { hit ->
                        Row(
                            Modifier.fillMaxWidth().clickable { pick(hit.number) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(hit.name, hit.photoUri, 34.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(hit.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                Text(hit.number, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (current != null) TextButton(onClick = { pick(null) }) { Text(stringResource(R.string.speed_dial_remove)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The speed dial keys in the settings: each key with who it calls; a tap changes it. */
@Composable
internal fun SpeedDialSettings() {
    val app = LocalContext.current.container
    var editing by remember { mutableStateOf<Char?>(null) }
    // The contact names of the numbers on the keys, looked up again whenever a key changes.
    var names by remember { mutableStateOf(emptyMap<Char, String>()) }
    LaunchedEffect(app.speedDials) {
        names = app.speedDials.mapNotNull { (key, number) -> app.contacts.match(number)?.name?.takeIf(String::isNotBlank)?.let { key to it } }.toMap()
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.speed_dial_title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.speed_dial_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.Dialpad, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(6.dp))
            SpeedDialKeys.forEach { key ->
                val number = app.speedDials[key]
                Row(
                    Modifier.fillMaxWidth().clickable { editing = key }.padding(start = 10.dp, end = 4.dp).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(key.toString(), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        if (number == null) {
                            Text(stringResource(R.string.speed_dial_empty), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(names[key] ?: number, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                            if (names[key] != null) Text(number, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (number != null) {
                        IconButton(onClick = { app.setSpeedDial(key, null) }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.speed_dial_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
    editing?.let { key -> SpeedDialDialog(key, onDismiss = { editing = null }) }
}

private fun sameDigits(a: String, b: String): Boolean {
    val x = a.filter(Char::isDigit).takeLast(10)
    return x.isNotEmpty() && x == b.filter(Char::isDigit).takeLast(10)
}

private const val MAX_HITS = 12
