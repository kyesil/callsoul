package com.bettercallsoul.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhatsAppNoticesTest {
    private fun read(title: String?, text: String?, category: String? = "call", chronometer: Boolean = false) =
        WhatsAppNotices.read(title, text, category, chronometer)

    @Test
    fun incomingVoiceAndVideo() {
        assertEquals(CallNotice("Ali", video = false, phase = CallPhase.Ringing), read("Ali", "Gelen sesli arama"))
        assertEquals(CallNotice("Ali", video = true, phase = CallPhase.Ringing), read("Ali", "Incoming video call"))
        assertEquals(true, read("Ali", "GELEN GÖRÜNTÜLÜ ARAMA")?.video)
    }

    @Test
    fun ongoingAndDialing() {
        assertEquals(CallPhase.Ongoing, read("Ali", "Ongoing voice call")?.phase)
        assertEquals(CallPhase.Ongoing, read("Ali", "00:12", chronometer = true)?.phase)
        assertEquals(CallPhase.Dialing, read("Ali", "Aranıyor…")?.phase)
    }

    @Test
    fun missedWithTheLabelOnEitherLine() {
        assertEquals(CallNotice("Ali", video = false, phase = CallPhase.Missed), read("Ali", "Cevapsız sesli arama", category = null))
        assertEquals(CallNotice("Ali", video = true, phase = CallPhase.Missed), read("Missed video call", "Ali", category = null))
    }

    /** As WhatsApp wrote them on a phone in English: a ring has no text at all. */
    @Test
    fun theWordsSeenOnARealPhone() {
        assertEquals(CallPhase.Ringing, read("Ali", "")?.phase)
        assertEquals(CallPhase.Dialing, read("Ali", "Calling…")?.phase)
        assertEquals(CallPhase.Dialing, read("Ali", "Ringing…")?.phase)
        assertEquals(CallPhase.Ongoing, read("Ali", "Ongoing voice call", chronometer = true)?.phase)
        assertEquals(CallPhase.Missed, read("Ali", "3 missed calls", category = "missed_call")?.phase)
    }

    @Test
    fun anUnknownLanguageStillCounts() {
        assertEquals(CallNotice("Ali", video = false, phase = CallPhase.Ringing), read("Ali", "Eingehender Sprachanruf"))
    }

    @Test
    fun messagesAreNotCalls() {
        assertNull(read("Ali", "Cevapsız arama için özür dilerim", category = "msg"))
        assertNull(read("Ali", "Yarın geliyorum", category = null))
        assertNull(read("Ali", "Bu mesaj çok uzun ve içinde arama kelimesi geçiyor ama bir arama değil", category = null))
    }

    @Test
    fun aSummaryWithNoNameIsLeftOut() {
        assertNull(read("WhatsApp", "2 cevapsız arama", category = null))
    }
}

class WhatsAppCallTrackerTest {
    private val ring = CallNotice("Ali", video = true, phase = CallPhase.Ringing)
    private val talk = CallNotice("Ali", video = false, phase = CallPhase.Ongoing)
    private val dial = CallNotice("Ali", video = false, phase = CallPhase.Dialing)
    private val missed = CallNotice("Ali", video = false, phase = CallPhase.Missed)

    @Test
    fun anAnsweredCall() {
        val tracker = WhatsAppCallTracker()
        tracker.onNotice(ring, 1_000)
        tracker.onRemoved(5_000)
        tracker.onNotice(talk, 6_000)
        tracker.onRemoved(66_000)
        assertNull(tracker.flush(67_000, 3_000))
        val call = assertNotNull(tracker.flush(70_000, 3_000))
        assertEquals(CallOutcome.Incoming, call.outcome)
        assertEquals(60, call.seconds)
        assertEquals(1_000, call.date)
        assertTrue(call.video)
    }

    @Test
    fun aRingThatEndsIsMissedAndItsNoteDoesntCountTwice() {
        val tracker = WhatsAppCallTracker()
        tracker.onNotice(ring, 1_000)
        tracker.onRemoved(9_000)
        val call = assertNotNull(tracker.flush(12_000, 3_000))
        assertEquals(CallOutcome.Missed, call.outcome)
        assertEquals(8, call.seconds)
        assertNull(tracker.onNotice(missed, 13_000))
    }

    @Test
    fun aRingEndedByItsMissedNoteKeepsTheRingTime() {
        val tracker = WhatsAppCallTracker()
        tracker.onNotice(ring, 1_000)
        val call = assertNotNull(tracker.onNotice(missed, 46_000))
        assertEquals(CallOutcome.Missed, call.outcome)
        assertEquals(45, call.seconds)
    }

    @Test
    fun aMissedNoteAloneCounts() {
        val call = assertNotNull(WhatsAppCallTracker().onNotice(missed, 1_000))
        assertEquals(CallOutcome.Missed, call.outcome)
    }

    @Test
    fun anOutgoingCall() {
        val tracker = WhatsAppCallTracker()
        tracker.onNotice(dial, 1_000)
        tracker.onNotice(talk, 4_000)
        tracker.onRemoved(34_000)
        val call = assertNotNull(tracker.flush(40_000, 3_000))
        assertEquals(CallOutcome.Outgoing, call.outcome)
        assertEquals(30, call.seconds)
    }

    /** The phone handed the notifications over at the end of the call, all within the same moment. */
    @Test
    fun theNotificationsTimerGivesTheTalkTimeEvenIfTheyArriveLate() {
        val tracker = WhatsAppCallTracker()
        tracker.onNotice(ring, 2_000)
        tracker.onNotice(talk.copy(talkingSince = 10_000), 10_100)
        tracker.onRemoved(47_000)
        val call = assertNotNull(tracker.flush(50_100, 3_000))
        assertEquals(CallOutcome.Incoming, call.outcome)
        assertEquals(37, call.seconds)
        assertEquals(2_000, call.date)
    }

    @Test
    fun aTimerThatStartsInTheFutureCountsFromTheNotice() {
        val tracker = WhatsAppCallTracker()
        tracker.onNotice(talk.copy(talkingSince = 90_000), 10_000)
        tracker.onRemoved(40_000)
        assertEquals(30, assertNotNull(tracker.flush(43_000, 3_000)).seconds)
    }

    @Test
    fun aNewNoticeCancelsTheRemoval() {
        val tracker = WhatsAppCallTracker()
        tracker.onNotice(ring, 1_000)
        tracker.onRemoved(2_000)
        tracker.onNotice(talk, 3_000)
        assertNull(tracker.flush(10_000, 3_000))
    }
}

class TelecomCallTest {
    private val call = TelecomCall("Ali", "+905321234567", video = false, incoming = true, createdAt = 2_000, connectedAt = 10_083, endedAt = 45_406)

    @Test
    fun anAnsweredCallCountsFromTheAnswer() {
        val finished = call.finished()
        assertEquals(CallOutcome.Incoming, finished.outcome)
        assertEquals(35, finished.seconds)
        assertEquals(2_000, finished.date)
        assertEquals(CallOutcome.Outgoing, call.copy(incoming = false).finished().outcome)
        assertEquals(35, call.copy(incoming = false).finished().seconds)
    }

    @Test
    fun anUnansweredCallThatCameInIsMissedWithItsRingTime() {
        val finished = call.copy(connectedAt = 0, endedAt = 9_000).finished()
        assertEquals(CallOutcome.Missed, finished.outcome)
        assertEquals(7, finished.seconds)
    }

    @Test
    fun anUnansweredCallWeMadeHasNoTime() {
        val finished = call.copy(incoming = false, connectedAt = 0).finished()
        assertEquals(CallOutcome.Outgoing, finished.outcome)
        assertEquals(0, finished.seconds)
    }
}

class TelecomCoverageTest {
    @Test
    fun nothingIsCoveredBeforeTelecomSpeaks() {
        assertFalse(TelecomCoverage().covers(1_000_000))
    }

    @Test
    fun aCallIsCoveredWhileItLastsAndItsNotificationsAfterwards() {
        val coverage = TelecomCoverage()
        coverage.opened()
        assertTrue(coverage.active)
        assertTrue(coverage.covers(1_000_000))
        coverage.closed(1_000_000)
        assertFalse(coverage.active)
        assertTrue(coverage.covers(990_000))
        assertTrue(coverage.covers(1_030_000))
    }

    @Test
    fun aCallLongAfterIsNot() {
        val coverage = TelecomCoverage()
        coverage.opened()
        coverage.closed(1_000_000)
        assertFalse(coverage.covers(1_100_000))
    }
}
