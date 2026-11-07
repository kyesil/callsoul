package com.bettercallsoul.stats

import kotlin.test.Test
import kotlin.test.assertEquals

class CallStatisticsTest {
    private val ali = StatPerson("Ali", saved = true)

    /** Ali has two numbers; everything else is outside the contacts. */
    private fun person(number: String) = if (number.startsWith("1")) ali else StatPerson(number, saved = false)

    private val calls = listOf(
        StatCall("100", date = 50, seconds = 60, direction = CallDirection.Incoming),
        StatCall("101", date = 60, seconds = 30, direction = CallDirection.Outgoing),
        StatCall("200", date = 55, seconds = 200, direction = CallDirection.Incoming),
        StatCall("300", date = 58, seconds = 0, direction = CallDirection.Missed),
        StatCall("100", date = 5, seconds = 999, direction = CallDirection.Outgoing),
    )

    @Test
    fun onlyThePeriodCounts() {
        val summary = CallStatistics.summarize(calls, since = 10, personOf = ::person)
        assertEquals(290, summary.talkSeconds)
        assertEquals(4, summary.calls)
        assertEquals(2, summary.incoming)
        assertEquals(1, summary.outgoing)
        assertEquals(1, summary.missed)
    }

    @Test
    fun aPersonsNumbersCountTogetherAndMissedCallsAreNotTalks() {
        val summary = CallStatistics.summarize(calls, since = 10, personOf = ::person)
        assertEquals(2, summary.people)
        val first = summary.top[1]
        assertEquals("Ali", first.key)
        assertEquals(90, first.seconds)
        assertEquals(2, first.calls)
        assertEquals("101", first.number)
    }

    @Test
    fun numbersOutsideTheContactsAreCountedApart() {
        val summary = CallStatistics.summarize(calls, since = 10, personOf = ::person)
        assertEquals(200, summary.unsavedSeconds)
        assertEquals(1, summary.unsavedPeople)
    }

    @Test
    fun topIsLongestFirstAndCut() {
        val summary = CallStatistics.summarize(calls, since = 0, topCount = 1, personOf = ::person)
        assertEquals(listOf("Ali"), summary.top.map { it.key })
        assertEquals(1089, summary.top.single().seconds)
    }

    @Test
    fun anEmptyPeriod() {
        val summary = CallStatistics.summarize(calls, since = 1000, personOf = ::person)
        assertEquals(0, summary.calls)
        assertEquals(emptyList(), summary.top)
    }
}
