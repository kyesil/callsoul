package com.bettercallsoul.spam

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchTextTest {
    @Test
    fun lettersIgnoreCaseAndTurkishMarks() {
        assertEquals(7..8, SearchText.find("İphone Solid", "so"))
        assertEquals(0..4, SearchText.find("Şahin", "sahin"))
        assertEquals(0..3, SearchText.find("ELİF Kaya", "elif"))
        assertEquals(0..2, SearchText.find("Işık", "isi"))
        assertEquals(4..7, SearchText.find("Can Öztürk", "oztu"))
    }

    @Test
    fun foldKeepsPositions() {
        val text = "İğne Şüphe Çöp"
        assertEquals(text.length, SearchText.fold(text).length)
    }

    @Test
    fun digitsMatchAcrossFormatting() {
        assertEquals(4..6, SearchText.find("+90 532 410 27 85", "532"))
        assertEquals(4..9, SearchText.find("+90 532 410", "53241"))
    }

    @Test
    fun aLeadingZeroOrCountryCodeStillMatches() {
        assertEquals(2..6, SearchText.find("+90 532 410 27 85", "0532"))
        assertEquals(1..3, SearchText.find("0532 410 27 85", "90532"))
    }

    @Test
    fun noMatch() {
        assertNull(SearchText.find("Elif Kaya", "zey"))
        assertNull(SearchText.find("0532 410 27 85", "99"))
        assertNull(SearchText.find("Elif", "   "))
        assertNull(SearchText.find("0532", "ali 5"))
    }
}
