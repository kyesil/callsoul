package com.bettercallsoul.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.JoinInner
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.bettercallsoul.R
import com.bettercallsoul.calllog.DuplicateContacts
import com.bettercallsoul.calllog.DuplicateGroup
import com.bettercallsoul.calllog.DuplicatePerson
import com.bettercallsoul.calllog.DuplicateReason
import com.bettercallsoul.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Google Contacts' own "Merge & fix", which joins copies in the account itself. */
private const val GOOGLE_MERGE = "https://contacts.google.com/suggestions"

/**
 * Contacts saved more than once: the same number, or a contact without a number named like another.
 * Merging links the copies on this phone; when the contacts live in Google, Google's own tool is offered too.
 */
@Composable
fun DuplicatesScreen(onOpen: (Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.container
    val scope = rememberCoroutineScope()
    val finder = remember { DuplicateContacts(context.contentResolver) }
    var groups by remember { mutableStateOf<List<DuplicateGroup>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var askAll by remember { mutableStateOf(false) }
    var google by remember { mutableStateOf(false) }
    LaunchedEffect(reload) {
        val apart = app.keptApart
        groups = runCatching { finder.find() }.getOrDefault(emptyList()).filter { it.key !in apart }
        google = withContext(Dispatchers.IO) {
            runCatching { app.editor.systemDefault()?.isGoogle ?: app.editor.accounts().any { it.isGoogle } }.getOrDefault(false)
        }
    }

    fun merge(chosen: List<DuplicateGroup>) {
        scope.launch {
            chosen.forEach { finder.link(it) }
            reload++
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        DetailToolbar(onBack)
        LargeTitle(stringResource(R.string.dup_title), null)
        val shown = groups
        if (shown == null) {
            Note(stringResource(R.string.dup_searching))
            return@Column
        }
        Note(stringResource(R.string.dup_note))
        if (google) {
            Card {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Text(stringResource(R.string.dup_google_title), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.dup_google_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(
                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, GOOGLE_MERGE.toUri())) } },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(stringResource(R.string.dup_google_open)) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (shown.isEmpty()) {
            Note(stringResource(R.string.dup_none))
        } else {
            if (shown.size > 1) {
                TextButton(onClick = { askAll = true }, modifier = Modifier.align(Alignment.End).padding(end = 10.dp)) {
                    Text(stringResource(R.string.dup_merge_all))
                }
            }
            shown.forEach { group ->
                DuplicateCard(
                    group,
                    onPerson = { onOpen(Route.Contact(it.contactId)) },
                    onKeepApart = {
                        app.keptApart = app.keptApart + group.key
                        groups = shown - group
                    },
                    onMerge = { merge(listOf(group)) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (askAll) {
        val all = groups.orEmpty()
        ConfirmDialog(
            message = pluralStringResource(R.plurals.dup_merge_all_question, all.size, all.size),
            icon = Icons.Outlined.JoinInner,
            action = stringResource(R.string.dup_merge),
            destructive = false,
            onConfirm = {
                askAll = false
                merge(all)
            },
            onDismiss = { askAll = false },
        )
    }
}

@Composable
private fun DuplicateCard(group: DuplicateGroup, onPerson: (DuplicatePerson) -> Unit, onKeepApart: () -> Unit, onMerge: () -> Unit) {
    val phone = stringResource(R.string.storage_phone)
    Card {
        Text(
            stringResource(if (group.reason == DuplicateReason.SameNumber) R.string.dup_same_number else R.string.dup_similar_name),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 10.dp),
        )
        group.people.forEachIndexed { index, person ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(start = 66.dp))
            }
            Row(
                Modifier.fillMaxWidth().clickable { onPerson(person) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(person.name, person.photoUri, 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(person.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                    val details = (person.numbers + person.accounts.map { it ?: phone }.distinct()).joinToString(" · ")
                    Text(details, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp, top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            OutlinedButton(onClick = onKeepApart) { Text(stringResource(R.string.dup_keep_apart)) }
            Button(onClick = onMerge) { Text(stringResource(R.string.dup_merge)) }
        }
    }
}
