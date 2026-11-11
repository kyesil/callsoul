package com.bettercallsoul.spam

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlockRulesTest {
    private val home = listOf("90")

    @Test
    fun nationalNumbersComeFromHome() {
        assertFalse(isAbroad("05321234567", home))
        assertFalse(isAbroad("5321234567", home))
        assertFalse(isAbroad("", home))
    }

    @Test
    fun internationalNumbersOutsideTheListAreAbroad() {
        assertFalse(isAbroad("+905321234567", home))
        assertFalse(isAbroad("00905321234567", home))
        assertTrue(isAbroad("+4915112345678", home))
        assertTrue(isAbroad("004915112345678", home))
        assertFalse(isAbroad("+4915112345678", home + "49"))
        assertTrue(isAbroad("+12025550123", emptyList()))
    }

    @Test
    fun typedCodesAreCleaned() {
        assertEquals("90", cleanCountryCode("+90"))
        assertEquals("49", cleanCountryCode("0049"))
        assertEquals("1", cleanCountryCode(" 1 "))
        assertNull(cleanCountryCode("abc"))
        assertNull(cleanCountryCode("+12345"))
    }

    @Test
    fun callingCodesByCountry() {
        assertEquals("90", CallingCodes.of("TR"))
        assertEquals("49", CallingCodes.of("de"))
        assertNull(CallingCodes.of("zz"))
    }

    @Test
    fun rulesDecide() = runBlocking {
        val off = BlockRules()
        assertNull(off.reason("+4915112345678", isContact = false) { 99 })

        val abroad = BlockRules(blockAbroad = true, allowedCodes = home)
        assertEquals(BlockReason.Abroad, abroad.reason("+4915112345678", isContact = false) { 0 })
        assertNull(abroad.reason("+4915112345678", isContact = true) { 0 })
        assertNull(abroad.reason("05321234567", isContact = false) { 0 })

        val spam = BlockRules(blockSpam = true, spamVotes = 3)
        assertNull(spam.reason("05321234567", isContact = false) { 2 })
        assertEquals(BlockReason.Spam, spam.reason("05321234567", isContact = false) { 3 })
        assertNull(spam.reason("05321234567", isContact = true) { 30 })
    }
}
