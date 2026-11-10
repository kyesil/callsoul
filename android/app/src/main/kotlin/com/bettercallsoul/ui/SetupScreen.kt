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

/**
 * First start. Play's policy for call log access wants the default-app question before any
 * permission prompt, so the role comes first and the permissions only once it is held.
 */
@Composable
fun SetupScreen(
    isDefaultDialer: Boolean,
    roleDeclined: Boolean,
    onRequestRole: () -> Unit,
    onOpenDefaultApps: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
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

/** After the phone role: the system's own question for links, explained before it shows. */
@Composable
fun TelLinksPrompt(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tel_links_title)) },
        text = { Text(stringResource(R.string.tel_links_body)) },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.tel_links_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_skip)) } },
    )
}
