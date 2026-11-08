package com.bettercallsoul.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.gestures.detectTapGestures
import android.content.ClipboardManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.awaitCancellation
import com.bettercallsoul.R
import com.bettercallsoul.calllog.SearchHit
import com.bettercallsoul.container

private data class Key(val digit: Char, val letters: String)

private val Keys = listOf(
    listOf(Key('1', ""), Key('2', "ABC"), Key('3', "DEF")),
    listOf(Key('4', "GHI"), Key('5', "JKL"), Key('6', "MNO")),
    listOf(Key('7', "PQRS"), Key('8', "TUV"), Key('9', "WXYZ")),
    listOf(Key('*', ""), Key('0', "+"), Key('#', "")),
)

@Composable
fun KeypadScreen(
    digits: String,
    onDigitsChange: (String) -> Unit,
    onCall: (String) -> Unit,
    onOpen: (Route) -> Unit,
    isDefaultDialer: Boolean,
    onRequestDefault: () -> Unit,
    /** Puts the default-app banner away until the app is opened again, as on Home. */
    onCloseDefault: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var hits by remember { mutableStateOf(emptyList<SearchHit>()) }

    // The number keeps its own cursor and selection, so a digit goes where the cursor is and a copied
    // number can be pasted; [digits] stays the plain text the rest of the app sees.
    var field by remember { mutableStateOf(TextFieldValue(digits, TextRange(digits.length))) }
    LaunchedEffect(digits) {
        if (digits != field.text) field = TextFieldValue(digits, TextRange(digits.length))
    }
    fun edit(value: TextFieldValue) {
        field = value
        if (value.text != digits) onDigitsChange(value.text)
    }

    LaunchedEffect(digits) {
        // One digit matches half the address book, so the list starts at two.
        hits = if (digits.length >= 2) context.container.contacts.keypadSearch(digits) else emptyList()
    }

    // On a short screen the keys get smaller, so the banner or a few matching contacts still fit
    // above the number. The keys never move while contacts come and go.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val compact = maxHeight < COMPACT_HEIGHT
    val numberHeight = if (compact) 86.dp else 93.dp
    val callHeight = if (compact) 80.dp else 95.dp
    val keyHeight = ((maxHeight - TOOLBAR_HEIGHT - numberHeight - callHeight - ROOM_ABOVE) / 4).coerceIn(MIN_KEY_HEIGHT, KEY_HEIGHT)
    Column(Modifier.fillMaxSize()) {
        ToolbarRow {
            MainToolbarActions(onOpen)
        }

        // The banner and the matching contacts share the room above the number. While digits are
        // typed the banner steps aside for them; more matches than fit scroll in that room.
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            if (!isDefaultDialer && digits.isEmpty()) {
                Box(Modifier.align(Alignment.TopCenter)) { DefaultDialerBanner(onRequestDefault, onClose = onCloseDefault, short = compact) }
            }
            if (hits.isNotEmpty()) {
                Card(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)) {
                    // More matches than fit scroll here instead of pushing the keys down.
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        hits.forEach { hit ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDigitsChange(hit.number) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(hit.name, hit.photoUri, 34.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(hit.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                    Text(hit.number, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onCall(hit.number) }) {
                                    Icon(Icons.Filled.Call, stringResource(R.string.action_call), tint = CallGreen)
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(numberHeight).padding(horizontal = 27.dp),
            contentAlignment = Alignment.Center,
        ) {
            NumberField(field, onValueChange = { edit(it.forDialing()) })
            if (digits.isNotEmpty()) {
                // A number that is already someone's needs no "add to contacts".
                if (hits.none { sameNumber(it.number, digits) }) {
                    TextButton(
                        onClick = { onOpen(Route.NewContact(digits)) },
                        modifier = Modifier.align(Alignment.TopCenter).height(30.dp),
                    ) {
                        Text(stringResource(R.string.action_add_to_contacts), fontSize = 13.sp)
                    }
                }
                IconButton(
                    onClick = { edit(field.backspace()) },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Backspace,
                        stringResource(R.string.action_delete_digit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Dialpad(
            onDigit = { edit(field.insert(it.toString())) },
            onPlus = { edit(field.insert("+")) },
            keyHeight = keyHeight,
        )

        Box(Modifier.fillMaxWidth().height(callHeight), contentAlignment = Alignment.Center) {
            val description = stringResource(R.string.action_call)
            Box(
                modifier = Modifier
                    .size(65.dp)
                    .stretchy()
                    .clip(CircleShape)
                    .background(CallGreen)
                    .semantics { contentDescription = description }
                    .clickable(enabled = digits.isNotEmpty()) { onCall(digits) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
    }
}

/** Below this the keypad tab is short, as on small phones, and its parts get smaller. */
private val COMPACT_HEIGHT = 640.dp
private val TOOLBAR_HEIGHT = 56.dp
private val KEY_HEIGHT = 76.dp
private val MIN_KEY_HEIGHT = 54.dp

/** Kept free above the number for the banner or about two matching contacts. */
private val ROOM_ABOVE = 148.dp

@Composable
fun Dialpad(onDigit: (Char) -> Unit, onPlus: (() -> Unit)? = null, tint: Color = Color.Unspecified, keyHeight: Dp = KEY_HEIGHT) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 27.dp)) {
        Keys.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { key ->
                    DialKey(
                        key = key,
                        onClick = { onDigit(key.digit) },
                        onLongClick = if (key.digit == '0') onPlus else null,
                        tint = tint,
                        height = keyHeight,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(key: Key, onClick: () -> Unit, onLongClick: (() -> Unit)?, tint: Color, height: Dp, modifier: Modifier) {
    val digitColor = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onBackground else tint
    // Smaller keys get smaller digits, so the letters still fit under them.
    val small = height < 66.dp
    Column(
        modifier = modifier
            .height(height)
            .stretchy()
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(key.digit.toString(), fontSize = if (small) 30.sp else 38.sp, lineHeight = if (small) 33.sp else 42.sp, color = digitColor)
        Text(key.letters, fontSize = if (small) 11.sp else 13.sp, lineHeight = if (small) 13.sp else 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Asks to be made the default phone app; with [onClose] it has a cross that puts it away. [short]
 * is one line, the title and the button, for a small screen.
 */
@Composable
fun DefaultDialerBanner(onRequestDefault: () -> Unit, onClose: (() -> Unit)? = null, short: Boolean = false) {
    if (short) {
        Card(Modifier.padding(top = 4.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.setup_default_title),
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRequestDefault) { Text(stringResource(R.string.setup_default_action)) }
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_close), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        return
    }
    Card(Modifier.padding(top = 4.dp)) {
        Box {
            Column(Modifier.padding(start = 20.dp, end = if (onClose != null) 44.dp else 20.dp, top = 14.dp, bottom = 14.dp)) {
                Text(stringResource(R.string.setup_default_title), color = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(R.string.setup_default_body),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRequestDefault, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.setup_default_action))
                }
            }
            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_close), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}


/** Same phone number however it is written: compares the last ten digits. */
private fun sameNumber(a: String, b: String): Boolean {
    val x = a.filter(Char::isDigit).takeLast(10)
    val y = b.filter(Char::isDigit).takeLast(10)
    return x.length >= 7 && x == y
}


/**
 * The typed number: editable, with a cursor, selection and the copy and paste menu, but without the
 * soft keyboard, since the dial pad is the keyboard. The system menu only appears over text, so an
 * empty field offers its own Paste on a long press.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NumberField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    val context = LocalContext.current
    val color = MaterialTheme.colorScheme.onBackground
    var menu by remember { mutableStateOf(false) }
    // Whether there is text to paste; kept up to date while the keypad is open. Looking doesn't read it.
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    var canPaste by remember { mutableStateOf(clipboard?.hasPrimaryClip() == true) }
    DisposableEffect(clipboard) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener { canPaste = clipboard?.hasPrimaryClip() == true }
        clipboard?.addPrimaryClipChangedListener(listener)
        onDispose { clipboard?.removePrimaryClipChangedListener(listener) }
    }
    fun paste() {
        val clip = clipboard?.primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        if (!text.isNullOrEmpty()) onValueChange(TextFieldValue(text, TextRange(text.length)))
    }
    // The whole number row answers a long press, not just the thin line of an empty field.
    Box(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 48.dp), contentAlignment = Alignment.Center) {
        InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = if (value.text.length > 13) 28.sp else 36.sp,
                    textAlign = TextAlign.Center,
                    color = color,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.text.isEmpty()) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { menu = true }) },
            )
            // And a plain button while the field is empty and something is copied.
            if (canPaste) {
                AssistChip(
                    onClick = ::paste,
                    label = { Text(stringResource(R.string.action_paste)) },
                    leadingIcon = { Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_paste)) },
                onClick = {
                    menu = false
                    paste()
                },
            )
        }
    }
}

private const val DIALABLE = "0123456789+*#,;"

private fun TextFieldValue.insert(text: String): TextFieldValue {
    val start = selection.min
    val result = this.text.replaceRange(start, selection.max, text)
    return TextFieldValue(result, TextRange(start + text.length))
}

private fun TextFieldValue.backspace(): TextFieldValue {
    val start = selection.min
    return when {
        !selection.collapsed -> TextFieldValue(text.removeRange(start, selection.max), TextRange(start))
        start > 0 -> TextFieldValue(text.removeRange(start - 1, start), TextRange(start - 1))
        else -> this
    }
}

/** Keeps only what a phone can dial, so a pasted "+90 (532) 410-27-85" becomes "+905324102785". */
private fun TextFieldValue.forDialing(): TextFieldValue {
    if (text.all { it in DIALABLE }) return this
    val clean = text.filter { it in DIALABLE }
    return TextFieldValue(clean, TextRange(clean.length))
}
