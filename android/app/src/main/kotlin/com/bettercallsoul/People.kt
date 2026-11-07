package com.bettercallsoul

import com.bettercallsoul.calllog.CallEntry
import com.bettercallsoul.telecom.CallerDirectory

/** Someone talked to: a contact, or a number outside the contacts. A contact's numbers share [key]. */
class TalkedPerson(val key: String, val number: String, val name: String, val photoUri: String?, val seconds: Long)

/** By total talk time in the calls given, longest first; a contact's numbers count together. */
fun mostTalked(calls: List<CallEntry>, labels: Map<String, String>, count: Int): List<TalkedPerson> =
    calls.filter { it.durationSeconds > 0 && it.number.isNotBlank() }
        .groupBy { it.name ?: CallerDirectory.keyOf(it.number) }
        .map { (key, entries) ->
            val latest = entries.first()
            val name = latest.name ?: labels[CallerDirectory.keyOf(latest.number)] ?: latest.number
            TalkedPerson(key, latest.number, name, latest.photoUri, entries.sumOf { it.durationSeconds })
        }
        .sortedByDescending { it.seconds }
        .take(count)
