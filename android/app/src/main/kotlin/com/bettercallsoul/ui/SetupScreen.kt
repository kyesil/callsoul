package com.bettercallsoul.ui

import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bettercallsoul.R
import com.bettercallsoul.TelLinks
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.platform.LocalContext

/**
 * The steps of the first start, in order. Play's policy for call log access wants the default-app
 * question before any permission prompt, so the role comes first and the permissions only once it
 * is held; the rest follow on the same screen, each one only when still needed and each one skippable.
 */
enum class SetupStep { Role, Permissions, LockCalls, WhatsApp, TelLinks }

/** First start: one [SetupStep] at a time under the welcome. */
@Composable
fun SetupScreen(
    step: SetupStep,
    roleDeclined: Boolean,
    onRequestRole: () -> Unit,
    onOpenDefaultApps: () -> Unit,
    onRequestPermissions: () -> Unit,
    onAccept: (SetupStep) -> Unit,
    onSkip: (SetupStep) -> Unit,
) {
    val later = when (step) {
        SetupStep.Role, SetupStep.Permissions -> null
        SetupStep.LockCalls -> Triple(R.string.lock_calls_title, R.string.lock_calls_prompt, R.string.lock_calls_action)
        SetupStep.WhatsApp -> Triple(R.string.settings_whatsapp_title, R.string.setup_whatsapp_body, R.string.lock_calls_action)
        SetupStep.TelLinks -> Triple(R.string.tel_links_title, R.string.tel_links_body, R.string.tel_links_action)
    }
    if (later != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.setup_title),
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(later.first),
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(later.second),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onAccept(step) }) { Text(stringResource(later.third)) }
            TextButton(onClick = { onSkip(step) }) { Text(stringResource(R.string.action_skip)) }
        }
        return
    }
    val isDefaultDialer = step == SetupStep.Permissions
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.setup_title),
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(if (isDefaultDialer) R.string.setup_body else R.string.setup_role_body),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (isDefaultDialer) {
            Button(onClick = onRequestPermissions) { Text(stringResource(R.string.setup_permissions)) }
        } else {
            Button(onClick = onRequestRole) { Text(stringResource(R.string.setup_default_action)) }
            // Turned down twice, the system stops asking; its own page still can.
            if (roleDeclined) {
                TextButton(onClick = onOpenDefaultApps) { Text(stringResource(R.string.setup_role_settings)) }
            }
        }
    }
}

/** Asked once after the permissions: without the role, calls don't reach this app's screens. */
@Composable
fun DefaultPhonePrompt(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setup_default_title)) },
        text = { Text(stringResource(R.string.setup_default_body)) },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.setup_default_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_skip)) } },
    )
}

/**
 * After the setup, a permission that went missing while its feature is still wanted, shown on the
 * home screen in the place of the default-app banner, one at a time in this order.
 */
enum class Suggestion { LockCalls, WhatsApp, TelLinks }

/** The banner for a [Suggestion]; its cross gives the feature up, and Settings keeps the way back. */
@Composable
fun SuggestionBanner(suggestion: Suggestion, onAccept: () -> Unit, onClose: () -> Unit) {
    when (suggestion) {
        Suggestion.LockCalls -> SetupBanner(
            R.string.lock_calls_title, stringResource(R.string.lock_calls_prompt), R.string.lock_calls_action, onAccept, onClose,
        )
        Suggestion.WhatsApp -> SetupBanner(
            R.string.settings_whatsapp_title, stringResource(R.string.whatsapp_calls_prompt), R.string.lock_calls_action, onAccept, onClose,
        )
        Suggestion.TelLinks -> {
            // Asking again, or gone to another app, whose own page is the only place to undo that.
            val context = LocalContext.current
            // Read again on coming back: from that page, the holder has likely changed.
            fun read() = (TelLinks.holder(context) as? TelLinks.Holder.Other)?.let { TelLinks.label(context, it.packageName) }
            var other by remember { mutableStateOf(read()) }
            LifecycleResumeEffect(Unit) {
                other = read()
                onPauseOrDispose { }
            }
            val app = other
            if (app == null) {
                SetupBanner(R.string.tel_links_title, stringResource(R.string.tel_links_lost), R.string.tel_links_action, onAccept, onClose)
            } else {
                SetupBanner(R.string.tel_links_title, stringResource(R.string.tel_links_lost_other, app), R.string.settings_whatsapp_late_open, onAccept, onClose)
            }
        }
    }
}
