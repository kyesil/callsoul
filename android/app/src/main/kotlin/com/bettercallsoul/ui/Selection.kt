package com.bettercallsoul.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bettercallsoul.R

/**
 * The toolbar while rows are picked with a long press: leave, how many, pick all, delete. Back
 * leaves the selection too.
 */
@Composable
fun SelectionBar(count: Int, onClear: () -> Unit, onSelectAll: () -> Unit, onDelete: () -> Unit) {
    BackHandler(onBack = onClear)
    ToolbarRow {
        ToolbarIcon(Icons.Filled.Close, stringResource(R.string.selection_clear), onClear)
        Text(
            pluralStringResource(R.plurals.selected_count, count, count),
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        ToolbarIcon(Icons.Filled.SelectAll, stringResource(R.string.selection_all), onSelectAll)
        ToolbarIcon(Icons.Outlined.Delete, stringResource(R.string.action_delete), onDelete)
    }
}

/** The home and keypad toolbar, the same on both: add a contact, settings. */
@Composable
fun RowScope.MainToolbarActions(onOpen: (Route) -> Unit) {
    ToolbarIcon(Icons.Outlined.PersonAdd, stringResource(R.string.action_add_contact)) { onOpen(Route.NewContact(null)) }
    ToolbarIcon(Icons.Outlined.Settings, stringResource(R.string.action_settings)) { onOpen(Route.Settings) }
}

/** In place of a row's photo while it is picked. */
@Composable
fun SelectedMark(size: Dp) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** A yes/no question, headed by the action's icon. */
@Composable
fun ConfirmDialog(
    message: String,
    icon: ImageVector,
    action: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) = ConfirmDialog(AnnotatedString(message), icon, action, onConfirm, onDismiss, destructive)

/** The same, with parts of the question set apart (in bold, say). */
@Composable
fun ConfirmDialog(
    message: AnnotatedString,
    icon: ImageVector,
    action: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null, tint = if (destructive) EndRed else MaterialTheme.colorScheme.primary) },
        text = { Text(message, fontSize = 16.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(action, color = if (destructive) EndRed else MaterialTheme.colorScheme.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
