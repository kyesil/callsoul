package com.bettercallsoul.call

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bettercallsoul.ui.focusOnOpen
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bettercallsoul.R
import com.bettercallsoul.container
import com.bettercallsoul.telecom.CallManager

/**
 * Under the answer and decline buttons: turn the call down with a text message. The phone's own
 * telephony sends it, so the app needs no SMS permission.
 */
@Composable
fun ReplyWithMessage(modifier: Modifier = Modifier) {
    val app = LocalContext.current.container
    var choosing by remember { mutableStateOf(false) }
    var writing by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.12f),
        modifier = modifier.clickable { choosing = true },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.reply_with_sms), color = Color.White, fontSize = 15.sp)
        }
    }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.reply_with_sms)) },
            text = {
                Column {
                    app.quickReplies.forEach { reply ->
                        Text(
                            reply,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    choosing = false
                                    CallManager.rejectWithMessage(reply)
                                }
                                .padding(vertical = 12.dp),
                        )
                        HorizontalDivider()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                choosing = false
                                writing = true
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.reply_custom), fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (writing) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { writing = false },
            title = { Text(stringResource(R.string.reply_custom)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.reply_hint)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().focusOnOpen(),
                )
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    writing = false
                    CallManager.rejectWithMessage(text.trim())
                }) { Text(stringResource(R.string.reply_send)) }
            },
            dismissButton = { TextButton(onClick = { writing = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
