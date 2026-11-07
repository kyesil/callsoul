package com.bettercallsoul.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettercallsoul.R
import com.bettercallsoul.calllog.CallType
import com.bettercallsoul.calllog.KnownCaller
import com.bettercallsoul.calllog.toStatCall
import com.bettercallsoul.container
import com.bettercallsoul.stats.CallStatistics
import com.bettercallsoul.stats.PersonTotal
import com.bettercallsoul.stats.StatPerson
import com.bettercallsoul.stats.StatSummary
import com.bettercallsoul.telecom.CallerDirectory
import java.util.concurrent.TimeUnit

private enum class StatPeriod(val label: Int, val days: Long) {
    Week(R.string.stats_week, 7),
    Month(R.string.stats_month, 30),
    Year(R.string.stats_year, 365),
}

/** Talk time and who with, over the last week, month or year. Reads the app's own copy of the calls. */
@Composable
fun StatsScreen(onOpen: (Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.container
    val records by remember { app.history.observe() }.collectAsStateWithLifecycle(emptyList())
    val tags by remember { app.store.observeTags() }.collectAsStateWithLifecycle(emptyList())
    val labels = remember(tags) { tags.associate { it.number to it.label } }
    var callers by remember { mutableStateOf<Map<String, KnownCaller>?>(null) }
    var period by rememberSaveable { mutableStateOf(StatPeriod.Week) }
    LaunchedEffect(Unit) { app.history.sync() }
    LaunchedEffect(records) { callers = app.callLog.knownCallers(records.mapTo(HashSet()) { it.number }) }

    val known = callers
    val now = remember(records) { System.currentTimeMillis() }
    val since = now - TimeUnit.DAYS.toMillis(period.days)
    val summary = remember(records, known, period) {
        known?.let { people ->
            CallStatistics.summarize(records.map { it.toStatCall() }, since) { number ->
                people[number]?.let { StatPerson(it.name, saved = true) } ?: StatPerson(CallerDirectory.keyOf(number), saved = false)
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ToolbarRow {
            ToolbarIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onBack)
            Text(
                stringResource(R.string.stats_title),
                fontSize = 19.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
        }
        PrimaryTabRow(selectedTabIndex = period.ordinal, containerColor = MaterialTheme.colorScheme.background) {
            StatPeriod.entries.forEach { option ->
                Tab(
                    selected = period == option,
                    onClick = { period = option },
                    text = { Text(stringResource(option.label)) },
                )
            }
        }
        if (summary != null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Headline(summary, DateUtils.formatDateRange(context, since, now, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH))
                if (summary.calls == 0) {
                    Text(
                        stringResource(R.string.stats_empty),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                } else {
                    Tiles(summary)
                    Directions(summary)
                    if (summary.top.isNotEmpty()) {
                        DayHeader(stringResource(R.string.section_most_talked))
                        Card {
                            val longest = summary.top.first().seconds
                            summary.top.forEach { person ->
                                TopRow(person, longest, known?.get(person.number), labels[CallerDirectory.keyOf(person.number)], onOpen)
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.stats_note),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
    }
}

/** "2 sa 15 dk", "12 dk", "45 sn". */
@Composable
internal fun talkTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    return when {
        hours > 0 -> stringResource(R.string.duration_hm, hours, minutes)
        minutes > 0 -> stringResource(R.string.duration_m, minutes)
        else -> stringResource(R.string.duration_s, seconds)
    }
}

/** The number the page leads with. */
@Composable
private fun Headline(summary: StatSummary, range: String) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.stats_total), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(talkTime(summary.talkSeconds), fontSize = 44.sp, lineHeight = 52.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(range, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Tiles(summary: StatSummary) {
    Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(Icons.Outlined.People, summary.people.toString(), stringResource(R.string.stats_people), Modifier.weight(1f))
            StatTile(Icons.Outlined.Call, summary.calls.toString(), stringResource(R.string.stats_calls), Modifier.weight(1f))
        }
        StatTile(
            Icons.Outlined.PersonOff,
            talkTime(summary.unsavedSeconds),
            stringResource(R.string.stats_unsaved),
            Modifier.fillMaxWidth(),
            note = pluralStringResource(R.plurals.stats_unsaved_numbers, summary.unsavedPeople, summary.unsavedPeople),
        )
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier, note: String? = null) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (note != null) {
            Text(note, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
        }
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    }
}

/** Incoming, outgoing and missed, each with the same arrow the call lists use. */
@Composable
private fun Directions(summary: StatSummary) {
    Card(Modifier.padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Direction(CallType.Incoming, summary.incoming, R.string.filter_incoming, Modifier.weight(1f))
            Direction(CallType.Outgoing, summary.outgoing, R.string.filter_outgoing, Modifier.weight(1f))
            Direction(CallType.Missed, summary.missed, R.string.filter_missed, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Direction(type: CallType, count: Int, label: Int, modifier: Modifier) {
    val (icon, tint) = typeIcon(type)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(count.toString(), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(stringResource(label), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A ranked bar: every bar starts at the same place and the longest talk fills the width. */
@Composable
private fun TopRow(person: PersonTotal, longest: Long, caller: KnownCaller?, label: String?, onOpen: (Route) -> Unit) {
    val name = caller?.name ?: label ?: person.number.ifBlank { stringResource(R.string.stats_hidden_number) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = person.number.isNotBlank()) { onOpen(Route.Number(person.number)) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name, caller?.photoUri, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .fillMaxWidth((person.seconds.toFloat() / longest).coerceIn(0.02f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(talkTime(person.seconds), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                pluralStringResource(R.plurals.stats_call_count, person.calls, person.calls),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
