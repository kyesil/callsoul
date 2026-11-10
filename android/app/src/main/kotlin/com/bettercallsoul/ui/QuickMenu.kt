package com.bettercallsoul.ui

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bettercallsoul.R
import com.bettercallsoul.container
import com.bettercallsoul.calllog.WhatsAppAction
import com.bettercallsoul.calllog.WhatsAppKind
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** What a swipe up on a person's tile offers, in the order they sit around it from the left: phone and SMS blue, WhatsApp green. */
enum class QuickAction(val icon: ImageVector, @param:StringRes val label: Int, val color: Color) {
    Message(Icons.AutoMirrored.Filled.Message, R.string.action_message, MessageBlue),
    WhatsAppMessage(Icons.AutoMirrored.Filled.Message, R.string.quick_whatsapp_message, WhatsAppGreen),
    Call(Icons.Filled.Call, R.string.action_call, MessageBlue),
    WhatsAppCall(Icons.Filled.Call, R.string.quick_whatsapp_call, WhatsAppGreen),
    Video(Icons.Filled.Videocam, R.string.quick_whatsapp_video, WhatsAppGreen),
}

/** The number the menu acts on, and the WhatsApp calls WhatsApp has put on its contact. */
class QuickTarget(val number: String, val whatsApp: List<WhatsAppAction>) {
    private val voice = whatsApp.firstOrNull { it.kind == WhatsAppKind.Voice }
    private val video = whatsApp.firstOrNull { it.kind == WhatsAppKind.Video }

    /** WhatsApp's own entries only for someone WhatsApp knows, so none of them leads nowhere. */
    val actions: List<QuickAction> = buildList {
        add(QuickAction.Message)
        if (whatsApp.isNotEmpty()) add(QuickAction.WhatsAppMessage)
        add(QuickAction.Call)
        if (voice != null) add(QuickAction.WhatsAppCall)
        if (video != null) add(QuickAction.Video)
    }

    fun run(context: Context, action: QuickAction, onCall: (String) -> Unit) {
        when (action) {
            QuickAction.Call -> onCall(number)
            QuickAction.Message -> runCatching { sendSms(context, number) }
            QuickAction.WhatsAppMessage -> if (!openWhatsAppChat(context, number)) {
                Toast.makeText(context, R.string.whatsapp_missing, Toast.LENGTH_SHORT).show()
            }
            QuickAction.WhatsAppCall -> voice?.let { openWhatsApp(context, it) }
            QuickAction.Video -> video?.let { openWhatsApp(context, it) }
        }
    }
}

/**
 * The menu a swipe up on a person's tile opens around their photo. Still holding, the finger points
 * at an action and lets go to run it; let go anywhere else, the menu stays for a tap.
 */
@Stable
class QuickMenu internal constructor(private val context: Context) {
    internal class Opened(val center: Offset, val name: String, val photoUri: String?, val load: suspend () -> QuickTarget?)

    internal var opened by mutableStateOf<Opened?>(null)
    internal var target by mutableStateOf<QuickTarget?>(null)
    internal var chosen by mutableStateOf<QuickAction?>(null)
    internal var onCall: (String) -> Unit = {}

    /** Where each action's bubble sits in the window, as last laid out. */
    internal var spots: Map<QuickAction, Offset> = emptyMap()

    internal fun open(menu: Opened) {
        target = null
        chosen = null
        spots = emptyMap()
        opened = menu
    }

    fun close() {
        opened = null
        target = null
        chosen = null
        spots = emptyMap()
    }

    /**
     * The action the finger, [travel] away from where it went down, heads for: the bubble nearest to
     * the point that far out along its way. None while the finger is still close in.
     */
    internal fun aim(travel: Offset, reach: Float, radius: Float): QuickAction? {
        val center = opened?.center ?: return null
        val distance = travel.getDistance()
        if (distance < reach) return null
        val probe = center + travel * (maxOf(distance, radius) / distance)
        val nearest = spots.minByOrNull { (it.value - probe).getDistance() } ?: return null
        return nearest.key.takeIf { (nearest.value - probe).getDistance() <= radius * 0.6f }
    }

    internal fun run(action: QuickAction) {
        val target = target ?: return
        close()
        target.run(context, action, onCall)
    }
}

@Composable
fun rememberQuickMenu(onCall: (String) -> Unit): QuickMenu {
    val context = LocalContext.current
    val call by rememberUpdatedState(onCall)
    return remember { QuickMenu(context).apply { this.onCall = { call(it) } } }
}

/** Where the photo's middle sits in a tile: its top padding and half of it. */
private val PhotoMiddle = 6.dp + 28.dp

/**
 * Opens [menu] on a quick swipe up that starts on this tile. A sideways swipe stays the row's, a swipe
 * down the page's, and a held tile the long press's.
 */
fun Modifier.quickMenu(menu: QuickMenu, name: String, photoUri: String?, load: suspend () -> QuickTarget?): Modifier = composed {
    if (!LocalContext.current.container.quickMenuEnabled) return@composed this
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val reach = with(density) { 32.dp.toPx() }
    val radius = with(density) { Radius.toPx() }
    val middle = with(density) { PhotoMiddle.toPx() }
    val place = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val currentLoad by rememberUpdatedState(load)
    val currentName by rememberUpdatedState(name)
    val currentPhoto by rememberUpdatedState(photoUri)
    this
        .onGloballyPositioned { place[0] = it }
        .pointerInput(menu) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val coordinates = place[0]?.takeIf { it.isAttached } ?: return@awaitEachGesture
                // In the window's terms, as the tile itself leans with the finger.
                val start = coordinates.localToRoot(down.position)
                val center = coordinates.positionInRoot() + Offset(coordinates.size.width / 2f, middle)
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (!change.pressed || change.isConsumed) return@awaitEachGesture
                    if (change.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) return@awaitEachGesture
                    val travel = coordinates.localToRoot(change.position) - start
                    if (travel.getDistance() > viewConfiguration.touchSlop) {
                        if (travel.y >= 0 || abs(travel.y) < abs(travel.x)) return@awaitEachGesture
                        change.consume()
                        break
                    }
                }
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                menu.open(QuickMenu.Opened(center, currentName, currentPhoto) { currentLoad() })
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    if (!coordinates.isAttached) continue
                    val aimed = menu.aim(coordinates.localToRoot(change.position) - start, reach, radius)
                    if (aimed != menu.chosen) {
                        menu.chosen = aimed
                        if (aimed != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                }
                val picked = menu.chosen
                if (picked != null) menu.run(picked) else menu.chosen = null
            }
        }
}

private val Bubble = 44.dp
private val Radius = 96.dp

/**
 * The open menu over the screen, which [origin] says where it starts in the window: the page dimmed,
 * the photo again in the middle, the actions in an arc that leans away from the screen's edges. With
 * [onPhoto], a tap on the photo does that, such as opening the person's page.
 */
@Composable
fun QuickMenuOverlay(menu: QuickMenu, origin: Offset, onPhoto: (() -> Unit)? = null) {
    val opened = menu.opened ?: return
    BackHandler { menu.close() }
    LaunchedEffect(opened) {
        val target = runCatching { opened.load() }.getOrNull()
        if (menu.opened !== opened) return@LaunchedEffect
        if (target == null) menu.close() else menu.target = target
    }
    val motion = LocalMotion.current
    val shown = remember(opened) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(opened) { shown.animateTo(1f, tween(150)) }
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.getTop(density)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(opened) { detectTapGestures { menu.close() } },
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val center = opened.center - origin
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = shown.value }.background(Color.Black.copy(alpha = 0.55f)))
        val photo = with(density) { 56.dp.toPx() }
        Box(Modifier.offset { IntOffset((center.x - photo / 2).roundToInt(), (center.y - photo / 2).roundToInt()) }) {
            Avatar(
                opened.name, opened.photoUri, 56.dp,
                if (onPhoto == null) Modifier else Modifier.clip(CircleShape).clickable { menu.close(); onPhoto() },
            )
        }

        val target = menu.target ?: return@BoxWithConstraints
        val actions = target.actions
        val radius = with(density) { Radius.toPx() }
        val arc = remember(actions, center, width, height) {
            with(density) {
                arcAngles(
                    count = actions.size,
                    center = center,
                    radius = radius,
                    halfWidth = Bubble.toPx() / 2,
                    above = Bubble.toPx() / 2,
                    below = Bubble.toPx() / 2,
                    width = width,
                    height = height,
                    top = maxOf(8.dp.toPx(), topInset - origin.y),
                    margin = 8.dp.toPx(),
                )
            }
        }
        val hub = center + Offset(arc.shift, 0f)
        val outs = arc.angles.map { degrees ->
            val angle = Math.toRadians(degrees.toDouble())
            Offset(cos(angle).toFloat(), -sin(angle).toFloat()) * radius
        }
        SideEffect { menu.spots = actions.indices.associate { actions[it] to origin + hub + outs[it] } }
        actions.forEachIndexed { i, action ->
            val out = outs[i]
            ActionBubble(
                action = action,
                chosen = menu.chosen == action,
                modifier = Modifier
                    .offset {
                        val at = center + (hub - center + out) * shown.value
                        IntOffset((at.x - Bubble.toPx() / 2).roundToInt(), (at.y - Bubble.toPx() / 2).roundToInt())
                    }
                    .graphicsLayer { alpha = shown.value },
                onClick = { menu.run(action) },
            )
        }
    }
}

/** A round button, its words only for screen readers: the icon and colour say what it does. */
@Composable
private fun ActionBubble(action: QuickAction, chosen: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val motion = LocalMotion.current
    val grow by animateFloatAsState(if (chosen) 1.25f else 1f, if (motion) tween(90) else tween(0), label = "grow")
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = action.color,
        shadowElevation = if (chosen) 8.dp else 2.dp,
        modifier = modifier.size(Bubble).graphicsLayer {
            scaleX = grow
            scaleY = grow
        },
    ) {
        Icon(action.icon, stringResource(action.label), tint = Color.White, modifier = Modifier.padding(11.dp))
    }
}

private class Arc(val angles: List<Float>, val shift: Float)

/**
 * The directions of [count] actions around [center], spread from the left: straight up, leaning a
 * little away from a near edge and sliding off it as far as it must; below only where there's no room above.
 */
private fun arcAngles(
    count: Int,
    center: Offset,
    radius: Float,
    halfWidth: Float,
    above: Float,
    below: Float,
    width: Float,
    height: Float,
    top: Float,
    margin: Float,
): Arc {
    val step = if (count <= 1) 0f else minOf(36f, 144f / (count - 1))
    fun around(middle: Float) = List(count) { i -> middle + step * ((count - 1) / 2f - i) }
    // A lower angle leans right: away from the left edge for a tile on the left half.
    val away = if (center.x < width / 2) -1 else 1
    val tilts = listOf(0, 10, 20, 30).map { away * it }
    for (base in listOf(90f, 270f)) {
        val arcs = tilts.mapNotNull { tilt ->
            val angles = around(base + tilt)
            val xs = angles.map { center.x + radius * cos(Math.toRadians(it.toDouble())).toFloat() }
            val ys = angles.map { center.y - radius * sin(Math.toRadians(it.toDouble())).toFloat() }
            if (ys.any { it - above < top || it + below > height - margin }) return@mapNotNull null
            val left = xs.min() - halfWidth
            val right = xs.max() + halfWidth
            if (right - left > width - 2 * margin) return@mapNotNull null
            val shift = when {
                left < margin -> margin - left
                right > width - margin -> width - margin - right
                else -> 0f
            }
            Arc(angles, shift)
        }
        // The least sliding wins; between equals, the least lean.
        arcs.minByOrNull { abs(it.shift) }?.let { return it }
    }
    return Arc(around(90f), 0f)
}
