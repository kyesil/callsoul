package com.bettercallsoul.whatsapp

import com.bettercallsoul.spam.SearchText

enum class CallPhase { Ringing, Dialing, Ongoing, Missed }

data class CallNotice(val name: String, val video: Boolean, val phase: CallPhase, val number: String = "")

enum class CallOutcome { Incoming, Outgoing, Missed }

data class FinishedCall(
    val name: String,
    val number: String,
    val video: Boolean,
    val outcome: CallOutcome,
    val date: Long,
    /** How long the two talked; for a missed call, how long it rang, when that was seen. */
    val seconds: Long,
)

/**
 * Reads a WhatsApp notification. WhatsApp offers no other way to learn about its calls, so this goes
 * by the notification's category and the words of its text, in the app's four languages. The words
 * are a guess at WhatsApp's own: a call in another language still counts, as a voice call.
 */
object WhatsAppNotices {
    private const val CATEGORY_CALL = "call"
    private const val CATEGORY_MISSED = "missed_call"
    private const val CATEGORY_MESSAGE = "msg"
    private const val LONGEST_LABEL = 40

    private val VIDEO = words("video", "görüntülü", "فيديو", "видео")
    private val CALL = words("call", "arama", "aranıyor", "çalıyor", "مكالمة", "вызов", "звонок")
    private val MISSED = words("missed", "cevapsız", "فائت", "пропущ")
    private val INCOMING = words("incoming", "gelen", "وارد", "входящ")
    private val ONGOING = words("ongoing", "devam eden", "جارية", "текущ")
    private val DIALING = words("calling", "ringing", "aranıyor", "çalıyor", "جارٍ الاتصال", "вызов", "звонок")

    private fun words(vararg list: String) = list.map(SearchText::fold)

    private fun String.hasAny(words: List<String>) = words.any { it in this }

    fun read(title: String?, text: String?, category: String?, chronometer: Boolean): CallNotice? {
        if (category == CATEGORY_MESSAGE) return null
        val first = title.orEmpty().trim()
        val second = text.orEmpty().trim()
        // A missed call may put its label in the title and the name in the text.
        val swapped = SearchText.fold(first).hasAny(MISSED)
        val who = if (swapped) second else first
        val label = SearchText.fold(if (swapped) first else second)
        val marked = category == CATEGORY_CALL || category == CATEGORY_MISSED
        if (who.isEmpty() || who.equals("WhatsApp", ignoreCase = true)) return null
        if (!marked && (!label.hasAny(CALL) || label.length > LONGEST_LABEL)) return null
        val phase = when {
            category == CATEGORY_MISSED || label.hasAny(MISSED) -> CallPhase.Missed
            chronometer || label.hasAny(ONGOING) -> CallPhase.Ongoing
            label.hasAny(INCOMING) -> CallPhase.Ringing
            label.hasAny(DIALING) -> CallPhase.Dialing
            marked -> CallPhase.Ringing
            else -> return null
        }
        return CallNotice(who, label.hasAny(VIDEO), phase)
    }
}

/**
 * Follows a call through its notifications: ringing or dialing, maybe talking, then gone. Notifications
 * replace one another as a call moves on, so a removal only counts after [flush] finds nothing new
 * came within the grace time. One call at a time, as WhatsApp itself does.
 */
class WhatsAppCallTracker {
    private class Live(val name: String, var number: String, var video: Boolean, val incoming: Boolean, val startedAt: Long) {
        var answeredAt: Long? = null
        var removedAt: Long? = null
    }

    private var live: Live? = null
    private var lastMissedName: String? = null
    private var lastMissedAt = 0L

    /** Takes in a notification; a call that it ends, if any, comes back to be stored. */
    fun onNotice(notice: CallNotice, now: Long): FinishedCall? {
        if (notice.phase == CallPhase.Missed) return onMissed(notice, now)
        val previous = live?.takeIf { it.name != notice.name }
        val finished = previous?.let { finish(it, now) }
        if (previous != null) live = null
        val call = live ?: Live(notice.name, notice.number, notice.video, notice.phase == CallPhase.Ringing, now).also { live = it }
        call.removedAt = null
        call.video = call.video || notice.video
        if (call.number.isEmpty()) call.number = notice.number
        if (notice.phase == CallPhase.Ongoing && call.answeredAt == null) call.answeredAt = now
        return finished
    }

    fun onRemoved(now: Long) {
        live?.removedAt = now
    }

    /** The call, once its last notification has been gone for [graceMillis]. */
    fun flush(now: Long, graceMillis: Long): FinishedCall? {
        val call = live ?: return null
        val removed = call.removedAt ?: return null
        if (now - removed < graceMillis) return null
        live = null
        return finish(call, removed)
    }

    private fun onMissed(notice: CallNotice, now: Long): FinishedCall? {
        val call = live?.takeIf { it.name == notice.name }
        if (call != null) {
            live = null
            return finish(call, now)
        }
        // WhatsApp's "missed call" note for a ring already counted when it stopped.
        if (notice.name == lastMissedName && now - lastMissedAt < SAME_MISSED_MILLIS) return null
        remember(notice.name, now)
        return FinishedCall(notice.name, notice.number, notice.video, CallOutcome.Missed, now, 0)
    }

    private fun finish(call: Live, end: Long): FinishedCall {
        val answered = call.answeredAt
        val outcome = when {
            !call.incoming -> CallOutcome.Outgoing
            answered != null -> CallOutcome.Incoming
            else -> CallOutcome.Missed
        }
        if (outcome == CallOutcome.Missed) remember(call.name, end)
        val seconds = when {
            answered != null -> (end - answered) / 1000
            outcome == CallOutcome.Missed -> (end - call.startedAt) / 1000
            else -> 0
        }
        return FinishedCall(call.name, call.number, call.video, outcome, call.startedAt, seconds)
    }

    private fun remember(name: String, at: Long) {
        lastMissedName = name
        lastMissedAt = at
    }

    private companion object {
        const val SAME_MISSED_MILLIS = 120_000L
    }
}
