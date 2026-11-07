package com.bettercallsoul.spam

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneNumbersTest {
    @Test
    fun normalizesTurkishForms() {
        val expected = "+905321234567"
        assertEquals(expected, PhoneNumbers.toE164("0532 123 45 67"))
        assertEquals(expected, PhoneNumbers.toE164("532 123 45 67"))
        assertEquals(expected, PhoneNumbers.toE164("905321234567"))
        assertEquals(expected, PhoneNumbers.toE164("+90 (532) 123-45-67"))
        assertEquals(expected, PhoneNumbers.toE164("00905321234567"))
    }

    @Test
    fun keepsOtherCountries() {
        assertEquals("+4915112345678", PhoneNumbers.toE164("+49 151 12345678"))
    }

    @Test
    fun rejectsShortCodesAndEmptyInput() {
        assertNull(PhoneNumbers.toE164("112"))
        assertNull(PhoneNumbers.toE164("4440000"))
        assertNull(PhoneNumbers.toE164(""))
    }
}
