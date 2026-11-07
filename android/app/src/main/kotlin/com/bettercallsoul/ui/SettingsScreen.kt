package com.bettercallsoul.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bettercallsoul.R
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.bettercallsoul.container

@Composable
fun SettingsScreen(isDefaultDialer: Boolean, onRequestDefault: () -> Unit, onBack: () -> Unit) {
    val app = LocalContext.current.container
    val context = LocalContext.current
    var lookup by remember { mutableStateOf(app.spamLookupEnabled) }
    var faceDown by remember { mutableStateOf(app.silentWhenFaceDown) }
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    LifecycleResumeEffect(Unit) {
        overlayAllowed = Settings.canDrawOverlays(context)
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        DetailToolbar(onBack)
        LargeTitle(stringResource(R.string.action_settings), null)
        Card {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_lookup_title),
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.settings_lookup_body),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = lookup,
                    onCheckedChange = {
                        lookup = it
                        app.spamLookupEnabled = it
                    },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Card {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_face_down_title),
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.settings_face_down_body),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = faceDown,
                    onCheckedChange = {
                        faceDown = it
                        app.silentWhenFaceDown = it
                    },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Card {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_overlay_title),
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.settings_overlay_body),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(6.dp))
                // Only the system page can change this, in either direction.
                Switch(
                    checked = overlayAllowed,
                    onCheckedChange = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()),
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        WhatsAppCallSettings()
        Spacer(Modifier.height(6.dp))
        AnimationSettings()
        Spacer(Modifier.height(6.dp))
        SwipeSettings()
        Spacer(Modifier.height(6.dp))
        QuickReplySettings()
        Spacer(Modifier.height(6.dp))
        PillSettings(overlayAllowed)
        Spacer(Modifier.height(6.dp))
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
        }
        Spacer(Modifier.height(6.dp))
        DebugLogSettings(isDefaultDialer)
        Spacer(Modifier.height(6.dp))
        About()
        Spacer(Modifier.height(24.dp))
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.about_developer), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    "yesil.dev",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, "https://yesil.dev".toUri())) }
                        .padding(vertical = 4.dp),
                )
            }
            Text(stringResource(R.string.about_coauthor, "Claude Opus 5.5"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
