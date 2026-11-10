package com.bettercallsoul.ui

import com.bettercallsoul.ThemeChoice
import com.bettercallsoul.AppLook
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.JoinInner
import androidx.compose.material.icons.outlined.Block
import android.app.NotificationManager
import android.app.ActivityManager
import android.content.Context

import com.bettercallsoul.diag.DebugLog
import android.text.format.Formatter
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextDecoration
import com.bettercallsoul.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.bettercallsoul.island.CallOverlay
import com.bettercallsoul.island.PillStyle
import java.text.NumberFormat
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bettercallsoul.WhatsAppCallListener
import android.content.ComponentName
import com.bettercallsoul.SwipeMessage
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import com.bettercallsoul.MAX_SWIPE_EDGE
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bettercallsoul.BackupWatch
import com.bettercallsoul.TelLinks
import com.bettercallsoul.R
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.bettercallsoul.container
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SmartButton
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.bettercallsoul.calllog.CallHistory
import com.bettercallsoul.calllog.LocalDatabase
import com.bettercallsoul.spam.SearchText
import java.io.File

/** The groups the settings are sorted into: the first page lists them and each opens a page of its own. */
private enum class SettingsGroup(val title: Int, val icon: ImageVector) {
    Calls(R.string.settings_group_calls, Icons.Outlined.Call),
    Contacts(R.string.settings_group_contacts, Icons.Outlined.People),
    WhatsApp(R.string.whatsapp, Icons.Outlined.Forum),
    Pill(R.string.settings_pill_title, Icons.Outlined.SmartButton),
    Looks(R.string.settings_group_looks, Icons.Outlined.TouchApp),
    Storage(R.string.settings_group_storage, Icons.Outlined.History),
    About(R.string.settings_group_about, Icons.Outlined.Info),
}

/** One setting: its group, the texts a search finds it by (the first is its name), and how it is drawn. */
private class SettingEntry(val group: SettingsGroup, val words: List<Int>, val content: @Composable () -> Unit)

@Composable
fun SettingsScreen(isDefaultDialer: Boolean, onRequestDefault: () -> Unit, onBack: () -> Unit, onOpen: (Route) -> Unit = {}) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    LifecycleResumeEffect(Unit) {
        overlayAllowed = Settings.canDrawOverlays(context)
        onPauseOrDispose { }
    }
    var groupName by rememberSaveable { mutableStateOf<String?>(null) }
    val group = groupName?.let(SettingsGroup::valueOf)
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = group != null) { groupName = null }

    val entries = listOf(
        SettingEntry(SettingsGroup.Calls, listOf(R.string.settings_lookup_title, R.string.settings_lookup_body)) { LookupSettings() },
        SettingEntry(SettingsGroup.Calls, listOf(R.string.settings_face_down_title, R.string.settings_face_down_body)) { FaceDownSettings() },
        SettingEntry(SettingsGroup.Calls, listOf(R.string.settings_replies_title, R.string.settings_replies_body)) { QuickReplySettings() },
        SettingEntry(SettingsGroup.Calls, listOf(R.string.key_decline_title, R.string.key_decline_body)) { KeySettings() },
        SettingEntry(SettingsGroup.Calls, listOf(R.string.tone_connect_title, R.string.tone_connect_body, R.string.tone_end_title, R.string.tone_end_body)) { ToneSettings() },
        SettingEntry(SettingsGroup.Calls, listOf(R.string.dnd_title, R.string.dnd_body)) { DndSettings() },
        SettingEntry(SettingsGroup.Calls, listOf(R.string.blocked_title, R.string.blocked_body)) {
            LinkCard(Icons.Outlined.Block, R.string.blocked_title, R.string.blocked_body) { onOpen(Route.Blocked) }
        },
        SettingEntry(SettingsGroup.Contacts, listOf(R.string.dup_title, R.string.dup_body)) {
            LinkCard(Icons.Outlined.JoinInner, R.string.dup_title, R.string.dup_body) { onOpen(Route.Duplicates) }
        },
        SettingEntry(SettingsGroup.Contacts, listOf(R.string.section_labels, R.string.labels_body)) {
            LinkCard(Icons.AutoMirrored.Outlined.Label, R.string.section_labels, R.string.labels_body) { onOpen(Route.Labels) }
        },
        SettingEntry(SettingsGroup.Calls, listOf(R.string.settings_default_held, R.string.tel_links_row)) { DefaultAppSettings(isDefaultDialer, onRequestDefault) },
        SettingEntry(
            SettingsGroup.WhatsApp,
            listOf(R.string.settings_whatsapp_title, R.string.settings_whatsapp_body, R.string.settings_whatsapp_pill_title, R.string.settings_whatsapp_pill_body),
        ) { WhatsAppCallSettings() },
        SettingEntry(SettingsGroup.Pill, listOf(R.string.settings_overlay_title, R.string.settings_overlay_body)) { OverlaySettings(overlayAllowed) },
        SettingEntry(
            SettingsGroup.Pill,
            listOf(
                R.string.settings_pill_title, R.string.settings_pill_rings_title, R.string.settings_pill_rings_body,
                R.string.settings_pill_orientation, R.string.settings_pill_size, R.string.settings_pill_order, R.string.settings_pill_opacity,
            ),
        ) { PillSettings(overlayAllowed) },
        SettingEntry(SettingsGroup.Looks, listOf(R.string.settings_theme_title, R.string.theme_system, R.string.theme_light, R.string.theme_dark)) { ThemeSettings() },
        SettingEntry(SettingsGroup.Looks, listOf(R.string.settings_language_title)) { LanguageSettings() },
        SettingEntry(SettingsGroup.Looks, listOf(R.string.settings_animations_title, R.string.settings_animations_body)) { AnimationSettings() },
        SettingEntry(SettingsGroup.Looks, listOf(R.string.settings_swipe_title, R.string.settings_swipe_body, R.string.settings_swipe_message)) { SwipeSettings() },
        SettingEntry(SettingsGroup.Looks, listOf(R.string.settings_quick_menu_title, R.string.settings_quick_menu_body)) { QuickMenuSettings() },
        SettingEntry(SettingsGroup.Storage, listOf(R.string.settings_history_title, R.string.settings_history_body)) { HistorySettings() },
        SettingEntry(SettingsGroup.Storage, listOf(R.string.settings_backup_title, R.string.settings_backup_body)) { BackupSettings() },
        SettingEntry(SettingsGroup.About, listOf(R.string.settings_log_title, R.string.settings_log_body)) { DebugLogSettings(isDefaultDialer) },
        SettingEntry(SettingsGroup.About, listOf(R.string.about_title, R.string.about_developer, R.string.about_source, R.string.about_license, R.string.about_open_source)) { About() },
    )

    // Each page starts at its top.
    key(groupName) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
            DetailToolbar(onBack = { if (group != null) groupName = null else onBack() })
            LargeTitle(stringResource(group?.title ?: R.string.action_settings), null)
            if (group != null) {
                entries.filter { it.group == group }.forEach {
                    it.content()
                    Spacer(Modifier.height(6.dp))
                }
            } else {
                CompactTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.settings_search_hint),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                val text = query.trim()
                if (text.isEmpty()) {
                    Card {
                        Column {
                            SettingsGroup.entries.forEachIndexed { index, each ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(start = 62.dp),
                                    )
                                }
                                val summary = entries.filter { it.group == each }.joinToString(", ") { resources.getString(it.words.first()) }
                                GroupRow(each, summary) { groupName = each.name }
                            }
                        }
                    }
                } else {
                    val found = entries.filter { entry ->
                        (entry.words + entry.group.title).any { SearchText.find(resources.getString(it), text) != null }
                    }
                    if (found.isEmpty()) {
                        Text(
                            stringResource(R.string.search_none),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    found.groupBy { it.group }.forEach { (each, list) ->
                        Text(
                            stringResource(each.title),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 22.dp, top = 8.dp, bottom = 6.dp),
                        )
                        list.forEach {
                            it.content()
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A group on the first page: its icon, its name, and what is in it. */
@Composable
private fun GroupRow(group: SettingsGroup, summary: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(group.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(group.title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(summary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A setting that is one switch, with its name and a line on what it does. */
@Composable
private fun SwitchCard(title: Int, body: Int, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(6.dp))
            Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
        }
    }
}

/** A setting that is a page of its own. */
@Composable
private fun LinkCard(icon: ImageVector, title: Int, body: Int, onClick: () -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ThemeSettings() {
    val context = LocalContext.current
    val app = context.container
    Card {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(stringResource(R.string.settings_theme_title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(ThemeChoice.System to R.string.theme_system, ThemeChoice.Light to R.string.theme_light, ThemeChoice.Dark to R.string.theme_dark)
                options.forEachIndexed { index, (choice, label) ->
                    SegmentedButton(
                        selected = app.themeChoice == choice,
                        onClick = {
                            app.saveTheme(choice)
                            AppLook.applyTheme(context, choice)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        label = { Text(stringResource(label), maxLines = 1, fontSize = 13.sp) },
                    )
                }
            }
        }
    }
}

/** The app's own language, each named in itself, the phone's first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageSettings() {
    val context = LocalContext.current
    val current = remember { AppLook.language(context) }
    val system = stringResource(R.string.theme_system)
    Card {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(stringResource(R.string.settings_language_title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLook.LANGUAGES.forEach { tag ->
                    FilterChip(
                        selected = tag == current,
                        onClick = { if (tag != current) (context as? android.app.Activity)?.let { AppLook.setLanguage(it, tag) } },
                        label = { Text(AppLook.languageName(tag, system)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeySettings() {
    val app = LocalContext.current.container
    var decline by remember { mutableStateOf(app.declineOnKey) }
    SwitchCard(R.string.key_decline_title, R.string.key_decline_body, decline) {
        decline = it
        app.declineOnKey = it
    }
}

@Composable
private fun ToneSettings() {
    val app = LocalContext.current.container
    var connect by remember { mutableStateOf(app.toneOnConnect) }
    var end by remember { mutableStateOf(app.toneOnEnd) }
    SwitchCard(R.string.tone_connect_title, R.string.tone_connect_body, connect) {
        connect = it
        app.toneOnConnect = it
    }
    Spacer(Modifier.height(6.dp))
    SwitchCard(R.string.tone_end_title, R.string.tone_end_body, end) {
        end = it
        app.toneOnEnd = it
    }
}

/** Who may still call through Do Not Disturb: 0 nobody, 1 starred, 2 contacts, 3 anyone; and repeat callers. */
private data class CallsThrough(val who: Int, val repeatCallers: Boolean)

private fun callsThrough(context: Context): CallsThrough? = runCatching {
    val policy = context.getSystemService(NotificationManager::class.java).notificationPolicy
    val calls = policy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_CALLS != 0
    val who = if (!calls) 0 else when (policy.priorityCallSenders) {
        NotificationManager.Policy.PRIORITY_SENDERS_ANY -> 3
        NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS -> 2
        NotificationManager.Policy.PRIORITY_SENDERS_STARRED -> 1
        else -> 0
    }
    CallsThrough(who, policy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS != 0)
}.getOrNull()

/** The system page where the user picks who gets through; its name differs between Android versions and makers. */
private fun openDndSettings(context: Context) {
    val pages = listOf("android.settings.ZEN_MODE_PRIORITY_SETTINGS", "android.settings.ZEN_MODE_SETTINGS", Settings.ACTION_SOUND_SETTINGS)
    pages.firstOrNull { page -> runCatching { context.startActivity(Intent(page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
}

/**
 * Do Not Disturb belongs to Android: Telecom asks it whether a call may ring, and since Android 15
 * an app can no longer change the user's own rules. This shows the rule and leads to the system page.
 */
@Composable
private fun DndSettings() {
    val context = LocalContext.current
    var through by remember { mutableStateOf(callsThrough(context)) }
    LifecycleResumeEffect(Unit) {
        through = callsThrough(context)
        onPauseOrDispose { }
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(stringResource(R.string.dnd_title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.dnd_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            through?.let { now ->
                Spacer(Modifier.height(8.dp))
                val who = stringResource(
                    when (now.who) {
                        3 -> R.string.dnd_anyone
                        2 -> R.string.dnd_contacts
                        1 -> R.string.dnd_starred
                        else -> R.string.dnd_none
                    },
                )
                val line = stringResource(R.string.dnd_now, who) + if (now.repeatCallers) " " + stringResource(R.string.dnd_repeat) else ""
                Text(line, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                if (now.who == 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.dnd_hint_favourites), fontSize = 13.sp, color = EndRed)
                }
            }
            TextButton(onClick = { openDndSettings(context) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.dnd_change))
            }
        }
    }
}

@Composable
private fun LookupSettings() {
    val app = LocalContext.current.container
    var lookup by remember { mutableStateOf(app.spamLookupEnabled) }
    SwitchCard(R.string.settings_lookup_title, R.string.settings_lookup_body, lookup) {
        lookup = it
        app.spamLookupEnabled = it
    }
}

@Composable
private fun FaceDownSettings() {
    val app = LocalContext.current.container
    var faceDown by remember { mutableStateOf(app.silentWhenFaceDown) }
    SwitchCard(R.string.settings_face_down_title, R.string.settings_face_down_body, faceDown) {
        faceDown = it
        app.silentWhenFaceDown = it
    }
}

/** Only the system page can change this, in either direction. */
@Composable
private fun OverlaySettings(overlayAllowed: Boolean) {
    val context = LocalContext.current
    SwitchCard(R.string.settings_overlay_title, R.string.settings_overlay_body, overlayAllowed) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
    }
}

@Composable
private fun DefaultAppSettings(isDefaultDialer: Boolean, onRequestDefault: () -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_default_held),
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (isDefaultDialer) {
                Text("✓", fontSize = 20.sp, color = CallGreen)
            } else {
                TextButton(onClick = onRequestDefault) {
                    Text(stringResource(R.string.setup_default_action))
                }
            }
        }
        if (isDefaultDialer) TelLinksRow()
    }
}

/** tel: links from other apps: the phone role doesn't cover them, so they are shown apart. */
@Composable
private fun TelLinksRow() {
    val context = LocalContext.current
    var holder by remember { mutableStateOf(TelLinks.holder(context)) }
    LifecycleResumeEffect(Unit) {
        holder = TelLinks.holder(context)
        onPauseOrDispose {}
    }
    Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.tel_links_row), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            val note = when (val h = holder) {
                TelLinks.Holder.Us -> null
                TelLinks.Holder.Nobody -> stringResource(R.string.tel_links_asks)
                is TelLinks.Holder.Other -> stringResource(R.string.tel_links_other, TelLinks.label(context, h.packageName))
            }
            if (note != null) Text(note, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (holder == TelLinks.Holder.Us) {
            Text("✓", fontSize = 20.sp, color = CallGreen)
        } else {
            TextButton(onClick = { TelLinks.choose(context) }) { Text(stringResource(R.string.tel_links_action)) }
        }
    }
}

/**
 * How long the app keeps calls on the phone, and how much room it takes. A shorter time deletes
 * what falls outside it, so that is asked first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistorySettings() {
    val context = LocalContext.current
    val app = context.container
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    var measured by remember { mutableIntStateOf(0) }
    var asking by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(measured) {
        val (phone, whatsApp) = app.history.counts()
        usage = (phone + whatsApp) to databaseSize(context)
    }
    fun choose(days: Int) {
        scope.launch {
            app.saveHistoryDays(days)
            measured++
        }
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.settings_history_title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.settings_history_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CallHistory.KEEP_CHOICES.forEach { days ->
                    FilterChip(
                        selected = app.historyDays == days,
                        onClick = { if (days < app.historyDays) asking = days else if (days != app.historyDays) choose(days) },
                        label = { Text(keepLabel(days)) },
                    )
                }
            }
            usage?.let { (calls, bytes) ->
                Text(
                    pluralStringResource(R.plurals.settings_history_used, calls, calls, Formatter.formatShortFileSize(context, bytes)),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    val days = asking ?: return
    ConfirmDialog(
        message = stringResource(R.string.settings_history_shrink, keepLabel(days)),
        icon = Icons.Outlined.Delete,
        action = stringResource(R.string.action_delete),
        onConfirm = {
            asking = null
            choose(days)
        },
        onDismiss = { asking = null },
    )
}

@Composable
private fun keepLabel(days: Int): String = when (days) {
    7 -> stringResource(R.string.settings_history_week)
    30 -> stringResource(R.string.settings_history_month)
    else -> pluralStringResource(R.plurals.settings_history_years, days / 365, days / 365)
}

/** The app's database with its journal files: tags, the spam cache and the calls kept. */
private suspend fun databaseSize(context: Context): Long = withContext(Dispatchers.IO) {
    val db = context.getDatabasePath(LocalDatabase.FILE)
    listOf("", "-wal", "-shm", "-journal").sumOf { File(db.path + it).length() }
}

/**
 * What the phone's Google backup takes along, and when it last ran. Apps can't ask whether backup is
 * on (only system apps may), so the date of the last backup stands for it.
 */
@Composable
private fun BackupSettings() {
    val context = LocalContext.current
    var last by remember { mutableStateOf(BackupWatch.lastBackup(context)) }
    LifecycleResumeEffect(Unit) {
        last = BackupWatch.lastBackup(context)
        onPauseOrDispose { }
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.settings_backup_title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            val when_ = last
            if (when_ != null) {
                val date = android.text.format.DateUtils.formatDateTime(
                    context, when_,
                    android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_SHOW_TIME or android.text.format.DateUtils.FORMAT_SHOW_YEAR,
                )
                Text(stringResource(R.string.settings_backup_last, date), fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
            } else {
                Text(stringResource(R.string.settings_backup_never), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(stringResource(R.string.settings_backup_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The swipe-up menu on the home screen's favourites and most talked. */
@Composable
private fun QuickMenuSettings() {
    val app = LocalContext.current.container
    Card {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_quick_menu_title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.settings_quick_menu_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(6.dp))
            Switch(checked = app.quickMenuEnabled, onCheckedChange = app::setQuickMenu)
        }
    }
}

/** The app's own switch. When the system has animations off it wins, and the switch says so. */
@Composable
private fun AnimationSettings() {
    val context = LocalContext.current
    val app = context.container
    val system = remember { systemAnimationsOn(context) }
    Card {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_animations_title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(if (system) R.string.settings_animations_body else R.string.settings_animations_system_off),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = system && app.animationsEnabled,
                enabled = system,
                onCheckedChange = app::setAnimations,
            )
        }
    }
}

/**
 * WhatsApp calls in the recents. Turning it on takes the user to Android's notification access page
 * for this app; the switch shows on only once that access is there too.
 */
@Composable
private fun WhatsAppCallSettings() {
    val context = LocalContext.current
    val app = context.container
    var access by remember { mutableStateOf(WhatsAppCallListener.hasAccess(context)) }
    var heldBack by remember { mutableStateOf(heldBack(context)) }
    LifecycleResumeEffect(Unit) {
        access = WhatsAppCallListener.hasAccess(context)
        heldBack = heldBack(context)
        onPauseOrDispose { }
    }
    Card {
      Column {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_whatsapp_title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.settings_whatsapp_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = app.whatsAppCallsEnabled && access,
                onCheckedChange = { on ->
                    app.saveWhatsAppCalls(on)
                    if (on && !access) {
                        val listener = ComponentName(context, WhatsAppCallListener::class.java)
                        context.startActivity(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener.flattenToString())
                            } else {
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            },
                        )
                    }
                },
            )
        }
        // The pill rides on the WhatsApp calls: it can be on only while they are.
        val calls = app.whatsAppCallsEnabled && access
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_whatsapp_pill_title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.settings_whatsapp_pill_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = calls && app.whatsAppPillEnabled,
                enabled = calls,
                onCheckedChange = app::saveWhatsAppPill,
            )
        }
        if (calls && (app.whatsAppLate || heldBack)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                Text(stringResource(R.string.settings_whatsapp_late), fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = {
                    app.forgetWhatsAppLate()
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
                    )
                }) { Text(stringResource(R.string.settings_whatsapp_late_open)) }
            }
        }
      }
    }
}

/**
 * The user restricted the app's background use, so the phone stops it between calls and WhatsApp's
 * notifications reach nobody. Whether battery optimization is off can't be told the same way on every
 * phone (Samsung keeps "Unrestricted" elsewhere), so late notices tell the rest.
 */
private fun heldBack(context: Context): Boolean =
    context.getSystemService(ActivityManager::class.java).isBackgroundRestricted

/** The texts offered when a call is turned down with a message: edit, remove, add. */
@Composable
private fun QuickReplySettings() {
    val app = LocalContext.current.container
    var editing by remember { mutableStateOf<Int?>(null) }
    Card {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.settings_replies_title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.settings_replies_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            app.quickReplies.forEachIndexed { index, reply ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        reply,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).clickable { editing = index }.padding(vertical = 8.dp),
                    )
                    IconButton(onClick = { app.saveQuickReplies(app.quickReplies.filterIndexed { i, _ -> i != index }) }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_remove_row), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            TextButton(onClick = { editing = app.quickReplies.size }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.reply_add))
            }
        }
    }
    val index = editing ?: return
    var text by remember(index) { mutableStateOf(app.quickReplies.getOrNull(index).orEmpty()) }
    AlertDialog(
        onDismissRequest = { editing = null },
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
                val list = app.quickReplies.toMutableList()
                if (index < list.size) list[index] = text else list += text
                app.saveQuickReplies(list)
                editing = null
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Where a swipe on a list row may start: the shaded strips at the sides belong to Android's back gesture. */
@Composable
private fun SwipeSettings() {
    val app = LocalContext.current.container
    val edge = app.swipeEdgeDp
    Card {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.settings_swipe_title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.settings_swipe_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LabeledSlider(
                label = R.string.settings_swipe_edge,
                value = edge.toFloat(),
                shown = stringResource(R.string.settings_swipe_edge_value, edge),
                range = 0f..MAX_SWIPE_EDGE.toFloat(),
                steps = MAX_SWIPE_EDGE / 8 - 1,
            ) { app.setSwipeEdge((it / 8).roundToInt() * 8) }
            EdgePreview(edge)
            Text(stringResource(R.string.settings_swipe_message), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SwipeMessage.entries.forEach { choice ->
                    FilterChip(
                        selected = app.swipeMessage == choice,
                        onClick = { app.saveSwipeMessage(choice) },
                        label = {
                            Text(stringResource(if (choice == SwipeMessage.WhatsApp) R.string.whatsapp else R.string.message_sms))
                        },
                    )
                }
            }
        }
    }
}

/** The screen's width as a bar, with the side strips a swipe can't start in drawn to scale. */
@Composable
private fun EdgePreview(edgeDp: Int) {
    val screenDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value }.coerceAtLeast(1f)
    val strip = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        val side = maxWidth * (edgeDp.toFloat() / screenDp)
        Box(Modifier.align(Alignment.CenterStart).width(side).fillMaxHeight().background(strip))
        Box(Modifier.align(Alignment.CenterEnd).width(side).fillMaxHeight().background(strip))
        Text(
            stringResource(R.string.settings_swipe_preview),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun DebugLogSettings(isDefaultDialer: Boolean) {
    val context = LocalContext.current
    val app = context.container
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(app.debugLogEnabled) }
    var size by remember { mutableLongStateOf(DebugLog.size()) }
    val shareTitle = stringResource(R.string.settings_log_share)
    Card {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_log_title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.settings_log_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(6.dp))
                Switch(checked = enabled, onCheckedChange = {
                    enabled = it
                    app.debugLogEnabled = it
                    DebugLog.i("App", "debug log ${if (it) "on" else "off"}")
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_log_size, Formatter.formatShortFileSize(context, size)),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    DebugLog.clear()
                    size = 0
                }) { Text(stringResource(R.string.settings_log_clear)) }
                TextButton(onClick = {
                    scope.launch {
                        val header = listOf(
                            "App: ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})",
                            "Install ID: ${app.deviceId}",
                            "Default phone app: $isDefaultDialer",
                            "Cloud lookup: ${app.spamLookupEnabled}, silent face down: ${app.silentWhenFaceDown}, animations: ${app.animationsEnabled}",
                            "Overlay allowed: ${Settings.canDrawOverlays(context)}, system animations: ${systemAnimationsOn(context)}",
                            "Pill: ${PillStyle.load(context)}",
                        )
                        val file = withContext(Dispatchers.IO) { DebugLog.export(context, header) }
                        size = DebugLog.size()
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", file)
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .putExtra(Intent.EXTRA_SUBJECT, file.name)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    }
                }) { Text(stringResource(R.string.settings_log_share)) }
            }
        }
    }
}

@Composable
private fun About() {
    val context = LocalContext.current
    Card {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.about_title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "${stringResource(R.string.app_name)} · ${stringResource(R.string.about_version, BuildConfig.VERSION_NAME)}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinkRow(stringResource(R.string.about_developer), "yesil.dev", "https://yesil.dev")
            LinkRow(stringResource(R.string.about_source), "github.com/kyesil/callsoul", SOURCE_URL)
            LinkRow(stringResource(R.string.about_license), "GPL-3.0", "$SOURCE_URL/blob/main/LICENSE")
            Text(stringResource(R.string.about_open_source), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // A missing feature: a review on Play, or an issue on GitHub.
            Text(stringResource(R.string.about_feedback), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinkRow("Google Play", stringResource(R.string.about_review), PLAY_URL)
            LinkRow("GitHub", stringResource(R.string.about_issue), "$SOURCE_URL/issues/new")
            Text(stringResource(R.string.about_coauthor, "Claude Opus 5.5"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Where the app's code is published, under the GPL. */
private const val SOURCE_URL = "https://github.com/kyesil/callsoul"

/** The app's page on Google Play, where reviews are written. */
private const val PLAY_URL = "https://play.google.com/store/apps/details?id=dev.yesil.callsoul"

/** A label and an underlined address that opens in the browser. */
@Composable
private fun LinkRow(label: String, text: String, url: String) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                .padding(vertical = 4.dp),
        )
    }
}

/** Shape of the floating call pill. Every change shows the pill for a few seconds as a preview. */
@Composable
private fun PillSettings(overlayAllowed: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var style by remember { mutableStateOf(PillStyle.load(context)) }
    val preview = remember { CallOverlay(context, onOpen = {}, onAnswer = {}, onHangUp = {}) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { preview.hide() } }

    fun showPreview() {
        previewJob?.cancel()
        previewJob = scope.launch {
            preview.hide()
            preview.show(System.currentTimeMillis() - 83_000)
            delay(4000)
            preview.hide()
        }
    }

    fun update(next: PillStyle) {
        style = next
        PillStyle.save(context, next)
    }

    val app = context.container
    Card {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_pill_title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_pill_rings_title), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.settings_pill_rings_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = app.ringInPill && overlayAllowed,
                    enabled = overlayAllowed,
                    onCheckedChange = app::saveRingInPill,
                )
            }
            Choice(
                R.string.settings_pill_orientation,
                listOf(stringResource(R.string.pill_horizontal), stringResource(R.string.pill_vertical)),
                selected = if (style.vertical) 1 else 0,
            ) { update(style.copy(vertical = it == 1)) }
            val locale = LocalConfiguration.current.locales[0]
            val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
            LabeledSlider(
                label = R.string.settings_pill_size,
                value = style.scale,
                shown = percent.format(style.scale.toDouble()),
                range = PillStyle.MIN_SCALE..PillStyle.MAX_SCALE,
                steps = 9,
            ) { update(style.copy(scale = (it * 10).roundToInt() / 10f)) }
            Choice(
                R.string.settings_pill_order,
                listOf(stringResource(R.string.pill_speaker_first), stringResource(R.string.pill_end_first)),
                selected = if (style.endFirst) 1 else 0,
            ) { update(style.copy(endFirst = it == 1)) }
            LabeledSlider(
                label = R.string.settings_pill_opacity,
                value = style.opacity,
                shown = percent.format(style.opacity.toDouble()),
                range = PillStyle.MIN_OPACITY..1f,
                steps = 8,
            ) { update(style.copy(opacity = (it * 10).roundToInt() / 10f)) }
            PillPreview(style)
            if (overlayAllowed) {
                IconButton(onClick = ::showPreview) {
                    Icon(Icons.Outlined.PlayCircle, stringResource(R.string.settings_pill_preview), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                }
            } else {
                Text(
                    stringResource(R.string.settings_pill_needs_permission),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: Int,
    value: Float,
    shown: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(label), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(shown, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

/** A drawing of the pill as the current choices make it, on a dark strip like a wallpaper. */
@Composable
private fun PillPreview(style: PillStyle) {
    val s = style.scale
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2B3036), Color(0xFF14181C))))
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp * s),
            color = Color(0xF01E2328),
            shadowElevation = 6.dp,
            modifier = Modifier.alpha(style.opacity),
        ) {
            val speaker: @Composable () -> Unit = { PreviewCircle(Icons.AutoMirrored.Filled.VolumeUp, Color(0x40FFFFFF), s) }
            val end: @Composable () -> Unit = { PreviewCircle(Icons.Filled.CallEnd, EndRed, s) }
            val (first, last) = if (style.endFirst) end to speaker else speaker to end
            val timer: @Composable () -> Unit = {
                Text("01:25", color = Color.White, fontSize = 14.sp * s, modifier = Modifier.padding(8.dp * s))
            }
            if (style.vertical) {
                Column(Modifier.padding(4.dp * s), horizontalAlignment = Alignment.CenterHorizontally) { first(); timer(); last() }
            } else {
                Row(Modifier.padding(4.dp * s), verticalAlignment = Alignment.CenterVertically) { first(); timer(); last() }
            }
        }
    }
}

@Composable
private fun PreviewCircle(icon: ImageVector, color: Color, scale: Float) {
    Box(
        Modifier.size(36.dp * scale).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp * scale))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(label: Int, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(stringResource(label), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(option, maxLines = 1, fontSize = 13.sp) },
                )
            }
        }
    }
}
