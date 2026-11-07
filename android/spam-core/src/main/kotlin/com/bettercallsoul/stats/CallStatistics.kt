package com.bettercallsoul.stats

enum class CallDirection { Incoming, Outgoing, Missed, Other }

data class StatCall(val number: String, val date: Long, val seconds: Long, val direction: CallDirection)

/** Who a number belongs to. [key] is shared by all the numbers of one person; [saved] means in the contacts. */
data class StatPerson(val key: String, val saved: Boolean)

/** One person's talk time, with [number] being the one used last. */
data class PersonTotal(val key: String, val number: String, val saved: Boolean, val seconds: Long, val calls: Int)

data class StatSummary(
    val talkSeconds: Long,
    /** People actually talked to: a missed call doesn't count. */
    val people: Int,
    val calls: Int,
    val incoming: Int,
    val outgoing: Int,
    val missed: Int,
    val unsavedSeconds: Long,
    val unsavedPeople: Int,
    /** Most talked to first. */
    val top: List<PersonTotal>,
)

object CallStatistics {
    /** The totals of the [calls] made since [since]. */
    fun summarize(calls: List<StatCall>, since: Long, topCount: Int = 10, personOf: (String) -> StatPerson): StatSummary {
        val inPeriod = calls.filter { it.date >= since }
        val totals = inPeriod.filter { it.seconds > 0 }
            .groupBy { personOf(it.number) }
            .map { (person, talks) ->
                PersonTotal(person.key, talks.maxBy { it.date }.number, person.saved, talks.sumOf { it.seconds }, talks.size)
            }
        val unsaved = totals.filterNot { it.saved }
        return StatSummary(
            talkSeconds = totals.sumOf { it.seconds },
            people = totals.size,
            calls = inPeriod.size,
            incoming = inPeriod.count { it.direction == CallDirection.Incoming },
            outgoing = inPeriod.count { it.direction == CallDirection.Outgoing },
            missed = inPeriod.count { it.direction == CallDirection.Missed },
            unsavedSeconds = unsaved.sumOf { it.seconds },
            unsavedPeople = unsaved.size,
            top = totals.sortedByDescending { it.seconds }.take(topCount),
        )
    }
}
