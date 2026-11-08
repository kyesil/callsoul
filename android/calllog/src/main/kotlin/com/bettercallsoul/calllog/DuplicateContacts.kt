package com.bettercallsoul.calllog

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.provider.ContactsContract
import android.provider.ContactsContract.AggregationExceptions
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.RawContacts
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One contact in a group of copies, with what is needed to show it and to link it. */
data class DuplicatePerson(
    val contactId: Long,
    val name: String,
    val photoUri: String?,
    val numbers: List<String>,
    /** The accounts holding its copies; null for the phone's own storage. */
    val accounts: List<String?>,
    val rawIds: List<Long>,
)

enum class DuplicateReason { SameNumber, SimilarName }

data class DuplicateGroup(val people: List<DuplicatePerson>, val reason: DuplicateReason) {
    /** Stays the same while the group does, to remember a group the user keeps apart. */
    val key: String get() = people.map { it.contactId }.sorted().joinToString(",")
}

/**
 * Finds contacts saved more than once and links them into one, as Google Contacts' "link" does:
 * the copies stay as they are and Android shows them as a single contact. Contacts that share a
 * number go together; so does a contact without a number whose name matches another one's.
 */
class DuplicateContacts(private val resolver: ContentResolver) {

    suspend fun find(): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val names = mutableMapOf<Long, Pair<String, String?>>()
        resolver.query(Contacts.CONTENT_URI, arrayOf(Contacts._ID, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_URI), null, null, null)?.use {
            while (it.moveToNext()) names[it.getLong(0)] = (it.getString(1).orEmpty()) to it.getString(2)
        }
        val raws = mutableMapOf<Long, MutableList<Pair<Long, String?>>>()
        resolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts._ID, RawContacts.CONTACT_ID, RawContacts.ACCOUNT_NAME),
            "${RawContacts.DELETED} = 0", null, null,
        )?.use {
            while (it.moveToNext()) raws.getOrPut(it.getLong(1)) { mutableListOf() } += it.getLong(0) to it.getString(2)
        }
        val numbers = mutableMapOf<Long, MutableList<String>>()
        resolver.query(Phone.CONTENT_URI, arrayOf(Phone.CONTACT_ID, Phone.NUMBER), null, null, null)?.use {
            while (it.moveToNext()) {
                val number = it.getString(1)?.trim().orEmpty()
                if (number.isNotEmpty()) numbers.getOrPut(it.getLong(0)) { mutableListOf() } += number
            }
        }
        val ids = names.keys.filter { !ContactsContract.isProfileId(it) && raws[it].orEmpty().isNotEmpty() }
        val people = UnionFind()
        val byNumber = mutableSetOf<Long>()

        // Same number, however it is written.
        ids.flatMap { id -> numbers[id].orEmpty().mapNotNull(::numberKey).distinct().map { it to id } }
            .groupBy({ it.first }, { it.second })
            .values.filter { it.distinct().size > 1 }
            .forEach { group ->
                group.drop(1).forEach { people.join(group.first(), it) }
                byNumber += group
            }

        // A contact without a number whose name is another's, or one letter off a longer name.
        val keys = ids.associateWith { nameKey(names.getValue(it).first) }.filterValues { it.isNotEmpty() }
        val bare = keys.keys.filter { numbers[it].isNullOrEmpty() }
        bare.forEach { id ->
            val key = keys.getValue(id)
            keys.forEach { (other, otherKey) ->
                if (other != id && (otherKey == key || (key.length >= SIMILAR_MIN_LENGTH && oneEditApart(key, otherKey)))) people.join(id, other)
            }
        }

        people.groups(ids).filter { it.size > 1 }.map { group ->
            DuplicateGroup(
                people = group.map { id ->
                    val (name, photo) = names.getValue(id)
                    val copies = raws[id].orEmpty()
                    DuplicatePerson(id, name, photo, numbers[id].orEmpty().distinct(), copies.map { it.second }.distinct(), copies.map { it.first })
                }.sortedByDescending { it.numbers.size },
                reason = if (group.any { it in byNumber }) DuplicateReason.SameNumber else DuplicateReason.SimilarName,
            )
        }.sortedBy { it.people.first().name.lowercase() }
    }

    /** Links the copies into one contact. Nothing is deleted; Android keeps them together from now on. */
    suspend fun link(group: DuplicateGroup): Boolean = withContext(Dispatchers.IO) {
        val rawIds = group.people.flatMap { it.rawIds }.distinct()
        val first = rawIds.firstOrNull() ?: return@withContext false
        val operations = rawIds.drop(1).map { other ->
            ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI)
                .withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_TOGETHER)
                .withValue(AggregationExceptions.RAW_CONTACT_ID1, first)
                .withValue(AggregationExceptions.RAW_CONTACT_ID2, other)
                .build()
        }
        runCatching { resolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(operations)) }.isSuccess
    }

    private class UnionFind {
        private val parent = mutableMapOf<Long, Long>()
        fun root(id: Long): Long {
            var at = id
            while (true) {
                val up = parent[at] ?: return at
                at = up
            }
        }
        fun join(a: Long, b: Long) {
            val ra = root(a)
            val rb = root(b)
            if (ra != rb) parent[rb] = ra
        }
        fun groups(ids: List<Long>): Collection<List<Long>> = ids.groupBy(::root).values
    }

    companion object {
        private const val SIMILAR_MIN_LENGTH = 6
        private const val NUMBER_DIGITS = 10
        private const val SHORTEST_NUMBER = 5

        /** The last ten digits, so 0532…, +90532… and 90532… are the same number; short codes are left out. */
        fun numberKey(number: String): String? {
            val digits = number.filter(Char::isDigit)
            if (digits.length < SHORTEST_NUMBER) return null
            return digits.takeLast(NUMBER_DIGITS)
        }

        /** The name's words without case or accents, in order, so "Çelik Selin" matches "selin celik". */
        fun nameKey(name: String): String {
            val folded = name.replace('ı', 'i').replace('İ', 'i').lowercase(Locale.ROOT)
            val plain = Normalizer.normalize(folded, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            val words = plain.split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotEmpty)
            // A name that is only a number is no name.
            if (words.none { word -> word.any(Char::isLetter) }) return ""
            return words.sorted().joinToString(" ")
        }

        /** True when one letter changed, added or left out turns [a] into [b]. */
        fun oneEditApart(a: String, b: String): Boolean {
            if (a == b) return true
            if (kotlin.math.abs(a.length - b.length) > 1) return false
            val (short, long) = if (a.length <= b.length) a to b else b to a
            var i = 0
            var j = 0
            var edits = 0
            while (i < short.length && j < long.length) {
                if (short[i] == long[j]) {
                    i++
                    j++
                    continue
                }
                if (++edits > 1) return false
                if (short.length == long.length) i++
                j++
            }
            return edits + (long.length - j) <= 1
        }
    }
}
