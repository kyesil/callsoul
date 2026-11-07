package com.bettercallsoul.calllog

import com.bettercallsoul.whatsapp.CallOutcome
import com.bettercallsoul.whatsapp.FinishedCall
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The WhatsApp calls read from its notifications. They live in the app's own table and go into the
 * recents beside the phone's calls, with negative ids so the two can't clash. Kept for a year.
 */
class WhatsAppCallLog(private val dao: LocalDao, private val callLog: CallLogRepository, private val contacts: ContactsRepository) {
    fun observe(): Flow<List<CallEntry>> = dao.observeWhatsAppCalls()
        .map { rows ->
            val known = callLog.knownCallers(rows.mapTo(HashSet()) { it.number })
            rows.map { row ->
                val caller = known[row.number]
                entry(row, caller?.name ?: row.name, caller?.photoUri)
            }
        }
        .flowOn(Dispatchers.IO)

    /** The calls with these numbers, or under [name] when a notification gave no number. */
    suspend fun historyFor(numbers: Collection<String>, name: String? = null): List<CallEntry> = withContext(Dispatchers.IO) {
        val tails = numbers.mapTo(HashSet(), ::tail)
        dao.whatsAppCalls()
            .filter { row -> if (row.number.isBlank()) row.name == name else tail(row.number) in tails }
            .map { row -> entry(row, row.name, null) }
    }

    private fun tail(number: String) = number.filter(Char::isDigit).takeLast(10)

    private fun entry(row: WhatsAppCall, name: String, photoUri: String?) = CallEntry(
        id = -row.id,
        number = row.number,
        name = name,
        photoUri = photoUri,
        type = when (row.outcome) {
            CallOutcome.Incoming.name -> CallType.Incoming
            CallOutcome.Outgoing.name -> CallType.Outgoing
            else -> CallType.Missed
        },
        date = row.date,
        durationSeconds = if (missed(row)) 0 else row.seconds,
        whatsApp = if (row.video) WhatsAppKind.Video else WhatsAppKind.Voice,
        ringSeconds = row.seconds.takeIf { missed(row) && it > 0 },
    )

    private fun missed(row: WhatsAppCall) = row.outcome == CallOutcome.Missed.name

    /**
     * Stores a call. WhatsApp's notifications give only a name, so the number comes from the contacts;
     * someone who isn't saved shows as their number, which then is the name.
     */
    suspend fun save(call: FinishedCall): Unit = withContext(Dispatchers.IO) {
        val number = call.number.ifEmpty {
            call.name.takeIf { name -> name.count(Char::isDigit) >= 7 && name.none(Char::isLetter) }
                ?: contacts.numberOf(call.name).orEmpty()
        }
        dao.saveWhatsAppCall(
            WhatsAppCall(
                name = call.name,
                number = number,
                video = call.video,
                outcome = call.outcome.name,
                date = call.date,
                seconds = call.seconds,
            ),
        )
        dao.deleteWhatsAppCallsBefore(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(CallHistory.KEEP_DAYS.toLong()))
    }

    /** [ids] as they are in the recents: negative. */
    suspend fun forget(ids: Collection<Long>): Unit = withContext(Dispatchers.IO) {
        ids.chunked(500).forEach { chunk -> dao.deleteWhatsAppCalls(chunk.map { -it }) }
    }
}
