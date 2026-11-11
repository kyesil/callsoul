package com.bettercallsoul.calllog

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog.Calls
import android.provider.ContactsContract
import android.provider.ContactsContract.PhoneLookup
import androidx.core.database.getStringOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlinx.coroutines.withContext

enum class CallType { Incoming, Outgoing, Missed, Rejected, Blocked, Other }

data class CallEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val photoUri: String?,
    val type: CallType,
    val date: Long,
    val durationSeconds: Long,
    /** Set for a WhatsApp call read from its notification; its id is negative and [number] may be empty. */
    val whatsApp: WhatsAppKind? = null,
    /** How long a missed or declined call rang, when this app was the one that saw it ring. */
    val ringSeconds: Long? = null,
)

/**
 * Puts each unanswered call's ring time on it. The log has no field for it, so the two are matched
 * by number and start: the log dates a call by when it was created, as [RingTime.date] does.
 */
fun List<CallEntry>.withRingTimes(rings: List<RingTime>): List<CallEntry> {
    if (rings.isEmpty()) return this
    val byNumber = rings.groupBy { it.number.filter(Char::isDigit).takeLast(10) }
    return map { call ->
        if (call.type != CallType.Missed && call.type != CallType.Rejected) return@map call
        val ring = byNumber[call.number.filter(Char::isDigit).takeLast(10)].orEmpty()
            .filter { abs(it.date - call.date) <= RING_MATCH_MILLIS }
            .minByOrNull { abs(it.date - call.date) }
        if (ring == null) call else call.copy(ringSeconds = ring.seconds)
    }
}

private const val RING_MATCH_MILLIS = 30_000L

/** A number's contact: the name and small photo the address book has for it. */
data class KnownCaller(val name: String, val photoUri: String?)

class CallLogRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Reads the calls again now: a contact changed here, so the names beside them may have too. */
    fun refresh() {
        refreshes.tryEmit(Unit)
    }

    /** Re-reads when the log changes, and when contacts do, since names come from the address book. */
    fun observe(limit: Int = 300): Flow<List<CallEntry>> =
        merge(changes(Calls.CONTENT_URI), changes(ContactsContract.Contacts.CONTENT_URI), refreshes)
            .conflate()
            .map { query(null, emptyArray(), limit) }
            .flowOn(Dispatchers.IO)

    suspend fun historyFor(number: String, limit: Int = 50): List<CallEntry> =
        withContext(Dispatchers.IO) {
            // The log stores the same line as 0532..., +90532... and so on.
            val tail = number.filter(Char::isDigit).takeLast(10)
            if (tail.isEmpty()) emptyList() else query("${Calls.NUMBER} LIKE ?", arrayOf("%$tail"), limit)
        }

    suspend fun lastCall(number: String): CallEntry? = historyFor(number, limit = 1).firstOrNull()

    private fun changes(uri: Uri): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(uri, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    private fun query(selection: String?, args: Array<String>, limit: Int): List<CallEntry> {
        val projection = arrayOf(
            Calls._ID, Calls.NUMBER, Calls.CACHED_NAME, Calls.CACHED_PHOTO_URI,
            Calls.TYPE, Calls.DATE, Calls.DURATION,
        )
        val uri = Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(Calls.LIMIT_PARAM_KEY, limit.toString())
            .build()
        val cursor = resolver.query(uri, projection, selection, args, "${Calls.DATE} DESC")
            ?: return emptyList()
        // The log's cached name is only what the dialer of the day wrote down: missing when another
        // app was the dialer or the contact was added afterwards, stale after a rename or delete.
        // So each number is looked up in the address book once per read, and the cached name is
        // used only when contacts can't be read.
        val people = HashMap<String, KnownCaller?>()
        var canLookUp = true
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val number = it.getStringOrNull(1).orEmpty()
                    var cachedName = it.getStringOrNull(2)?.takeIf(String::isNotBlank)
                    var photo = it.getStringOrNull(3)
                    if (canLookUp && number.isNotBlank()) {
                        try {
                            val person = people.getOrPut(number) { lookUp(number) }
                            cachedName = person?.name
                            photo = person?.photoUri
                        } catch (_: SecurityException) {
                            canLookUp = false
                        }
                    }
                    add(
                        CallEntry(
                            id = it.getLong(0),
                            number = number,
                            name = cachedName,
                            photoUri = photo,
                            type = callType(it.getInt(4)),
                            date = it.getLong(5),
                            durationSeconds = it.getLong(6),
                        ),
                    )
                }
            }
        }
    }

    /** Removes the calls from the phone's call log; the app is the default phone app, so it may. */
    suspend fun delete(ids: Collection<Long>): Unit = withContext(Dispatchers.IO) {
        ids.chunked(500).forEach { chunk ->
            resolver.delete(Calls.CONTENT_URI, "${Calls._ID} IN (${chunk.joinToString()})", null)
        }
    }

    /** The contact behind each of [numbers]; numbers outside the contacts are left out. */
    suspend fun knownCallers(numbers: Collection<String>): Map<String, KnownCaller> = withContext(Dispatchers.IO) {
        numbers.filter(String::isNotBlank).mapNotNull { number ->
            runCatching { lookUp(number) }.getOrNull()?.let { number to it }
        }.toMap()
    }

    private fun lookUp(number: String): KnownCaller? {
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(NameOrder.nameColumn, PhoneLookup.PHOTO_THUMBNAIL_URI)
        return resolver.query(uri, projection, null, null, null)?.use {
            if (it.moveToFirst()) it.getStringOrNull(0)?.takeIf(String::isNotBlank)?.let { name -> KnownCaller(name, it.getStringOrNull(1)) } else null
        }
    }

    private fun callType(value: Int): CallType = when (value) {
        Calls.INCOMING_TYPE, Calls.ANSWERED_EXTERNALLY_TYPE -> CallType.Incoming
        Calls.OUTGOING_TYPE -> CallType.Outgoing
        Calls.MISSED_TYPE -> CallType.Missed
        Calls.REJECTED_TYPE -> CallType.Rejected
        Calls.BLOCKED_TYPE -> CallType.Blocked
        else -> CallType.Other
    }
}
