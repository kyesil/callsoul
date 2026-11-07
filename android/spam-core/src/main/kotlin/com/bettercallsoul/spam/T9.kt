package com.bettercallsoul.spam

import java.text.Normalizer

/**
 * Matches what is typed on the keypad against names and numbers. Accents are folded first, so
 * Turkish ç ğ ö ş ü sit on the keys of c g o s u; Cyrillic follows the usual Russian keypad.
 */
object T9 {
    private val keys: Map<Char, Char> = buildMap {
        fun put(digit: Char, letters: String) = letters.forEach { put(it, digit) }
        put('2', "abcабвг")
        put('3', "defдежз")
        put('4', "ghiийкл")
        put('5', "jklмноп")
        put('6', "mnoрсту")
        put('7', "pqrsфхцч")
        put('8', "tuvшщъы")
        put('9', "wxyzьэюя")
    }

    private val marks = Regex("\\p{Mn}+")

    /** The digits a word is typed with; characters without a key are dropped. */
    fun encode(text: String): String {
        val folded = Normalizer.normalize(text.replace('İ', 'I').replace('ı', 'i'), Normalizer.Form.NFD)
            .replace(marks, "")
            .lowercase()
        return buildString {
            for (c in folded) {
                when {
                    c.isDigit() -> append(c)
                    else -> keys[c]?.let(::append)
                }
            }
        }
    }

    /** True when the digits start a word of [name], including runs over several words ("Ali Iki" from 25445). */
    fun matchesName(digits: String, name: String): Boolean {
        if (digits.isEmpty() || !digits.all(Char::isDigit)) return false
        val words = name.split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotEmpty).map(::encode)
        return words.indices.any { start -> words.drop(start).joinToString("").startsWith(digits) }
    }

    /** True when the typed digits appear in the number, also across the 0 / +90 prefixes. */
    fun matchesNumber(typed: String, number: String): Boolean {
        val digits = typed.filter(Char::isDigit)
        if (digits.isEmpty()) return false
        val stored = number.filter(Char::isDigit)
        return digits in stored || (digits.startsWith("0") && digits.length > 1 && digits.drop(1) in stored)
    }
}
