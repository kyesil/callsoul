package com.bettercallsoul.spam

/**
 * Matching for the search screen. Letters compare without case or Turkish marks, so "sahin" finds
 * "Şahin"; digits compare however the number is written, so "0532" finds "+90 532 410 27 85".
 */
object SearchText {
    private const val COUNTRY = "90"
    private const val SHORTEST_TRIMMED = 3

    /** One character for one, so a position in the folded text is the same position in the original. */
    fun fold(text: String): String = buildString(text.length) { text.forEach { append(foldChar(it)) } }

    private fun foldChar(char: Char): Char = when (char) {
        'İ', 'I', 'ı' -> 'i'
        'Ş', 'ş' -> 's'
        'Ç', 'ç' -> 'c'
        'Ğ', 'ğ' -> 'g'
        'Ü', 'ü' -> 'u'
        'Ö', 'ö' -> 'o'
        else -> char.lowercaseChar()
    }

    /** The part of [text] that [query] matches, to highlight it, or null when it doesn't match. */
    fun find(text: String, query: String): IntRange? {
        val wanted = fold(query.trim())
        if (wanted.isEmpty()) return null
        val at = fold(text).indexOf(wanted)
        return if (at >= 0) at until at + wanted.length else findDigits(text, query.trim())
    }

    private fun findDigits(text: String, query: String): IntRange? {
        if (!query.all { it.isDigit() || it in " +-()" }) return null
        val typed = query.filter(Char::isDigit)
        if (typed.isEmpty()) return null
        val positions = text.indices.filter { text[it].isDigit() }
        val digits = positions.joinToString("") { text[it].toString() }
        // The same line written the other way round: 0532... against +90 532..., or the reverse.
        val forms = listOf(typed, typed.removePrefix("0"), typed.removePrefix(COUNTRY))
            .filter { it == typed || it.length >= SHORTEST_TRIMMED }
        for (form in forms) {
            val at = digits.indexOf(form)
            if (at >= 0) return positions[at]..positions[at + form.length - 1]
        }
        return null
    }
}
