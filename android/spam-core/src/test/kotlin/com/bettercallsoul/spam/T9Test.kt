package com.bettercallsoul.spam

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class T9Test {
    @Test
    fun encodesLatinTurkishAndCyrillic() {
        assertEquals("254", T9.encode("Ali"))
        assertEquals("78578", T9.encode("Şükrü"))
        assertEquals(T9.encode("Igdir"), T9.encode("Iğdır"))
        assertEquals(T9.encode("Istanbul"), T9.encode("İstanbul"))
        assertEquals("2552", T9.encode("Анна"))
    }

    @Test
    fun matchesWordStartsAndRuns() {
        assertTrue(T9.matchesName("254", "Ali Iki"))
        assertTrue(T9.matchesName("454", "Ali Iki"))
        assertTrue(T9.matchesName("25445", "Ali Iki"))
        assertTrue(T9.matchesName("6383", "Metehan Bekler"))
        assertTrue(T9.matchesName("235537", "Metehan Bekler"))
        assertFalse(T9.matchesName("54", "Ali Iki"))
        assertFalse(T9.matchesName("25*", "Ali Iki"))
    }

    @Test
    fun matchesNumbersAcrossPrefixes() {
        assertTrue(T9.matchesNumber("5321", "+90 532 123 45 67"))
        assertTrue(T9.matchesNumber("0532", "+905321234567"))
        assertTrue(T9.matchesNumber("4567", "0532 123 45 67"))
        assertFalse(T9.matchesNumber("999", "0532 123 45 67"))
    }

    @Test
    fun findsWhereTheDigitsMatch() {
        // "Ali Iki": 2-5-4 spells "Ali", and 4-5-4 starts the second word.
        assertEquals(0..2, T9.nameMatch("254", "Ali Iki"))
        assertEquals(4..6, T9.nameMatch("454", "Ali Iki"))
        // A run over two words ends inside the second one.
        assertEquals(0..5, T9.nameMatch("25445", "Ali Iki"))
        assertEquals(null, T9.nameMatch("999", "Ali Iki"))
        // Digits in a spaced number, and the leading 0 against +90.
        assertEquals(9..13, T9.numberMatch("4567", "0532 123 45 67"))
        assertEquals(2..5, T9.numberMatch("0532", "+905321234567"))
        assertEquals(2..6, T9.numberMatch("0532", "+90 532 123"))
        assertEquals(null, T9.numberMatch("999", "0532 123 45 67"))
    }
}
