package com.bettercallsoul.ui

import android.content.Context
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.Icons
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bettercallsoul.calllog.CallEntry
import androidx.core.net.toUri
import com.bettercallsoul.spam.PhoneNumbers
import com.bettercallsoul.container
import com.bettercallsoul.SwipeMessage
import android.widget.Toast
import android.content.ActivityNotFoundException
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.bettercallsoul.AppLook
import com.bettercallsoul.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


private val AvatarColors = listOf(
    Color(0xFF5B8DEF), Color(0xFF3FB68B), Color(0xFFE08A3C), Color(0xFFB36BD4),
    Color(0xFFE0637C), Color(0xFF3AA6B9),
)

/** The colour behind a name's initial, the same wherever it shows. */
fun avatarColor(name: String): Color = AvatarColors[(name.hashCode() and Int.MAX_VALUE) % AvatarColors.size]

@Composable
fun Avatar(name: String, photoUri: String?, size: Dp, modifier: Modifier = Modifier) {
    val initial = name.firstOrNull { it.isLetter() }?.uppercase()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColor(name)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial ?: "#", color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Medium)
        if (photoUri != null) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** Rounded group that list rows sit in, inset 10dp from the screen edge. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

/** The large centred title that scrolls away above each list. */
@Composable
fun LargeTitle(title: String, subtitle: String?, height: Dp = 110.dp) {
    Column(
        modifier = Modifier.fillMaxWidth().height(height),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) {
            Text(subtitle, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ToolbarRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 4.dp)
            .height(44.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun ToolbarIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.stretchy()) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onBackground)
    }
}

/** An outlined field about half the height of Material's own. */
@OptIn(ExperimentalMaterial3Api::class)
/**
 * For the text field of a dialog: focuses it and brings up the keyboard as soon as the dialog
 * opens, so typing can start straight away.
 */
@Composable
fun Modifier.focusOnOpen(): Modifier {
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        // The dialog window takes focus a moment after it is shown.
        delay(80)
        runCatching { requester.requestFocus() }
        keyboard?.show()
    }
    return focusRequester(requester)
}

@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        // The label sits on the border, so it needs room above the box.
        modifier = modifier.padding(top = if (label != null) 6.dp else 0.dp),
        textStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interaction,
        decorationBox = { field ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = field,
                enabled = true,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interaction,
                label = label?.let { { Text(it) } },
                placeholder = placeholder?.let { { Text(it, fontSize = 15.sp) } },
                colors = colors,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = false,
                        interactionSource = interaction,
                        colors = colors,
                    )
                },
            )
        },
    )
}

fun dayHeader(context: Context, millis: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (day) {
        today -> context.getString(R.string.today)
        today.minusDays(1) -> context.getString(R.string.yesterday)
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(AppLook.formatLocale(context)))
    }
}

fun timeOfDay(context: Context, millis: Long): String =
    DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)

fun formatDuration(seconds: Long): String = DateUtils.formatElapsedTime(seconds)

/** How long a call lasted, with a small timer icon, for the call lists: "3:04", "1:02:10". */
@Composable
fun CallLength(seconds: Long, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    if (seconds <= 0) return
    val text = formatDuration(seconds).let { if (it.length == 5 && it.startsWith('0')) it.substring(1) else it }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Timer,
            contentDescription = stringResource(R.string.call_length),
            tint = color.copy(alpha = 0.7f),
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(text, fontSize = 13.sp, color = color)
    }
}

/** How long an unanswered call rang, in seconds and in red: "12 sn". */
@Composable
fun RingLength(seconds: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.NotificationsActive,
            contentDescription = stringResource(R.string.ring_length),
            tint = EndRed.copy(alpha = 0.7f),
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(stringResource(R.string.ring_seconds, seconds), fontSize = 13.sp, color = EndRed)
    }
}

/** The right end of a call's row: when it was, and under that how long it lasted or rang. */
@Composable
fun CallTimes(entry: CallEntry, time: String = timeOfDay(LocalContext.current, entry.date)) {
    Column(horizontalAlignment = Alignment.End) {
        Text(time, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val ring = entry.ringSeconds
        if (ring != null) RingLength(ring) else CallLength(entry.durationSeconds)
    }
}

fun sendSms(context: Context, number: String) {
    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))
}

/**
 * A left swipe on a row: the chat in WhatsApp or the SMS app, as chosen in Settings. Without a
 * WhatsApp to open, or a number it can use, it says so and opens SMS instead.
 */
fun sendSwipeMessage(context: Context, number: String) {
    if (context.container.swipeMessage == SwipeMessage.Sms) {
        sendSms(context, number)
    } else if (!openWhatsAppChat(context, number)) {
        Toast.makeText(context, R.string.whatsapp_missing, Toast.LENGTH_SHORT).show()
        sendSms(context, number)
    }
}

/**
 * Calls back the way a call came. A WhatsApp call goes back through WhatsApp when the contact has its
 * call action, else to the chat, where the call button is; a normal call is left to [onCall].
 */
suspend fun callBack(context: Context, entry: CallEntry, onCall: (String) -> Unit) {
    val kind = entry.whatsApp
    if (kind == null) {
        onCall(entry.number)
        return
    }
    val actions = context.container.contacts.whatsAppActions(entry.number)
    val action = actions.firstOrNull { it.kind == kind } ?: actions.firstOrNull()
    when {
        action != null -> openWhatsApp(context, action)
        !openWhatsAppChat(context, entry.number) -> onCall(entry.number)
    }
}

private val WHATSAPP_APPS = listOf("com.whatsapp", "com.whatsapp.w4b")

internal fun openWhatsAppChat(context: Context, number: String): Boolean {
    val international = PhoneNumbers.toE164(number)?.removePrefix("+") ?: return false
    val chat = Intent(Intent.ACTION_VIEW, "https://wa.me/$international".toUri())
    return WHATSAPP_APPS.any { app ->
        try {
            context.startActivity(chat.setPackage(app))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
