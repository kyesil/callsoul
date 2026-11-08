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

    /**
     * Where in [name] the digits match, as character positions, for showing the match in bold:
     * from the start of the word they begin at to the last letter they reach. Null when they don't.
     */
    fun nameMatch(digits: String, name: String): IntRange? {
        if (digits.isEmpty() || !digits.all(Char::isDigit)) return null
        val starts = Regex("[\\p{L}\\p{N}]+").findAll(name).map { it.range.first }
        for (start in starts) {
            var matched = 0
            for (i in start until name.length) {
                val key = encode(name[i].toString())
                if (key.isEmpty()) continue
                if (key[0] != digits[matched]) break
                matched++
                if (matched == digits.length) return start..i
            }
        }
        return null
    }

    /** Where in [number] the typed digits are, as character positions, for showing them in bold. */
    fun numberMatch(typed: String, number: String): IntRange? {
        val digits = typed.filter(Char::isDigit)
        if (digits.isEmpty()) return null
        val places = number.indices.filter { number[it].isDigit() }
        val stored = places.joinToString("") { number[it].toString() }
        val (found, length) = stored.indexOf(digits).takeIf { it >= 0 }?.let { it to digits.length }
            ?: digits.takeIf { it.startsWith("0") && it.length > 1 }?.drop(1)?.let { rest -> stored.indexOf(rest).takeIf { it >= 0 }?.let { it to rest.length } }
            ?: return null
        return places[found]..places[found + length - 1]
    }

    /** True when the typed digits appear in the number, also across the 0 / +90 prefixes. */
    fun matchesNumber(typed: String, number: String): Boolean {
        val digits = typed.filter(Char::isDigit)
        if (digits.isEmpty()) return false
        val stored = number.filter(Char::isDigit)
        return digits in stored || (digits.startsWith("0") && digits.length > 1 && digits.drop(1) in stored)
    }
}
