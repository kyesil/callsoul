package com.bettercallsoul.ui

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.annotation.PluralsRes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettercallsoul.R
import com.bettercallsoul.calllog.ContactAccount
import com.bettercallsoul.calllog.ContactExport
import com.bettercallsoul.calllog.ContactStore
import com.bettercallsoul.calllog.MovePlan
import com.bettercallsoul.container
import com.bettercallsoul.myCardAccount
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An address book in a picker, with how many contacts it holds (null while counting); [store] null for all of them. */
internal data class StoreOption(val store: ContactStore?, val label: String, val detail: String?, val count: Int?) {
    val account: ContactAccount? get() = store?.account
}

/**
 * Moves the contacts of one address book (the phone, a Google, Samsung, Outlook or iCloud account)
 * into another. Those the other already has under the same number or e-mail are merged into it, the
 * details from where they come taking precedence. My card, where it is a contact of its own, stays put.
 */
@Composable
fun MoveContactsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.container
    val moves = app.moves
    val run = moves.run
    val scope = rememberCoroutineScope()
    val myCard by app.profileRow.collectAsStateWithLifecycle()
    var options by remember { mutableStateOf(emptyList<StoreOption>()) }
    var phoneAllowed by remember { mutableStateOf(true) }
    var from by remember { mutableStateOf<ContactAccount?>(null) }
    var to by remember { mutableStateOf<ContactAccount?>(null) }
    var checking by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<MovePlan?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    // A hidden account picked in Android's chooser: where it goes once the list has it.
    var granted by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val choose = rememberAccountChooser { type ->
        granted = granted?.copy(first = type)
        reload++
    }

    fun canReceive(option: StoreOption) = option.store?.isPhone != true || phoneAllowed

    // Counted again once a run is over.
    LaunchedEffect(run?.finished, myCard?.id, reload) {
        if (run?.finished == false) return@LaunchedEffect
        val keep = setOfNotNull(myCard?.id)
        val stores = withContext(Dispatchers.IO) { runCatching { app.contacts.stores() }.getOrDefault(emptyList()) }
        phoneAllowed = runCatching { app.editor.localAllowed() }.getOrDefault(true)
        if (options.isEmpty()) options = stores.map { StoreOption(it, it.label, it.detail, null) }
        options = withContext(Dispatchers.IO) {
            stores.map { StoreOption(it, it.label, it.detail, runCatching { app.editor.rawIdsIn(it.account, keep).size }.getOrNull()) }
        }
        // At first the phone into the first account that can take its contacts.
        granted?.let { (type, asSource) ->
            options.firstOrNull { it.account?.type == type && it.store?.needsAccess == false }?.account?.let { if (asSource) from = it else to = it }
            granted = null
        }
        if (options.none { it.account == from }) {
            from = (options.firstOrNull { it.store?.isPhone == true && (it.count ?: 0) > 0 } ?: options.firstOrNull { (it.count ?: 0) > 0 } ?: options.firstOrNull())?.account
        }
        if (to == from || options.none { it.account == to && canReceive(it) }) {
            to = options.firstOrNull { it.account != from && canReceive(it) }?.account
        }
    }

    val source = options.firstOrNull { it.account == from }
    val target = options.firstOrNull { it.account == to }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        DetailToolbar(onBack)
        LargeTitle(stringResource(R.string.move_title), null)
        Note(stringResource(R.string.move_note))

        if (run != null) {
            Card {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(stringResource(R.string.move_running), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { if (run.total == 0) 1f else run.done.toFloat() / run.total }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    val line = if (!run.finished) {
                        boldFormat(stringResource(R.string.move_progress), run.done, run.total)
                    } else {
                        val moved = run.total - run.failed - run.merged
                        val parts = buildList {
                            if (moved > 0 || run.merged == 0) add(boldPlural(R.plurals.move_moved, moved, moved))
                            if (run.merged > 0) add(boldPlural(R.plurals.move_merged, run.merged, run.merged))
                            if (run.failed > 0) add(boldPlural(R.plurals.move_failed, run.failed, run.failed))
                        }
                        buildAnnotatedString {
                            parts.forEachIndexed { index, part ->
                                if (index > 0) append(", ")
                                append(part)
                            }
                        }
                    }
                    Text(line, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (run.finished) {
                        TextButton(onClick = moves::dismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_done)) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Card {
            StorePicker(stringResource(R.string.move_from), options, source, onPick = { option ->
                if (option.store?.needsAccess == true) {
                    granted = "" to true
                    choose(option.store.account.type!!)
                    return@StorePicker
                }
                from = option.account
                if (to == from) to = options.firstOrNull { it.account != from && canReceive(it) }?.account
            })
            Box(Modifier.fillMaxWidth()) {
                // The line stops short of the swap button rather than running under it.
                HorizontalDivider(Modifier.align(Alignment.Center).padding(start = 16.dp, end = 64.dp))
                IconButton(
                    enabled = source != null && target != null && canReceive(source),
                    onClick = { from = to.also { to = from } },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                ) { Icon(Icons.Filled.SwapVert, stringResource(R.string.move_swap)) }
            }
            StorePicker(
                stringResource(R.string.move_to), options.filter { it.account != from }, target,
                onPick = { option ->
                    if (option.store?.needsAccess == true) {
                        granted = "" to false
                        choose(option.store.account.type!!)
                    } else {
                        to = option.account
                    }
                },
                isEnabled = ::canReceive,
            )
            if (!phoneAllowed && source?.store?.isPhone != true) {
                Text(
                    stringResource(R.string.move_phone_not_allowed), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (checking) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp).height(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.move_checking), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Button(
                    enabled = !moves.busy && !checking && source != null && target != null && (source.count ?: 0) > 0 &&
                        source.store?.needsAccess != true && target.store?.needsAccess != true,
                    onClick = {
                        val fromAccount = source?.account ?: return@Button
                        val toAccount = target?.account ?: return@Button
                        checking = true
                        scope.launch {
                            plan = runCatching { app.editor.plan(fromAccount, toAccount, setOfNotNull(myCard?.id)) }.getOrNull()
                            checking = false
                        }
                    },
                ) { Text(stringResource(R.string.move_action)) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    plan?.let { ready ->
        val count = ready.rawIds.size
        val fromLabel = source?.label.orEmpty()
        val toLabel = target?.label.orEmpty()
        val question = boldPlural(R.plurals.move_question, count, count, fromLabel, toLabel)
        val matches = ready.matches.size
        val message = if (matches == 0) question else question + AnnotatedString("\n\n") + boldPlural(R.plurals.move_matches, matches, matches, toLabel, fromLabel)
        ConfirmDialog(
            message = message,
            icon = Icons.Outlined.SwapHoriz,
            action = stringResource(R.string.move_action),
            destructive = false,
            onConfirm = {
                plan = null
                val into = if (target?.store?.isPhone == true) myCardAccount(app) else ready.to
                moves.move(ready, into)
            },
            onDismiss = { plan = null },
        )
    }
}

/** Writes the contacts of one address book, or all of them, to a vCard or CSV file the user picks. */
@Composable
fun ExportContactsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.container
    val exports = app.exports
    val run = exports.run
    val allLabel = stringResource(R.string.export_all)
    var options by remember { mutableStateOf(emptyList<StoreOption>()) }
    var chosen by remember { mutableStateOf<ContactAccount?>(null) }
    var format by rememberSaveable { mutableStateOf(ContactExport.Format.Vcf) }
    var reload by remember { mutableIntStateOf(0) }
    var granted by remember { mutableStateOf<String?>(null) }
    val choose = rememberAccountChooser { type ->
        granted = type
        reload++
    }

    LaunchedEffect(reload) {
        val stores = withContext(Dispatchers.IO) { runCatching { app.contacts.stores() }.getOrDefault(emptyList()) }
        options = listOf(StoreOption(null, allLabel, null, null)) + stores.map { StoreOption(it, it.label, it.detail, null) }
        options = withContext(Dispatchers.IO) {
            options.map { option -> option.copy(count = runCatching { exports.export.contactIds(option.account).size }.getOrNull()) }
        }
        granted?.let { type ->
            options.firstOrNull { it.account?.type == type && it.store?.needsAccess == false }?.let { chosen = it.account }
            granted = null
        }
    }
    val picked = options.firstOrNull { it.account == chosen }

    fun fileName(extension: String): String {
        val what = picked?.store?.let { "-" + it.label.lowercase().replace(Regex("[^\\p{L}\\p{N}@._-]+"), "-") }.orEmpty()
        return "contacts$what-${LocalDate.now()}.$extension"
    }
    val vcf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ContactExport.Format.Vcf.mimeType)) { uri ->
        uri?.let { exports.start(ContactExport.Format.Vcf, chosen, it) }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ContactExport.Format.Csv.mimeType)) { uri ->
        uri?.let { exports.start(ContactExport.Format.Csv, chosen, it) }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        DetailToolbar(onBack)
        LargeTitle(stringResource(R.string.export_title), null)
        Note(stringResource(R.string.export_note))

        if (run != null) {
            Card {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(stringResource(R.string.export_running), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { if (run.total == 0) 0f else run.done.toFloat() / run.total }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    val line = when {
                        !run.finished -> stringResource(R.string.move_progress, run.done, run.total)
                        run.failed -> stringResource(R.string.export_failed)
                        else -> pluralStringResource(R.plurals.export_done, run.written, run.written)
                    }
                    Text(line, fontSize = 14.sp, color = if (run.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (run.finished) {
                        TextButton(onClick = exports::dismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_done)) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Card {
            StorePicker(stringResource(R.string.export_which), options, picked, onPick = { option ->
                if (option.store?.needsAccess == true) choose(option.store.account.type!!) else chosen = option.account
            })
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                val formats = listOf(ContactExport.Format.Vcf to R.string.export_vcf, ContactExport.Format.Csv to R.string.export_csv)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    formats.forEachIndexed { index, (option, label) ->
                        SegmentedButton(
                            selected = format == option,
                            onClick = { format = option },
                            shape = SegmentedButtonDefaults.itemShape(index, formats.size),
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (format == ContactExport.Format.Vcf) R.string.export_vcf_hint else R.string.export_csv_hint),
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    enabled = !exports.busy && (picked?.count ?: 0) > 0,
                    onClick = {
                        when (format) {
                            ContactExport.Format.Vcf -> vcf.launch(fileName(format.extension))
                            ContactExport.Format.Csv -> csv.launch(fileName(format.extension))
                        }
                    },
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                ) { Text(stringResource(R.string.export_action)) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** A row naming the chosen address book and its count; a tap lists the others. */
@Composable
internal fun StorePicker(
    title: String,
    options: List<StoreOption>,
    chosen: StoreOption?,
    onPick: (StoreOption) -> Unit,
    isEnabled: (StoreOption) -> Boolean = { true },
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = options.isNotEmpty()) { open = true }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Text(chosen?.label ?: "—", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (chosen != null) Text(optionLine(chosen), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(optionLine(option), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    trailingIcon = if (option.account == chosen?.account && chosen != null) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    enabled = isEnabled(option),
                    onClick = {
                        open = false
                        onPick(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun optionLine(option: StoreOption): String {
    if (option.store?.needsAccess == true) return stringResource(R.string.move_allow_access)
    val count = option.count?.let { pluralStringResource(R.plurals.move_count, it, it) } ?: stringResource(R.string.move_counting)
    return listOfNotNull(option.detail, count).joinToString(" · ")
}

/**
 * Android's own account chooser for one kind of account; picking one there lets the app see it from
 * then on. [onPicked] gets the kind once the user picked an account.
 */
@Composable
private fun rememberAccountChooser(onPicked: (String) -> Unit): (String) -> Unit {
    var asked by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val type = asked
        asked = null
        if (result.resultCode == Activity.RESULT_OK && type != null) onPicked(type)
    }
    return { type ->
        asked = type
        launcher.launch(AccountManager.newChooseAccountIntent(null, null, arrayOf(type), null, null, null, null))
    }
}

/** A plural with what fills it in bold: how many, from where, to where. */
@Composable
private fun boldPlural(@PluralsRes id: Int, count: Int, vararg args: Any): AnnotatedString =
    boldFormat(LocalContext.current.resources.getQuantityString(id, count), *args)

/** [template] with its %1$d / %2$s places filled by [args], each in bold. */
internal fun boldFormat(template: String, vararg args: Any): AnnotatedString = buildAnnotatedString {
    var at = 0
    Regex("%(\\d+)\\$[sd]|%[sd]").findAll(template).forEachIndexed { order, match ->
        append(template.substring(at, match.range.first))
        val arg = args.getOrNull(match.groupValues[1].toIntOrNull()?.minus(1) ?: order)
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(if (arg is Int) "%d".format(arg) else arg?.toString().orEmpty()) }
        at = match.range.last + 1
    }
    append(template.substring(at))
}
