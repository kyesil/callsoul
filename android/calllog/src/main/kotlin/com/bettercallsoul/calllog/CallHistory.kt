package com.bettercallsoul.calllog

import android.content.Context
import android.provider.CallLog.Calls
import androidx.core.database.getStringOrNull
import com.bettercallsoul.stats.CallDirection
import com.bettercallsoul.stats.StatCall
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * The app's own copy of the call log, for the statistics. Android trims its log to about the last
 * 500 calls, too few for a year, so calls are copied here as they appear. Nothing older than
 * [KEEP_DAYS] days is kept. The phone's own call log is only read.
 */
class CallHistory(context: Context, private val dao: LocalDao) {
    private val resolver = context.applicationContext.contentResolver

    fun observe(): Flow<List<CallRecord>> = dao.observeCallRecords()

    /** Drops what has grown too old and copies the log's last year; the log is small enough to read whole. */
    suspend fun sync(): Unit = withContext(Dispatchers.IO) {
        val keepFrom = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(KEEP_DAYS.toLong())
        dao.deleteCallRecordsBefore(keepFrom)
        dao.deleteRingTimesBefore(keepFrom)
        val projection = arrayOf(Calls._ID, Calls.NUMBER, Calls.TYPE, Calls.DATE, Calls.DURATION)
        val cursor = runCatching {
            resolver.query(Calls.CONTENT_URI, projection, "${Calls.DATE} >= ?", arrayOf(keepFrom.toString()), null)
        }.getOrNull() ?: return@withContext
        val calls = cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(CallRecord(it.getLong(0), it.getStringOrNull(1).orEmpty(), it.getInt(2), it.getLong(3), it.getLong(4)))
                }
            }
        }
        dao.saveCallRecords(calls)
    }

    /** How long the calls with these numbers lasted, over the year kept here. */
    suspend fun talkSeconds(numbers: Collection<String>): Long = withContext(Dispatchers.IO) {
        val tails = numbers.mapTo(HashSet()) { it.filter(Char::isDigit).takeLast(10) }
        dao.callRecords().filter { it.number.filter(Char::isDigit).takeLast(10) in tails }.sumOf { it.seconds }
    }

    /** A call deleted from the list by the user stops counting too. */
    suspend fun forget(ids: Collection<Long>): Unit = withContext(Dispatchers.IO) {
        ids.chunked(500).forEach { dao.deleteCallRecords(it) }
    }

    companion object {
        const val KEEP_DAYS = 365
    }
}

fun CallRecord.toStatCall(): StatCall = StatCall(
    number = number,
    date = date,
    seconds = seconds,
    direction = when (type) {
        Calls.INCOMING_TYPE, Calls.ANSWERED_EXTERNALLY_TYPE -> CallDirection.Incoming
        Calls.OUTGOING_TYPE -> CallDirection.Outgoing
        Calls.MISSED_TYPE, Calls.REJECTED_TYPE -> CallDirection.Missed
        else -> CallDirection.Other
    },
)
