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

    @Test
    fun typedPrefixesAreCleaned() {
        assertEquals("90850", cleanPrefix("+90 850", "90"))
        assertEquals("90850", cleanPrefix("0090850", "90"))
        assertEquals("90850", cleanPrefix("0850", "90"))
        assertEquals("90850", cleanPrefix("850", "90"))
        assertEquals("49151", cleanPrefix("+49 151", "90"))
        assertNull(cleanPrefix("", "90"))
        assertNull(cleanPrefix("+", "90"))
        assertNull(cleanPrefix("0", "90"))
        assertNull(cleanPrefix("abc", "90"))
        assertEquals("+90 850", prefixLabel("90850"))
        assertEquals("+44 70", prefixLabel("4470"))
        assertEquals("+1 900", prefixLabel("1900"))
        assertEquals("+90", prefixLabel("90"))
    }

    @Test
    fun numbersAreMatchedByPrefix() {
        val p = listOf("90850")
        assertTrue(startsWithAny("+908501234567", p, "90"))
        assertTrue(startsWithAny("00908501234567", p, "90"))
        assertTrue(startsWithAny("0850 123 45 67", p, "90"))
        assertTrue(startsWithAny("8501234567", p, "90"))
        assertTrue(startsWithAny("908501234567", p, "90"))
        assertFalse(startsWithAny("05321234567", p, "90"))
        assertFalse(startsWithAny("+498501234567", p, "90"))
        assertFalse(startsWithAny("", p, "90"))
    }

    @Test
    fun prefixRuleDecides() = runBlocking {
        val rules = BlockRules(blockPrefixes = true, prefixes = listOf("90850"), homeCode = "90")
        assertEquals(BlockReason.Prefix, rules.reason("08501234567", isContact = false) { 0 })
        assertNull(rules.reason("08501234567", isContact = true) { 0 })
        assertNull(rules.reason("05321234567", isContact = false) { 0 })
        assertNull(rules.copy(blockPrefixes = false).reason("08501234567", isContact = false) { 0 })
        assertFalse(BlockRules(blockPrefixes = true, homeCode = "90").any)
    }
}
