package com.bettercallsoul.call

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.PersonAdd
import com.bettercallsoul.BlockedList
import com.bettercallsoul.MainActivity
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
    val context = LocalContext.current
    val app = context.container
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
            if (!ended.caller.isContact && number.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EndedAction(
                        Icons.AutoMirrored.Outlined.Label,
                        stringResource(if (ended.caller.isTagged) R.string.tag_edit else R.string.tag_add),
                    ) {
                        interacted = true
                        editingTag = true
                    }
                    EndedAction(Icons.Outlined.PersonAdd, stringResource(R.string.action_add_to_contacts)) {
                        openNewContact(context, number, onClose)
                    }
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

@Composable
private fun EndedAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White)
    }
}

/**
 * The new-contact page with the number filled in. On a locked phone it asks for the unlock first,
 * since the app's own pages don't show over the lock screen.
 */
private fun openNewContact(context: Context, number: String, onClose: () -> Unit) {
    fun open() {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_NEW_CONTACT)
                .putExtra(MainActivity.EXTRA_NUMBER, number)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        onClose()
    }
    val keyguard = context.getSystemService(KeyguardManager::class.java)
    val activity = context.findActivity()
    if (activity == null || !keyguard.isKeyguardLocked) {
        open()
        return
    }
    keyguard.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
        override fun onDismissSucceeded() = open()
    })
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** With the setting on (it is by default), a number reported as spam is blocked too, so it can't ring again. */
private suspend fun blockAsSpam(context: Context, number: String) {
    if (context.container.blockReported && BlockedList.block(context, number)) {
        withContext(Dispatchers.Main) { Toast.makeText(context, R.string.spam_blocked, Toast.LENGTH_SHORT).show() }
    }
}

/**
 * Ends the call and reports the number as spam. Noted as answered first, so the screen after the
 * call doesn't ask about it again; the report then goes out on its own.
 */
internal fun endAsSpam(context: Context, app: AppContainer, number: String) {
    val e164 = PhoneNumbers.toE164(number) ?: return
    val appContext = context.applicationContext
    reportScope.launch {
        app.store.markAnswered(AnsweredNumber(CallerDirectory.keyOf(number), System.currentTimeMillis()))
        withContext(Dispatchers.Main) { CallManager.hangUp() }
        blockAsSpam(appContext, number)
        app.spam.report(SpamReport(e164, app.deviceId, true))
    }
}

private enum class Step { Spam, Caller }

/**
 * After a call from an unknown number, one question: not spam, or which kind of spam. Not spam then
 * asks who it was. The spam answer goes to
 * the server; who it was stays on the phone as the number's label.
 */
@Composable
fun SpamQuestion(number: String, onFinished: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val app = context.container
    var step by remember { mutableStateOf(Step.Spam) }
    var caller by remember { mutableStateOf("") }

    fun finish() {
        reportScope.launch { app.store.markAnswered(AnsweredNumber(CallerDirectory.keyOf(number), System.currentTimeMillis())) }
        onFinished()
    }

    fun send(isSpam: Boolean, category: SpamCategory? = null, label: String? = null) {
        val e164 = PhoneNumbers.toE164(number)
        val name = label?.trim()?.take(40)?.takeIf { it.isNotEmpty() }
        reportScope.launch {
            if (name != null) app.store.saveTag(NumberTag(CallerDirectory.keyOf(number), name, System.currentTimeMillis()))
            // Blocked before the card closes, so a page showing the number reads it as blocked.
            if (isSpam) blockAsSpam(context, number)
            withContext(Dispatchers.Main) { finish() }
            if (e164 != null) app.spam.report(SpamReport(e164, app.deviceId, isSpam, category))
        }
    }

    Column(Modifier.fillMaxWidth().padding(10.dp)) {
        when (step) {
            Step.Spam -> {
                // Says "block" only when a spam answer will block the number.
                Text(
                    stringResource(if (app.blockReported) R.string.spam_question_block else R.string.spam_question),
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { step = Step.Caller }, label = { Text(stringResource(R.string.spam_not)) })
                    SpamCategory.entries.forEach { category ->
                        AssistChip(
                            onClick = { send(true, category) },
                            label = { Text(stringResource(labelOf(category))) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = ::finish) { Text(stringResource(R.string.action_skip)) }
                }
            }
            Step.Caller -> {
                Text(stringResource(R.string.caller_question), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.caller_label_note), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                CompactTextField(
                    value = caller,
                    onValueChange = { caller = it.take(40) },
                    placeholder = stringResource(R.string.caller_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { send(false) }) { Text(stringResource(R.string.action_skip)) }
                    TextButton(onClick = { send(false, label = caller) }, enabled = caller.isNotBlank()) { Text(stringResource(R.string.action_save)) }
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
