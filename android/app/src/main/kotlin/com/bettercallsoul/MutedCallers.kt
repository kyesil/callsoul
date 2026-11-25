package com.bettercallsoul

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * People whose calls ring without sound for a while, picked on their page. A phone call still comes
 * in and shows, only Telecom keeps it quiet; a WhatsApp call is quieted from its notification, when
 * the app may read them. Numbers match by their last ten digits, so +90 532... finds 0532...; a
 * WhatsApp call that names no number matches by the contact's name. Each one ends by itself.
 */
class MutedCallers(context: Context) {
    data class Entry(val numbers: Set<String>, val name: String?, val until: Long)

    private val prefs = context.getSharedPreferences("muted_callers", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(read())

    /** What is muted now; ended ones drop out when anything is asked or changed. */
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    /** When the muting of any of these numbers ends, or null when none is muted. */
    fun until(numbers: Collection<String>): Long? {
        val keys = numbers.mapNotNullTo(HashSet(), ::keyOf)
        return live().filter { entry -> entry.numbers.any(keys::contains) }.maxOfOrNull { it.until }
    }

    fun isMuted(number: String): Boolean = until(listOf(number)) != null

    /** A WhatsApp call: by its number when the notification names one, else by the name it shows. */
    fun isMuted(number: String?, name: String?): Boolean {
        val key = number?.let(::keyOf)
        val shown = name?.trim()?.takeIf(String::isNotEmpty)
        return live().any { entry ->
            (key != null && key in entry.numbers) || (key == null && shown != null && entry.name.equals(shown, ignoreCase = true))
        }
    }

    @Synchronized
    fun mute(numbers: Collection<String>, name: String?, minutes: Int) {
        val keys = numbers.mapNotNullTo(HashSet(), ::keyOf)
        if (keys.isEmpty() || minutes <= 0) return
        val until = System.currentTimeMillis() + minutes * 60_000L
        val others = live().filter { entry -> entry.numbers.none(keys::contains) }
        save(others + Entry(keys, name?.trim()?.takeIf(String::isNotEmpty), until))
    }

    @Synchronized
    fun unmute(numbers: Collection<String>) {
        val keys = numbers.mapNotNullTo(HashSet(), ::keyOf)
        save(live().filter { entry -> entry.numbers.none(keys::contains) })
    }

    @Synchronized
    private fun live(): List<Entry> {
        val now = System.currentTimeMillis()
        val all = _entries.value
        val kept = all.filter { it.until > now }
        if (kept.size != all.size) save(kept)
        return kept
    }

    private fun save(entries: List<Entry>) {
        _entries.value = entries
        prefs.edit {
            putString(KEY, entries.joinToString("\n") { "${it.until}\t${it.name.orEmpty().replace('\t', ' ').replace('\n', ' ')}\t${it.numbers.joinToString(",")}" })
        }
    }

    private fun read(): List<Entry> = prefs.getString(KEY, null).orEmpty().lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        val until = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val numbers = parts.getOrNull(2)?.split(',')?.filter(String::isNotEmpty)?.toSet().orEmpty()
        if (numbers.isEmpty()) null else Entry(numbers, parts.getOrNull(1)?.takeIf(String::isNotEmpty), until)
    }.toList()

    companion object {
        private const val KEY = "entries"

        internal fun keyOf(number: String): String? = number.filter(Char::isDigit).takeLast(10).takeIf { it.length >= 5 }
    }
}
