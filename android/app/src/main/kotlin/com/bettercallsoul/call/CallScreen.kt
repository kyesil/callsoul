package com.bettercallsoul.call

import androidx.compose.material.icons.automirrored.outlined.Label
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.bettercallsoul.ui.LocalMotion
import com.bettercallsoul.ui.liquidGlass
import com.bettercallsoul.ui.motionSpec
import com.bettercallsoul.ui.stretchy
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import android.content.Intent
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bettercallsoul.spam.PhoneNumbers
import androidx.compose.material.icons.outlined.Report
import android.widget.Toast
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.Canvas
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettercallsoul.R
import com.bettercallsoul.container
import com.bettercallsoul.spam.Verdict
import com.bettercallsoul.telecom.AudioUiState
import com.bettercallsoul.telecom.CallManager
import com.bettercallsoul.telecom.CallPhase
import com.bettercallsoul.telecom.CallUiState
import com.bettercallsoul.telecom.CallerInfo
import com.bettercallsoul.telecom.HeldCall
import com.bettercallsoul.ui.Avatar
import com.bettercallsoul.ui.CallGreen
import com.bettercallsoul.ui.Dialpad
import com.bettercallsoul.ui.EndRed
import com.bettercallsoul.ui.SpamOrange
import com.bettercallsoul.ui.formatDuration
import com.bettercallsoul.ui.timeOfDay
import kotlinx.coroutines.delay

private val Backdrop = Brush.linearGradient(listOf(Color(0xFF2B3036), Color(0xFF14181C), Color(0xFF3A4046)))
private val BackdropTone = Color(0xFF14181C)
private val Soft = Color(0xFFC9CDD2)

@Composable
fun CallBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Backdrop)
            .statusBarsPadding()
            .navigationBarsPadding(),
        content = content,
    )
}

@Composable
fun CallScreen(state: CallUiState) {
    // Answering grows the answer button over the screen; it lives here, above the phases, so the
    // switch from ringing to talking happens under it.
    var reveal by remember { mutableStateOf<Reveal?>(null) }
    val motion = LocalMotion.current
    CallBackdrop {
        when (state.phase) {
            CallPhase.SelectingAccount -> AccountPicker()
            CallPhase.Incoming -> IncomingCall(state.caller, onAnswered = { center -> if (motion) reveal = Reveal(center) })
            else -> OngoingCall(state)
        }
        reveal?.let { RevealOverlay(it, onFinished = { reveal = null }) }
    }
}

/** Where the answer button was let go, in root coordinates. */
private data class Reveal(val center: Offset)

/** A green circle that swells to cover the screen, turning into the call screen's own colour as it goes. */
@Composable
private fun RevealOverlay(reveal: Reveal, onFinished: () -> Unit) {
    val grow = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(reveal) {
        grow.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        fade.animateTo(0f, tween(320))
        onFinished()
    }
    Canvas(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        val center = reveal.center - origin
        val far = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
            .maxOf { (it - center).getDistance() }
        val start = 34.dp.toPx()
        val color = lerp(CallGreen, BackdropTone, grow.value)
        drawCircle(color.copy(alpha = fade.value), radius = start + (far - start) * grow.value, center = center)
    }
}

@Composable
private fun CallerHeader(status: String, caller: CallerInfo, showPhoto: Boolean) {
    // The caller rises gently into place when the screen opens.
    val motion = LocalMotion.current
    val appear = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, start = 24.dp, end = 24.dp)
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 40.dp.toPx()
                scaleX = 0.92f + 0.08f * appear.value
                scaleY = 0.92f + 0.08f * appear.value
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(status, color = Soft, fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
        if (showPhoto && caller.photoUri != null) {
            Avatar(caller.title, caller.photoUri, 120.dp)
            Spacer(Modifier.height(16.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                caller.title,
                color = Color.White,
                fontSize = if (caller.title.length > 14) 32.sp else 40.sp,
                lineHeight = 44.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (caller.isTagged) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Outlined.Label,
                    contentDescription = stringResource(R.string.tag),
                    tint = Soft,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        caller.subtitle?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Soft, fontSize = 16.sp, textAlign = TextAlign.Center)
        }
        if (caller.verdict == Verdict.Spam) SpamBadge(caller)
    }
}

@Composable
fun SpamBadge(caller: CallerInfo) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Warning, contentDescription = null, tint = SpamOrange, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "${stringResource(R.string.spam_warning)} · ${pluralStringResource(R.plurals.spam_reports, caller.spamVotes, caller.spamVotes)}",
            color = SpamOrange,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun IncomingCall(caller: CallerInfo, onAnswered: (Offset) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        CallerHeader(stringResource(R.string.call_incoming), caller, showPhoto = true)
        CallerExtras(caller, Modifier.weight(1f).fillMaxWidth())
        caller.lastCallAt?.let { last ->
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.call_last), color = Soft, fontSize = 14.sp)
                Text(timeOfDay(context, last), color = Soft, fontSize = 14.sp)
            }
        }
        var hint by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(hint) {
            if (hint != null) {
                delay(4000)
                hint = null
            }
        }
        Text(
            hint?.let { stringResource(it) }.orEmpty(),
            color = Soft,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SwipeCallButton(Icons.Filled.Call, R.string.call_answer, CallGreen, pulse = true, onTap = { hint = R.string.swipe_to_answer }) { center ->
                onAnswered(center)
                CallManager.answer()
            }
            SwipeCallButton(Icons.Filled.CallEnd, R.string.call_decline, EndRed, onTap = { hint = R.string.swipe_to_decline }) {
                CallManager.hangUp()
            }
        }
        ReplyWithMessage(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 28.dp))
    }
}

/**
 * A button that has to be dragged out of its ring, in any direction, to fire. A tap only shows a
 * hint. Screen readers still get a plain click action, since dragging is hard without sight.
 */
@Composable
private fun SwipeCallButton(
    icon: ImageVector,
    label: Int,
    color: Color,
    pulse: Boolean = false,
    onTap: () -> Unit,
    onTrigger: (center: Offset) -> Unit,
) {
    val description = stringResource(label)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val reach = with(LocalDensity.current) { 80.dp.toPx() }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var fired by remember { mutableStateOf(false) }
    // The button's middle in root coordinates, for the answer's reveal to start from.
    var center by remember { mutableStateOf(Offset.Zero) }
    // A fired button stays where it was let go. Should the call still be ringing a moment later
    // (it didn't go through), the button goes home and can be tried again.
    LaunchedEffect(fired) {
        if (fired) {
            delay(2500)
            fired = false
            offset.animateTo(Offset.Zero, spring())
        }
    }
    val progress = (offset.value.getDistance() / reach).coerceIn(0f, 1f)
    val armed = progress >= 1f
    LaunchedEffect(armed) { if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }

    // While it rings, the answer button breathes and sends out a fading ring, until a finger takes it.
    val breathing = pulse && LocalMotion.current && offset.value == Offset.Zero
    val beat = rememberInfiniteTransition(label = "answer")
    val breath by beat.animateFloat(1f, 1.1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val wave by beat.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearOutSlowInEasing)), label = "wave")

    Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
        if (breathing) {
            Box(
                Modifier
                    .requiredSize(68.dp + 70.dp * wave)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.35f * (1f - wave))),
            )
        }
        // The ring grows with the drag and fills in once letting go would fire.
        Box(
            Modifier
                .requiredSize(68.dp + 180.dp * progress)
                .clip(CircleShape)
                .background(color.copy(alpha = if (armed) 0.35f else 0.15f * progress)),
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
                .onGloballyPositioned { center = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f) }
                .graphicsLayer {
                    val scale = if (breathing) breath else 1f + 0.08f * progress
                    scaleX = scale
                    scaleY = scale
                }
                .size(68.dp)
                .clip(CircleShape)
                .background(Color.White)
                .semantics {
                    contentDescription = description
                    onClick(description) {
                        onTrigger(center)
                        true
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            if (!fired && offset.value.getDistance() >= reach) {
                                fired = true
                                onTrigger(center)
                            } else if (!fired) {
                                // Not far enough: back into its ring.
                                scope.launch { offset.animateTo(Offset.Zero, spring()) }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(Offset.Zero, spring()) } },
                    ) { change, drag ->
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + drag) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
        }
    }
}

/**
 * The rest of a saved contact's card (other numbers, e-mail, work, address, note), faint and
 * scrollable in the free space under the name. Empty for numbers outside the contacts.
 */
@Composable
private fun CallerExtras(caller: CallerInfo, modifier: Modifier) {
    val context = LocalContext.current
    val contactId = caller.contactId
    var lines by remember(contactId) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(contactId, caller.number) {
        if (contactId == null) return@LaunchedEffect
        val detail = context.container.contacts.detail(contactId) ?: return@LaunchedEffect
        val calling = caller.number.filter(Char::isDigit).takeLast(10)
        lines = buildList {
            detail.phones
                .filter { it.number.filter(Char::isDigit).takeLast(10) != calling }
                .forEach { add(listOfNotNull(it.label, it.number).joinToString(" · ")) }
            addAll(detail.emails.map { it.value })
            // Company and title already sit under the name; only what they leave out goes here.
            val work = listOfNotNull(detail.company, detail.jobTitle, detail.department).joinToString(" · ")
            val shown = caller.subtitle
            val rest = if (shown != null && work.startsWith(shown)) work.removePrefix(shown).removePrefix(" · ") else work
            rest.ifEmpty { null }?.let(::add)
            addAll(detail.addresses.map { it.value })
            detail.note?.let(::add)
        }.filter { it != caller.subtitle }.distinct()
    }
    Box(modifier) {
        if (lines.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdges()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                lines.forEach { Text(it, color = Soft.copy(alpha = 0.55f), fontSize = 15.sp, textAlign = TextAlign.Center) }
            }
        }
    }
}

/** Fades the top and bottom of a scrolling area, so cut-off lines dissolve instead of ending sharply. */
private fun Modifier.fadingEdges() = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 0.12f to Color.Black, 0.88f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun CircleButton(icon: ImageVector, label: Int, tint: Color, background: Color, onClick: () -> Unit) {
    val description = stringResource(label)
    Box(
        modifier = Modifier
            .size(68.dp)
            .stretchy()
            .clip(CircleShape)
            .background(background)
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
    }
}

/** While talking to a number outside the contacts: hang up and report it as spam in one go. */
@Composable
private fun EndAsSpam(number: String, modifier: Modifier) {
    val context = LocalContext.current
    val reported = stringResource(R.string.spam_reported)
    Surface(
        shape = RoundedCornerShape(50),
        color = SpamOrange.copy(alpha = 0.18f),
        modifier = modifier.clickable {
            Toast.makeText(context, reported, Toast.LENGTH_SHORT).show()
            endAsSpam(context.container, number)
        },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Report, contentDescription = null, tint = SpamOrange, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.call_end_spam), color = Color.White, fontSize = 15.sp)
        }
    }
}

@Composable
private fun BoxScope.OngoingCall(state: CallUiState) {
    val audio by CallManager.audio.collectAsStateWithLifecycle()
    var showKeypad by remember { mutableStateOf(false) }
    // The proximity lock lets go while the keypad is open, so the screen stays usable for digits.
    LaunchedEffect(showKeypad) { CallManager.setKeypadOpen(showKeypad) }
    DisposableEffect(Unit) { onDispose { CallManager.setKeypadOpen(false) } }
    var pickBluetooth by remember { mutableStateOf(false) }
    val status = when (state.phase) {
        CallPhase.Dialing -> stringResource(R.string.call_dialing)
        CallPhase.OnHold -> stringResource(R.string.call_on_hold)
        else -> state.connectedAt?.let { elapsed(it) }.orEmpty()
    }

    Column(Modifier.fillMaxSize()) {
    CallerHeader(status, state.caller, showPhoto = !showKeypad && state.held == null)
    state.held?.let { HeldRow(it, state.canMerge) }
    // The free space shows the rest of the contact, unless a waiting call or the keypad needs it.
    if (state.held == null && !showKeypad) {
        CallerExtras(state.caller, Modifier.weight(1f).fillMaxWidth())
    } else {
        Spacer(Modifier.weight(1f))
    }
    // Kept away from the end button: this one also tells the server, so a slip shouldn't land on it.
    val reportable = state.phase == CallPhase.Active && state.incoming && !state.caller.isContact && state.held == null
    if (reportable && !showKeypad && PhoneNumbers.toE164(state.caller.number) != null) {
        EndAsSpam(state.caller.number, Modifier.align(Alignment.CenterHorizontally))
    }

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .liquidGlass(RoundedCornerShape(32.dp), onDark = true),
    ) {
        Column(Modifier.padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showKeypad) {
                Dialpad(onDigit = CallManager::playDtmf, tint = Color.White)
                IconButton(onClick = { showKeypad = false }) {
                    Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.call_keypad_hide), tint = Color.White, modifier = Modifier.size(30.dp))
                }
            } else {
                ControlGrid(
                    state = state,
                    audio = audio,
                    onBluetooth = {
                        if (!audio.onBluetooth && audio.bluetoothDevices.size > 1) pickBluetooth = true else CallManager.toggleBluetooth()
                    },
                    onKeypad = { showKeypad = true },
                )
            }
            Spacer(Modifier.height(16.dp))
            CircleButton(Icons.Filled.CallEnd, R.string.call_end, Color.White, EndRed) { CallManager.hangUp() }
        }
    }
    }

    if (pickBluetooth) {
        AlertDialog(
            onDismissRequest = { pickBluetooth = false },
            title = { Text(stringResource(R.string.call_bluetooth)) },
            text = {
                Column {
                    audio.bluetoothDevices.forEach { device ->
                        Text(
                            device.name,
                            fontSize = 17.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    CallManager.selectBluetooth(device)
                                    pickBluetooth = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickBluetooth = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** The waiting call: tap it to go there, or join the two. */
@Composable
private fun HeldRow(held: HeldCall, canMerge: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .stretchy(strength = 0.4f, follow = false)
            .clickable(onClick = CallManager::swap)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(held.title, null, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(held.title, color = Soft, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.call_on_hold), color = Soft, fontSize = 14.sp)
        }
        if (canMerge) {
            Control(Icons.Filled.CallMerge, stringResource(R.string.call_merge), null, active = false, onClick = CallManager::merge)
        }
    }
}

@Composable
private fun elapsed(connectedAt: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connectedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return formatDuration(((now - connectedAt) / 1000).coerceAtLeast(0))
}

@Composable
private fun ControlGrid(state: CallUiState, audio: AudioUiState, onBluetooth: () -> Unit, onKeypad: () -> Unit) {
    val context = LocalContext.current
    val onHold = state.phase == CallPhase.OnHold
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Control(
                icon = Icons.Filled.Add,
                label = stringResource(R.string.call_add),
                detail = null,
                active = false,
                enabled = state.canAddCall,
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_DIAL, "tel:".toUri())
                            .setPackage(context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
            Control(
                icon = if (onHold) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                label = stringResource(if (onHold) R.string.call_resume else R.string.call_hold),
                detail = null,
                active = onHold,
                enabled = state.canHold,
                onClick = CallManager::toggleHold,
            )
            Control(
                icon = Icons.Filled.Bluetooth,
                label = stringResource(R.string.call_bluetooth),
                detail = audio.bluetoothName,
                active = audio.onBluetooth,
                enabled = audio.bluetoothDevices.isNotEmpty(),
                onClick = onBluetooth,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Control(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                label = stringResource(R.string.call_speaker),
                detail = null,
                active = audio.speakerOn,
                onClick = CallManager::toggleSpeaker,
            )
            Control(Icons.Filled.MicOff, stringResource(R.string.call_mute), null, audio.muted, onClick = CallManager::toggleMute)
            Control(Icons.Filled.Dialpad, stringResource(R.string.call_keypad), null, false, onClick = onKeypad)
        }
    }
}

@Composable
private fun Control(
    icon: ImageVector,
    label: String,
    detail: String?,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp).alpha(if (enabled) 1f else 0.35f),
    ) {
        val fill by animateColorAsState(if (active) Color.White else Color.Transparent, motionSpec(tween(220)), label = "controlFill")
        val ink by animateColorAsState(if (active) Color.Black else Color.White, motionSpec(tween(220)), label = "controlInk")
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(18.dp),
            color = fill,
            modifier = Modifier.size(56.dp).stretchy(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = ink, modifier = Modifier.size(28.dp))
            }
        }
        Text(label, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (detail != null) {
            Text(detail, color = Soft, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BoxScope.AccountPicker() {
    val context = LocalContext.current
    val telecom = remember { context.getSystemService(TelecomManager::class.java) }
    val accounts: List<PhoneAccountHandle> = remember {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            telecom.callCapablePhoneAccounts
        } else {
            emptyList()
        }
    }
    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.call_pick_sim), color = Color.White, fontSize = 22.sp)
        Spacer(Modifier.height(20.dp))
        accounts.forEach { account ->
            Text(
                telecom.getPhoneAccount(account)?.label?.toString() ?: account.id,
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier
                    .clickable { CallManager.selectAccount(account) }
                    .padding(16.dp),
            )
        }
    }
    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)) {
        CircleButton(Icons.Filled.CallEnd, R.string.call_end, Color.White, EndRed) { CallManager.hangUp() }
    }
}
