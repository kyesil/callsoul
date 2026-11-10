package com.bettercallsoul.call

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledIconButton
import com.bettercallsoul.ui.focusOnOpen
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bettercallsoul.R
import com.bettercallsoul.calllog.AnsweredNumber
import com.bettercallsoul.calllog.NumberTag
import com.bettercallsoul.container
import com.bettercallsoul.spam.PhoneNumbers
import com.bettercallsoul.spam.SpamCategory
import com.bettercallsoul.spam.SpamReport
import com.bettercallsoul.telecom.CallerDirectory
import com.bettercallsoul.telecom.EndedCall
import com.bettercallsoul.ui.Card
import com.bettercallsoul.ui.CompactTextField
import com.bettercallsoul.ui.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.bettercallsoul.telecom.CallManager
import com.bettercallsoul.AppContainer
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Outlives the screen, so closing it right after answering doesn't lose the vote. */
private val reportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun EndedScreen(ended: EndedCall, onClose: () -> Unit) {
    val app = LocalContext.current.container
    val number = ended.caller.number
    var asking by remember { mutableStateOf(false) }
    var interacted by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf(false) }

    LaunchedEffect(number) {
        val canReport = !ended.failed && !ended.caller.isContact && PhoneNumbers.toE164(number) != null
        asking = canReport && !app.store.isAnswered(CallerDirectory.keyOf(number))
    }
    LaunchedEffect(asking, interacted, editingTag) {
        if (!asking && !interacted && !editingTag) {
            delay(4000)
            onClose()
        }
    }

    CallBackdrop {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 56.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(if (ended.failed) R.string.call_failed else R.string.call_ended),
                color = Color(0xFFC9CDD2),
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(24.dp))
            Text(ended.caller.title, color = Color.White, fontSize = 36.sp, textAlign = TextAlign.Center)
            ended.failureText?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFFC9CDD2), fontSize = 15.sp, textAlign = TextAlign.Center)
            }
            if (ended.durationSeconds > 0) {
                Spacer(Modifier.height(6.dp))
                Text(formatDuration(ended.durationSeconds), color = Color(0xFFC9CDD2), fontSize = 16.sp)
            }
            if (!ended.caller.isContact) {
                TextButton(onClick = {
                    interacted = true
                    editingTag = true
                }) {
                    Text(stringResource(if (ended.caller.isTagged) R.string.tag_edit else R.string.tag_add), color = Color.White)
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 32.dp)) {
            if (asking) {
                Card {
                    SpamQuestion(number = number, onFinished = {
                        asking = false
                        interacted = false
                    })
                }
                Spacer(Modifier.height(16.dp))
            }
            FilledIconButton(
                onClick = onClose,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White),
                modifier = Modifier.align(Alignment.CenterHorizontally).size(56.dp),
            ) {
                Icon(Icons.Filled.Check, stringResource(R.string.action_done), modifier = Modifier.size(28.dp))
            }
        }
    }

    if (editingTag) {
        TagDialog(number = number, onDismiss = {
            editingTag = false
            interacted = false
        })
    }
}

/**
 * Ends the call and reports the number as spam. Noted as answered first, so the screen after the
 * call doesn't ask about it again; the report then goes out on its own.
 */
internal fun endAsSpam(app: AppContainer, number: String) {
    val e164 = PhoneNumbers.toE164(number) ?: return
    reportScope.launch {
        app.store.markAnswered(AnsweredNumber(CallerDirectory.keyOf(number), System.currentTimeMillis()))
        withContext(Dispatchers.Main) { CallManager.hangUp() }
        app.spam.report(SpamReport(e164, app.deviceId, true, null, null))
    }
}

private enum class Step { Spam, Category, Caller }

/** After a call from an unknown number: was it spam, and if not, who was it? */
@Composable
fun SpamQuestion(number: String, onFinished: () -> Unit) {
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.Spam) }
    var caller by remember { mutableStateOf("") }

    fun finish() {
        scope.launch { app.store.markAnswered(AnsweredNumber(CallerDirectory.keyOf(number), System.currentTimeMillis())) }
        onFinished()
    }

    fun send(isSpam: Boolean, category: SpamCategory? = null, callerName: String? = null) {
        val e164 = PhoneNumbers.toE164(number)
        if (e164 != null) {
            reportScope.launch {
                app.spam.report(SpamReport(e164, app.deviceId, isSpam, category, callerName))
            }
        }
        finish()
    }

    Column(Modifier.fillMaxWidth().padding(10.dp)) {
        when (step) {
            Step.Spam -> {
                Text(stringResource(R.string.spam_question), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(R.string.spam_disclosure),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = ::finish) { Text(stringResource(R.string.action_skip)) }
                    TextButton(onClick = { step = Step.Caller }) { Text(stringResource(R.string.action_no)) }
                    TextButton(onClick = { step = Step.Category }) { Text(stringResource(R.string.action_yes)) }
                }
            }
            Step.Category -> {
                Text(stringResource(R.string.spam_category_question), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpamCategory.entries.forEach { category ->
                        AssistChip(
                            onClick = { send(true, category) },
                            label = { Text(stringResource(labelOf(category))) },
                        )
                    }
                }
            }
            Step.Caller -> {
                Text(stringResource(R.string.caller_question), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                CompactTextField(
                    value = caller,
                    onValueChange = { caller = it },
                    placeholder = stringResource(R.string.caller_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { send(false) }) { Text(stringResource(R.string.action_skip)) }
                    TextButton(onClick = { send(false, callerName = caller) }) { Text(stringResource(R.string.action_send)) }
                }
            }
        }
    }
}

private fun labelOf(category: SpamCategory): Int = when (category) {
    SpamCategory.Telemarketing -> R.string.spam_cat_telemarketing
    SpamCategory.Scam -> R.string.spam_cat_scam
    SpamCategory.Survey -> R.string.spam_cat_survey
    SpamCategory.Other -> R.string.spam_cat_other
}

/** Tags stay on the phone; they are never sent to the server. */
@Composable
fun TagDialog(number: String, onDismiss: () -> Unit) {
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val key = CallerDirectory.keyOf(number)
    var label by remember { mutableStateOf("") }
    var existing by remember { mutableStateOf(false) }

    LaunchedEffect(key) {
        app.store.tag(key)?.let {
            label = it.label
            existing = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing) R.string.tag_edit else R.string.tag_add)) },
        text = {
            CompactTextField(
                value = label,
                onValueChange = { label = it.take(40) },
                placeholder = stringResource(R.string.tag_hint),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().focusOnOpen(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank(),
                onClick = {
                    scope.launch {
                        app.store.saveTag(NumberTag(key, label.trim(), System.currentTimeMillis()))
                        onDismiss()
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            if (existing) {
                TextButton(onClick = {
                    scope.launch {
                        app.store.deleteTag(key)
                        onDismiss()
                    }
                }) { Text(stringResource(R.string.tag_remove)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
