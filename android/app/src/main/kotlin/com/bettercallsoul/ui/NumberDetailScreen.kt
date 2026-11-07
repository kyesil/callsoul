package com.bettercallsoul.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material.icons.automirrored.outlined.Label
import android.content.ContentValues
import android.provider.BlockedNumberContract.BlockedNumbers
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bettercallsoul.R
import com.bettercallsoul.calllog.CallEntry
import com.bettercallsoul.call.SpamBadge
import com.bettercallsoul.call.SpamQuestion
import com.bettercallsoul.call.TagDialog
import com.bettercallsoul.container
import com.bettercallsoul.spam.Verdict
import com.bettercallsoul.telecom.CallerInfo
import kotlinx.coroutines.launch

/** A saved number opens its contact's profile: this page is only for numbers outside the contacts. */
@Composable
fun NumberPage(number: String, onCall: (String) -> Unit, onOpen: (Route) -> Unit, onBack: () -> Unit) {
    val app = LocalContext.current.container
    var checked by remember(number) { mutableStateOf(false) }
    var contactId by remember(number) { mutableStateOf<Long?>(null) }
    LaunchedEffect(number) {
        contactId = runCatching { app.contacts.match(number)?.id }.getOrNull()
        checked = true
    }
    val id = contactId
    when {
        !checked -> Unit
        id != null -> ContactDetailScreen(id, onCall = onCall, onOpen = onOpen, onBack = onBack)
        else -> NumberDetailScreen(number, onCall = onCall, onOpen = onOpen, onBack = onBack)
    }
}

@Composable
private fun NumberDetailScreen(number: String, onCall: (String) -> Unit, onOpen: (Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.container
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf(CallerInfo(number = number, title = number)) }
    var history by remember { mutableStateOf(emptyList<CallEntry>()) }
    var editingTag by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }

    suspend fun load() {
        val local = app.directory.local(number)
        info = local
        history = app.callHistory(listOf(number), local.title)
        info = app.directory.remote(local)
    }
    LaunchedEffect(number) {
        load()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            DetailToolbar(onBack)
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(info.title, info.photoUri, 96.dp)
                Spacer(Modifier.height(7.dp))
                // A long press on the name or number copies the number.
                val haptics = LocalHapticFeedback.current
                val copiedNote = stringResource(R.string.number_copied)
                Row(
                    Modifier.combinedClickable(onClick = {}, onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        copyText(context, number, copiedNote)
                    }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(info.title, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
                    if (info.isTagged) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Outlined.Label,
                            contentDescription = stringResource(R.string.tag),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                info.subtitle?.let {
                    Text(it, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (info.verdict == Verdict.Spam) SpamBadge(info)
                TextButton(onClick = { editingTag = true }) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (info.isTagged) R.string.tag_edit else R.string.tag_add))
                }
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RoundAction(Icons.Filled.Call, R.string.action_call, CallGreen) { onCall(number) }
                    RoundAction(Icons.AutoMirrored.Filled.Message, R.string.action_message, MessageBlue) {
                        sendSms(context, number)
                    }
                }
            }
            if (history.isNotEmpty()) {
                DayHeader(stringResource(R.string.section_history))
                Card {
                    HistoryRows(history)
                }
            }
            if (reporting) {
                Spacer(Modifier.height(6.dp))
                Card {
                    SpamQuestion(number = number, onFinished = { reporting = false })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            BarAction(Icons.Outlined.PersonAdd, R.string.action_add_to_contacts) { onOpen(Route.NewContact(number)) }
            BarAction(Icons.Filled.Block, if (blocked) R.string.action_unblock else R.string.action_block) {
                blocked = toggleBlock(context, number, blocked)
            }
            BarAction(Icons.Outlined.Flag, R.string.action_report) { reporting = true }
        }
    }

    if (editingTag) {
        TagDialog(
            number = number,
            onDismiss = {
                editingTag = false
                scope.launch { load() }
            },
        )
    }
}

/** Only the default phone app may write here, so this quietly does nothing before that. */
private fun toggleBlock(context: android.content.Context, number: String, blocked: Boolean): Boolean = try {
    val resolver = context.contentResolver
    if (blocked) {
        resolver.delete(BlockedNumbers.CONTENT_URI, "${BlockedNumbers.COLUMN_ORIGINAL_NUMBER} = ?", arrayOf(number))
        false
    } else {
        val values = ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }
        resolver.insert(BlockedNumbers.CONTENT_URI, values)
        true
    }
} catch (_: SecurityException) {
    blocked
}

/** A number's or a contact's calls, with a thin line between them. */
@Composable
internal fun HistoryRows(entries: List<CallEntry>, showNumber: Boolean = false) {
    entries.forEachIndexed { index, entry ->
        if (index > 0) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 50.dp, end = 16.dp),
            )
        }
        HistoryRow(entry, showNumber)
    }
}

/** One call in a history list; [showNumber] adds which number it was, for a contact with several. */
@Composable
internal fun HistoryRow(entry: CallEntry, showNumber: Boolean = false) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CallKindIcon(entry)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                dayHeader(context, entry.date),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (showNumber) Text(entry.number, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CallTimes(entry)
    }
}

@Composable
internal fun RoundAction(icon: ImageVector, label: Int, color: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = color, modifier = Modifier.size(48.dp).stretchy()) {
        Icon(icon, stringResource(label), tint = Color.White, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier.stretchy().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(label), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}
