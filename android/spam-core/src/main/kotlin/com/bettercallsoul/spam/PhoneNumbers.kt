package com.bettercallsoul.spam

object PhoneNumbers {
    private const val TURKEY = "90"

    /** Returns the number in E.164 form, or null for short codes and anything else we can't place. */
    fun toE164(raw: String): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter(Char::isDigit)
        val candidate = when {
            trimmed.startsWith("+") -> "+$digits"
            digits.startsWith("00") -> "+${digits.drop(2)}"
            digits.length == 11 && digits.startsWith("0") -> "+$TURKEY${digits.drop(1)}"
            digits.length == 10 && digits[0] != '0' -> "+$TURKEY$digits"
            digits.length == 12 && digits.startsWith(TURKEY) -> "+$digits"
            else -> return null
        }
        return candidate.takeIf { E164.matches(it) }
    }

    private val E164 = Regex("""\+[1-9]\d{6,14}""")
}
