package com.bettercallsoul.ui

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.compose.LifecycleStartEffect
import com.bettercallsoul.container
import com.bettercallsoul.ownNumber
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PhoneCallback
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bettercallsoul.R
import com.bettercallsoul.codes.ScanCodes
import com.bettercallsoul.codes.ScanResult
import com.bettercallsoul.telecom.CallManager

sealed interface Route {
    data class Contact(val id: Long) : Route
    data class Number(val number: String) : Route
    data class NewContact(val number: String?) : Route
    data class EditContact(val id: Long) : Route

    /** The owner's own card, made for the first time. */
    data object NewProfile : Route

    /** Not a page: the Search tab, searching for a contact label. */
    data class SearchLabel(val label: String) : Route
    data object Settings : Route
    data object Trash : Route
    data object Stats : Route
    data object Labels : Route
    data object Blocked : Route
    data object Duplicates : Route
    data object MoveContacts : Route
    data object ExportContacts : Route

    /** A new contact filled from the business card in a scanned QR code; [raw] is the code's text. */
    data class ScannedContact(val raw: String) : Route
}

/** A page as text: for keeping the open pages, and for keeping each page's own state while it is covered. */
private fun Route.key(): String = when (this) {
    is Route.Contact -> "contact:$id"
    is Route.Number -> "number:$number"
    is Route.NewContact -> "new:${number.orEmpty()}"
    is Route.EditContact -> "edit:$id"
    Route.NewProfile -> "profile"
    is Route.SearchLabel -> "label:$label"
    Route.Settings -> "settings"
    Route.Trash -> "trash"
    Route.Stats -> "stats"
    Route.Labels -> "labels"
    Route.Blocked -> "blocked"
    Route.Duplicates -> "duplicates"
    Route.MoveContacts -> "move-contacts"
    Route.ExportContacts -> "export-contacts"
    is Route.ScannedContact -> "scan:$raw"
}

/** Keeps the open screens across a theme or language change, which recreates the activity. */
private val RouteStackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { stack -> stack.map { it.key() } },
    restore = { saved ->
        saved.mapNotNull {
            val value = it.substringAfter(':', "")
            when (it.substringBefore(':')) {
                "contact" -> value.toLongOrNull()?.let(Route::Contact)
                "number" -> Route.Number(value)
                "new" -> Route.NewContact(value.ifEmpty { null })
                "profile" -> Route.NewProfile
                "edit" -> value.toLongOrNull()?.let(Route::EditContact)
                "settings" -> Route.Settings
                "trash" -> Route.Trash
                "stats" -> Route.Stats
                "labels" -> Route.Labels
                "blocked" -> Route.Blocked
                "duplicates" -> Route.Duplicates
                "move-contacts" -> Route.MoveContacts
                "export-contacts" -> Route.ExportContacts
                "scan" -> Route.ScannedContact(value)
                else -> null
            }
        }.toMutableStateList()
    },
)

private enum class Tab(val label: Int, val icon: ImageVector) {
    Home(R.string.tab_home, Icons.Outlined.Home),
    Keypad(R.string.tab_keypad, Icons.Outlined.Dialpad),
    Search(R.string.tab_search, Icons.Outlined.Search),
    Recents(R.string.tab_recents, Icons.AutoMirrored.Outlined.PhoneCallback),
    Contacts(R.string.tab_contacts, Icons.Outlined.PersonOutline),
}

@Composable
fun MainScreen(
    isDefaultDialer: Boolean,
    dialRequest: String?,
    onDialRequestHandled: () -> Unit,
    missedRequest: String?,
    onMissedRequestHandled: () -> Unit,
    onRequestDefault: () -> Unit,
    scanRequest: String? = null,
    onScanRequestHandled: () -> Unit = {},
    openRequest: Route? = null,
    onOpenRequestHandled: () -> Unit = {},
    lastTouch: () -> Offset = { Offset.Zero },
    suggestion: Suggestion? = null,
    onSuggestion: (Suggestion) -> Unit = {},
    onCloseSuggestion: (Suggestion) -> Unit = {},
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var digits by rememberSaveable { mutableStateOf("") }
    // Search opens with the keyboard up and an empty field when its tab is chosen, not on the way
    // back from a result, which keeps the query. Its back arrow returns to the tab it came from.
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchFocus by remember { mutableStateOf(false) }
    var beforeSearch by rememberSaveable { mutableStateOf(Tab.Home) }
    val stack = rememberSaveable(saver = RouteStackSaver) { mutableStateListOf() }
    val tabStates = rememberSaveableStateHolder()
    // Each open page keeps its own state while another lies over it (Settings its open group, a list
    // its scroll), and forgets it once closed.
    val routeStates = rememberSaveableStateHolder()
    val liveRoutes = stack.mapIndexed { depth, route -> "$depth:${route.key()}" }
    var shownRoutes by remember { mutableStateOf(emptyList<String>()) }
    SideEffect {
        (shownRoutes - liveRoutes.toSet()).forEach(routeStates::removeState)
        shownRoutes = liveRoutes
    }
    // Counts the taps on a tab that is already shown; a new count sends its list to the top.
    var toTop by remember { mutableStateOf(emptyMap<Tab, Int>()) }
    // Closed on Home it stays away only until the app is opened again.
    var defaultBannerClosed by remember { mutableStateOf(false) }
    LifecycleStartEffect(Unit) {
        defaultBannerClosed = false
        onStopOrDispose { }
    }

    LaunchedEffect(missedRequest) {
        if (missedRequest != null) {
            tab = Tab.Recents
            stack.clear()
            if (missedRequest.isNotEmpty()) stack.add(Route.Number(missedRequest))
            onMissedRequestHandled()
        }
    }
    // The missed calls are seen once their list is in view; a page opened over it from one
    // person's notification counts only that person's calls, so the others' notifications stay.
    val atTabRoot = stack.isEmpty()
    LaunchedEffect(tab, atTabRoot) {
        if ((tab == Tab.Recents || tab == Tab.Home) && stack.isEmpty()) com.bettercallsoul.MissedCallReceiver.clear(context)
    }
    // A person's page asked for from a widget opens over whatever was there.
    LaunchedEffect(openRequest) {
        if (openRequest != null) {
            stack.clear()
            stack.add(openRequest)
            onOpenRequestHandled()
        }
    }

    LaunchedEffect(dialRequest) {
        if (dialRequest != null) {
            digits = dialRequest
            tab = Tab.Keypad
            stack.clear()
            onDialRequestHandled()
        }
    }

    // A code read in a debug build: a business card opens a filled-in new contact, a number a new contact with it,
    // and a link or plain text shows at the bottom to open or copy.
    var scanResult by remember { mutableStateOf<ScanResult?>(null) }
    val handleCode: (String) -> Unit = { raw ->
        when (val read = ScanCodes.read(raw)) {
            is ScanResult.Contact -> stack.add(Route.ScannedContact(raw))
            is ScanResult.Phone -> stack.add(Route.NewContact(read.number))
            else -> scanResult = read
        }
    }
    LaunchedEffect(scanRequest) {
        if (scanRequest != null) {
            handleCode(scanRequest)
            onScanRequestHandled()
        }
    }

    val switchTab: (Tab) -> Unit = { tab = it }
    // A call starts behind a green burst from the finger, which turns into the call screen's colour.
    var burst by remember { mutableStateOf<CallBurst?>(null) }
    val animated = LocalMotion.current
    val call: (String) -> Unit = { number ->
        if (animated) burst = CallBurst(number, lastTouch()) else CallManager.place(context, number)
    }
    val open: (Route) -> Unit = { route ->
        if (route is Route.SearchLabel) {
            context.container.rememberSearch(route.label, label = true)
            if (tab != Tab.Search) beforeSearch = tab
            searchQuery = route.label
            searchFocus = false
            tab = Tab.Search
            stack.clear()
        } else {
            stack.add(route)
        }
    }
    val back: () -> Unit = { stack.removeLastOrNull() }

    // A page opens over the tabs, which stay composed: their lists are where they were on the way back.
    val focus = LocalFocusManager.current
    LaunchedEffect(stack.size) { if (stack.isNotEmpty()) focus.clearFocus() }
    val underPage = if (stack.isNotEmpty()) Modifier.clearAndSetSemantics { } else Modifier

    val motion = LocalMotion.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().then(underPage)) {
            Crossfade(targetState = tab, animationSpec = if (motion) tween(180) else snap(), label = "tab", modifier = Modifier.weight(1f)) { tab ->
              // Each tab keeps its own scroll and state while another is shown.
              tabStates.SaveableStateProvider(tab.name) {
                when (tab) {
                    Tab.Home -> HomeScreen(
                        onCall = call,
                        onOpen = open,
                        toTop = toTop[Tab.Home] ?: 0,
                        askDefault = !isDefaultDialer && !defaultBannerClosed,
                        onRequestDefault = onRequestDefault,
                        onCloseDefault = { defaultBannerClosed = true },
                        suggestion = suggestion,
                        onSuggestion = onSuggestion,
                        onCloseSuggestion = onCloseSuggestion,
                    )
                    Tab.Keypad -> KeypadScreen(
                        digits = digits,
                        onDigitsChange = { digits = it },
                        onCall = call,
                        onOpen = open,
                        // Closed on Home or here, the banner is gone from both until the app is opened again.
                        isDefaultDialer = isDefaultDialer || defaultBannerClosed,
                        onRequestDefault = onRequestDefault,
                        onCloseDefault = { defaultBannerClosed = true },
                    )
                    Tab.Recents -> RecentsScreen(onCall = call, onOpen = open, toTop = toTop[Tab.Recents] ?: 0)
                    Tab.Contacts -> ContactsScreen(
                        onCall = call,
                        onOpen = open,
                        toTop = toTop[Tab.Contacts] ?: 0,
                    )
                    Tab.Search -> SearchScreen(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        focusField = searchFocus,
                        onFieldFocused = { searchFocus = false },
                        onCall = call,
                        onOpen = open,
                        onBack = { switchTab(beforeSearch) },
                    )
                }
              }
            }
            BottomTabs(selected = tab, onSelect = {
                // The tab already shown, tapped again, goes back to its top.
                if (it == tab && it != Tab.Search) toTop = toTop + (it to (toTop[it] ?: 0) + 1)
                if (it == Tab.Search) {
                    searchFocus = true
                    if (tab != Tab.Search) {
                        searchQuery = ""
                        beforeSearch = tab
                    }
                }
                tab = it
            })
        }
        AnimatedContent(
            targetState = stack.size to stack.lastOrNull(),
            transitionSpec = {
                // No size change is animated: with no page open this is empty, and growing it to the
                // full screen showed as a page unrolling even with animations off.
                if (!motion) {
                    (EnterTransition.None togetherWith ExitTransition.None) using null
                } else {
                    // A deeper page comes in from the trailing edge over the one it covers, and going
                    // back it leaves the same way. Only that upper page moves and fades: the one under
                    // it stays still and solid, or the tabs below both would show through for a moment.
                    val forward = targetState.first > initialState.first
                    val edge = if (rtl) -1 else 1
                    if (forward) {
                        (slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)) { it * edge / 3 } +
                            fadeIn(tween(220))) togetherWith ExitTransition.KeepUntilTransitionsFinished
                    } else {
                        EnterTransition.None togetherWith
                            (slideOutHorizontally(tween(220)) { it * edge / 3 } + fadeOut(tween(220)))
                    }.apply { targetContentZIndex = if (forward) 1f else -1f } using null
                }
            },
            label = "route",
        ) { (size, route) ->
            if (route != null) routeStates.SaveableStateProvider("${size - 1}:${route.key()}") {
                BackHandler(onBack = back)
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) { }) {
                    when (route) {
                    is Route.Contact -> ContactDetailScreen(route.id, onCall = call, onOpen = open, onBack = back)
                    is Route.Number -> NumberPage(route.number, onCall = call, onOpen = open, onBack = back)
                    is Route.NewContact -> ContactEditorScreen(null, route.number, onDone = back)
                    is Route.SearchLabel -> Unit
                    Route.NewProfile -> ContactEditorScreen(null, remember { ownNumber(context) }, profile = true, onDone = back)
                    is Route.EditContact -> ContactEditorScreen(route.id, null, onDone = back, onMoved = { newId ->
                        // Moved to another account it is a new contact: the profile under the editor follows it.
                        stack.removeLastOrNull()
                        val below = stack.lastIndex
                        if (below >= 0 && stack[below] == Route.Contact(route.id)) stack[below] = Route.Contact(newId)
                    })
                    Route.Settings -> SettingsScreen(isDefaultDialer, onRequestDefault, onOpen = open, onBack = back)
                    Route.Trash -> TrashScreen(onBack = back)
                    Route.Stats -> StatsScreen(onOpen = open, onBack = back)
                    Route.Labels -> LabelsScreen(onOpen = open, onBack = back)
                    Route.Blocked -> BlockedScreen(onOpen = open, onBack = back)
                    Route.Duplicates -> DuplicatesScreen(onOpen = open, onBack = back)
                    Route.MoveContacts -> MoveContactsScreen(onBack = back)
                    Route.ExportContacts -> ExportContactsScreen(onBack = back)
                    is Route.ScannedContact -> ContactEditorScreen(null, null, scanned = route.raw, onDone = back)
                    }
                }
            }
        }
        burst?.let { CallBurstOverlay(it, onCovered = { CallManager.place(context, it.number) }, onFinished = { burst = null }) }
        scanResult?.let { result ->
            ScanResultBar(
                result,
                onDismiss = { scanResult = null },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp),
            )
        }
    }
}

private class CallBurst(val number: String, val center: Offset)

/**
 * Grows from the finger over the whole app while turning from green into the colour the call screen
 * opens with, so that screen comes up unseen. It stays a moment for slow phones, then lets go.
 */
@Composable
private fun CallBurstOverlay(burst: CallBurst, onCovered: () -> Unit, onFinished: () -> Unit) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(burst) {
        grow.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
        onCovered()
        delay(BURST_HOLD_MILLIS)
        onFinished()
    }
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) { }) {
        val far = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
            .maxOf { (it - burst.center).getDistance() }
        val start = 28.dp.toPx()
        drawCircle(lerp(CallGreen, CallScreenColor, grow.value), radius = start + (far - start) * grow.value, center = burst.center)
    }
}

private val CallScreenColor = Color(0xFF2B3036)
private const val BURST_HOLD_MILLIS = 1_500L

/**
 * The tab bar as a floating glass capsule. The light under the selected tab slides like a drop of
 * liquid: it stretches while moving and settles with a small wobble.
 */
@Composable
private fun BottomTabs(selected: Tab, onSelect: (Tab) -> Unit) {
    val motion = LocalMotion.current
    val tabs = Tab.entries
    val position = remember { Animatable(selected.ordinal.toFloat()) }
    LaunchedEffect(selected, motion) {
        if (motion) {
            position.animateTo(selected.ordinal.toFloat(), spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
        } else {
            position.snapTo(selected.ordinal.toFloat())
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(64.dp)
            .liquidGlass(RoundedCornerShape(32.dp)),
    ) {
        val slot = maxWidth / tabs.size
        val stretch = 1f + (abs(position.velocity) * 0.06f).coerceAtMost(0.45f)
        Box(
            Modifier
                .offset { IntOffset((slot * position.value).roundToPx(), 0) }
                .width(slot)
                .fillMaxHeight()
                .padding(6.dp)
                .graphicsLayer { scaleX = stretch; scaleY = 1f / stretch.coerceAtLeast(1f).let { 1f + (it - 1f) * 0.4f } }
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEach { tab ->
                val active = tab == selected
                val color by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    motionSpec(tween(200)),
                    label = "tabColor",
                )
                val lift by animateFloatAsState(if (active) 1.12f else 1f, motionSpec(spring(dampingRatio = 0.45f)), label = "tabLift")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .stretchy()
                        .clip(RoundedCornerShape(26.dp))
                        .clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.graphicsLayer { scaleX = lift; scaleY = lift })
                    Text(
                        stringResource(tab.label),
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
