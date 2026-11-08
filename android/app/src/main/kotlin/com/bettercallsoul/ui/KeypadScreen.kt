package com.bettercallsoul.ui

import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.AnnotatedString
import com.bettercallsoul.spam.T9
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material.icons.outlined.Info
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
        hits = if (digits.length >= 2) context.container.contacts.keypadSearch(digits, limit = MAX_MATCHES) else emptyList()
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

        // The matching contacts get the room above the number; more than fit scroll in it.
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            if (hits.isNotEmpty()) {
                Card(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)) {
                    // More matches than fit scroll here instead of pushing the keys down.
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        hits.forEach { hit ->
                            // As in the lists: a swipe right calls, a swipe left writes; a tap puts the
                            // number in the field and the info button opens the contact.
                            SwipeRow(onCall = { onCall(hit.number) }, onMessage = { sendSwipeMessage(context, hit.number) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable { onDigitsChange(hit.number) }
                                        .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Avatar(hit.name, hit.photoUri, 34.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        // What the digits matched is bold: letters of the name, digits of the number, or both.
                                        Text(boldPart(hit.name, T9.nameMatch(digits, hit.name)), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                        Text(boldPart(hit.number, T9.numberMatch(digits, hit.number)), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { onOpen(Route.Contact(hit.contactId)) }) {
                                        Icon(Icons.Outlined.Info, stringResource(R.string.contact_info), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
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
                // On the left, opposite the delete key.
                if (hits.none { sameNumber(it.number, digits) }) {
                    IconButton(
                        onClick = { onOpen(Route.NewContact(digits)) },
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        Icon(Icons.Outlined.PersonAddAlt, stringResource(R.string.action_add_to_contacts), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DeleteKey(onDelete = { edit(field.backspace()) }, modifier = Modifier.align(Alignment.CenterEnd))
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
    // The default-app banner lies over the top of the keypad instead of taking room from the keys;
    // it goes once a digit is typed, and its cross puts it away until the app is opened again.
    if (!isDefaultDialer && digits.isEmpty()) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = TOOLBAR_HEIGHT)) {
            DefaultDialerBanner(onRequestDefault, onClose = onCloseDefault, short = compact)
        }
    }
    }
}

/** [text] with the characters in [part] in bold. */
private fun boldPart(text: String, part: IntRange?): AnnotatedString = buildAnnotatedString {
    append(text)
    if (part != null && part.last < text.length) {
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), part.first, part.last + 1)
    }
}

/** How many matching contacts the keypad lists; more than fit scroll above the number. */
private const val MAX_MATCHES = 30

/** Held this long, the delete key starts repeating; then it speeds up to [REPEAT_FASTEST_MILLIS]. */
private const val REPEAT_AFTER_MILLIS = 400L
private const val REPEAT_FIRST_MILLIS = 110L
private const val REPEAT_FASTEST_MILLIS = 35L
private const val REPEAT_SPEED_UP_MILLIS = 8L

/**
 * The delete key: a tap removes one digit; held, it keeps removing them, faster the longer it is
 * held, as on the system phone app.
 */
@Composable
private fun DeleteKey(onDelete: () -> Unit, modifier: Modifier) {
    val delete by rememberUpdatedState(onDelete)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val description = stringResource(R.string.action_delete_digit)
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent)
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    delete()
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    delete()
                    val repeating = scope.launch {
                        delay(REPEAT_AFTER_MILLIS)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        var wait = REPEAT_FIRST_MILLIS
                        while (isActive) {
                            delete()
                            delay(wait)
                            wait = (wait - REPEAT_SPEED_UP_MILLIS).coerceAtLeast(REPEAT_FASTEST_MILLIS)
                        }
                    }
                    waitForUpOrCancellation()
                    repeating.cancel()
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Below this the keypad tab is short, as on small phones, and its parts get smaller. */
private val COMPACT_HEIGHT = 640.dp
private val TOOLBAR_HEIGHT = 56.dp
private val KEY_HEIGHT = 76.dp
private val MIN_KEY_HEIGHT = 54.dp

/** Kept free above the number on a short screen, for at least one matching contact. */
private val ROOM_ABOVE = 64.dp

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

/** What the number may hold: digits and the + * # keys, nothing pasted besides. */
private const val DIALABLE = "0123456789+*#"

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

/** A number as people write it: digits, + * #, and spaces, brackets, dots or dashes between them. */
private val NUMBER_RUN = Regex("""\+?[0-9*#](?:[0-9*#]|[\s ().\-]+(?=[0-9*#]))*""")

/**
 * Keeps only the number in pasted text: "+90 (532) 410-27-85" becomes "+905324102785", and from
 * "Ali Veli 0532 410 27 85, kapı no 5" only the longest number is taken, without the 5.
 */
private fun TextFieldValue.forDialing(): TextFieldValue {
    if (text.all { it in DIALABLE }) return this
    val clean = NUMBER_RUN.findAll(text)
        .map { run -> run.value.filter { it in DIALABLE } }
        .maxByOrNull { run -> run.count(Char::isDigit) }
        .orEmpty()
    return TextFieldValue(clean, TextRange(clean.length))
}
