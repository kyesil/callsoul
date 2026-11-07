package com.bettercallsoul.codes

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/** What a scanned QR code turned out to be. */
sealed interface ScanResult {
    data class Contact(val card: ScannedContact) : ScanResult
    data class Link(val url: String) : ScanResult
    data class Phone(val number: String) : ScanResult
    data class Text(val text: String) : ScanResult
}

enum class ValueKind { Mobile, Home, Work, Fax, Other }

data class TypedValue(val value: String, val kind: ValueKind)

/** A business card read from a vCard or MECARD code. Every part is optional. */
data class ScannedContact(
    val formattedName: String? = null,
    val prefix: String? = null,
    val given: String? = null,
    val middle: String? = null,
    val family: String? = null,
    val suffix: String? = null,
    val nickname: String? = null,
    val phones: List<TypedValue> = emptyList(),
    val emails: List<TypedValue> = emptyList(),
    val addresses: List<TypedValue> = emptyList(),
    val organization: String? = null,
    val department: String? = null,
    val title: String? = null,
    val urls: List<String> = emptyList(),
    /** "yyyy-MM-dd", or "--MM-dd" when the year is left out. */
    val birthday: String? = null,
    val note: String? = null,
) {
    val isEmpty: Boolean
        get() = listOf(formattedName, given, family, organization).all { it.isNullOrBlank() } &&
            phones.isEmpty() && emails.isEmpty()
}

object ScanCodes {
    private val phoneLike = Regex("""^\+?[0-9 ()\-./]{6,}$""")

    fun read(raw: String): ScanResult {
        val text = raw.trim()
        val upper = text.uppercase()
        return when {
            upper.contains("BEGIN:VCARD") -> contactOrText(parseVCard(text), text)
            upper.startsWith("MECARD:") -> contactOrText(parseMeCard(text), text)
            upper.startsWith("TEL:") -> ScanResult.Phone(text.substring(4).trim())
            upper.startsWith("MEBKM:") -> meField(text.substring(6), "URL")?.let { ScanResult.Link(withScheme(it)) }
                ?: ScanResult.Text(text)
            upper.startsWith("URLTO:") -> ScanResult.Link(withScheme(text.substring(6).trim()))
            upper.startsWith("HTTP://") || upper.startsWith("HTTPS://") -> ScanResult.Link(text)
            upper.startsWith("WWW.") && ' ' !in text -> ScanResult.Link("https://$text")
            upper.startsWith("MAILTO:") || upper.startsWith("GEO:") -> ScanResult.Link(text)
            phoneLike.matches(text) && text.count(Char::isDigit) >= 6 -> ScanResult.Phone(text)
            else -> ScanResult.Text(text)
        }
    }

    private fun contactOrText(card: ScannedContact, text: String): ScanResult =
        if (card.isEmpty) ScanResult.Text(text) else ScanResult.Contact(card)

    private fun withScheme(url: String): String =
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(url)) url else "https://$url"

    // ---------- vCard 2.1, 3.0 and 4.0 ----------

    private class Property(val name: String, val types: Set<String>, val params: Map<String, String>, val value: String)

    fun parseVCard(text: String): ScannedContact {
        var card = ScannedContact()
        for (property in properties(text)) {
            val value = property.value
            when (property.name) {
                "FN" -> card = card.copy(formattedName = unescape(value).ifBlank { null } ?: card.formattedName)
                "N" -> {
                    val parts = splitStructured(value)
                    card = card.copy(
                        family = parts.getOrNull(0).orNullIfBlank(),
                        given = parts.getOrNull(1).orNullIfBlank(),
                        middle = parts.getOrNull(2).orNullIfBlank(),
                        prefix = parts.getOrNull(3).orNullIfBlank(),
                        suffix = parts.getOrNull(4).orNullIfBlank(),
                    )
                }
                "NICKNAME" -> card = card.copy(nickname = splitList(value).firstOrNull().orNullIfBlank())
                "TEL" -> unescape(value).removePrefix("tel:").trim().takeIf { it.isNotEmpty() }?.let {
                    card = card.copy(phones = card.phones + TypedValue(it, phoneKind(property.types)))
                }
                "EMAIL" -> unescape(value).trim().takeIf { it.isNotEmpty() }?.let {
                    card = card.copy(emails = card.emails + TypedValue(it, placeKind(property.types)))
                }
                "ADR" -> address(splitStructured(value))?.let {
                    card = card.copy(addresses = card.addresses + TypedValue(it, placeKind(property.types)))
                }
                "ORG" -> {
                    val parts = splitStructured(value)
                    card = card.copy(
                        organization = parts.getOrNull(0).orNullIfBlank(),
                        department = parts.drop(1).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { null },
                    )
                }
                "TITLE" -> card = card.copy(title = unescape(value).orNullIfBlank())
                "URL" -> unescape(value).trim().takeIf { it.isNotEmpty() }?.let { card = card.copy(urls = card.urls + it) }
                "NOTE" -> card = card.copy(note = unescape(value).orNullIfBlank())
                "BDAY" -> card = card.copy(birthday = date(unescape(value)))
            }
        }
        return card
    }

    /** Unfolds the lines, then splits each into name, parameters and value, decoding quoted-printable. */
    private fun properties(text: String): List<Property> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val unfolded = mutableListOf<String>()
        for (line in lines) {
            val previous = unfolded.lastOrNull()
            when {
                (line.startsWith(" ") || line.startsWith("\t")) && previous != null ->
                    unfolded[unfolded.lastIndex] = previous + line.substring(1)
                // Quoted-printable soft line break: the value continues on the next line.
                previous != null && previous.endsWith("=") && isQuotedPrintable(previous) ->
                    unfolded[unfolded.lastIndex] = previous.dropLast(1) + line
                else -> unfolded += line
            }
        }
        return unfolded.mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) return@mapNotNull null
            val head = line.substring(0, colon)
            var value = line.substring(colon + 1)
            val segments = head.split(';')
            val name = segments.first().substringAfterLast('.').uppercase()
            val types = mutableSetOf<String>()
            val params = mutableMapOf<String, String>()
            segments.drop(1).forEach { segment ->
                val eq = segment.indexOf('=')
                if (eq < 0) {
                    types += segment.uppercase()
                } else {
                    val key = segment.substring(0, eq).uppercase()
                    val paramValue = segment.substring(eq + 1).trim('"')
                    params[key] = paramValue
                    if (key == "TYPE") types += paramValue.uppercase().split(',')
                }
            }
            if (params["ENCODING"]?.uppercase() in setOf("QUOTED-PRINTABLE", "QP") || "QUOTED-PRINTABLE" in types) {
                value = decodeQuotedPrintable(value, params["CHARSET"] ?: "UTF-8")
            }
            Property(name, types, params, value)
        }
    }

    private fun isQuotedPrintable(line: String): Boolean = line.substringBefore(':').uppercase().contains("QUOTED-PRINTABLE")

    private fun decodeQuotedPrintable(value: String, charset: String): String {
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '=' && i + 2 <= value.lastIndex) {
                val hex = value.substring(i + 1, i + 3)
                val byte = hex.toIntOrNull(16)
                if (byte != null) {
                    bytes.write(byte)
                    i += 3
                    continue
                }
            }
            bytes.write(c.toString().toByteArray(Charsets.UTF_8))
            i++
        }
        val chosen = runCatching { Charset.forName(charset) }.getOrDefault(Charsets.UTF_8)
        return String(bytes.toByteArray(), chosen)
    }

    /** Splits on ';' that isn't escaped, and unescapes each part. */
    private fun splitStructured(value: String): List<String> = splitUnescaped(value, ';').map(::unescape)

    private fun splitList(value: String): List<String> = splitUnescaped(value, ',').map(::unescape)

    private fun splitUnescaped(value: String, separator: Char): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                current.append(c).append(value[i + 1])
                i += 2
                continue
            }
            if (c == separator) {
                parts += current.toString()
                current.clear()
            } else {
                current.append(c)
            }
            i++
        }
        parts += current.toString()
        return parts
    }

    private fun unescape(value: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n', 'N' -> out.append('\n')
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString().trim()
    }

    /** ADR is post box; extended; street; city; region; postal code; country. */
    private fun address(parts: List<String>): String? {
        val street = listOf(parts.getOrNull(0), parts.getOrNull(1), parts.getOrNull(2)).filterNot { it.isNullOrBlank() }
        val city = listOf(parts.getOrNull(5), parts.getOrNull(3)).filterNot { it.isNullOrBlank() }.joinToString(" ")
        val rest = listOf(parts.getOrNull(4), parts.getOrNull(6)).filterNot { it.isNullOrBlank() }
        return (street + listOf(city).filter { it.isNotBlank() } + rest).joinToString(", ").ifBlank { null }
    }

    private fun phoneKind(types: Set<String>): ValueKind = when {
        "FAX" in types -> ValueKind.Fax
        "CELL" in types || "MOBILE" in types || "IPHONE" in types -> ValueKind.Mobile
        "WORK" in types -> ValueKind.Work
        "HOME" in types -> ValueKind.Home
        types.none { it !in setOf("VOICE", "PREF", "X-INTERNET") } -> ValueKind.Mobile
        else -> ValueKind.Other
    }

    private fun placeKind(types: Set<String>): ValueKind = when {
        "WORK" in types -> ValueKind.Work
        "HOME" in types -> ValueKind.Home
        types.any { it == "OTHER" } -> ValueKind.Other
        else -> ValueKind.Home
    }

    /** Accepts 1990-05-12, 19900512, --0512, --05-12 and a trailing time; returns yyyy-MM-dd or --MM-dd. */
    fun date(value: String): String? {
        val day = value.trim().substringBefore('T')
        Regex("""^(\d{4})-?(\d{2})-?(\d{2})$""").matchEntire(day)?.let { m ->
            val (y, mo, d) = m.destructured
            return if (valid(mo, d)) "$y-$mo-$d" else null
        }
        Regex("""^--(\d{2})-?(\d{2})$""").matchEntire(day)?.let { m ->
            val (mo, d) = m.destructured
            return if (valid(mo, d)) "--$mo-$d" else null
        }
        return null
    }

    private fun valid(month: String, day: String): Boolean = month.toInt() in 1..12 && day.toInt() in 1..31

    // ---------- MECARD ----------

    fun parseMeCard(text: String): ScannedContact {
        var card = ScannedContact()
        val body = text.substringAfter(':')
        for (field in splitUnescaped(body, ';')) {
            val colon = field.indexOf(':')
            if (colon <= 0) continue
            val key = field.substring(0, colon).uppercase()
            val value = unescape(field.substring(colon + 1))
            if (value.isEmpty()) continue
            when (key) {
                "N" -> {
                    val parts = value.split(',').map { it.trim() }
                    card = if (parts.size >= 2) {
                        card.copy(family = parts[0].ifEmpty { null }, given = parts[1].ifEmpty { null })
                    } else {
                        card.copy(formattedName = value)
                    }
                }
                "NICKNAME" -> card = card.copy(nickname = value)
                "TEL", "TEL-AV" -> card = card.copy(phones = card.phones + TypedValue(value, ValueKind.Mobile))
                "EMAIL" -> card = card.copy(emails = card.emails + TypedValue(value, ValueKind.Home))
                // Like vCard's ADR, but with commas: post box, room, street, city, region, postal code, country.
                "ADR" -> (if (',' in value) address(value.split(',').map { it.trim() }) else value)?.let {
                    card = card.copy(addresses = card.addresses + TypedValue(it, ValueKind.Home))
                }
                "ORG" -> card = card.copy(organization = value)
                "URL" -> card = card.copy(urls = card.urls + value)
                "NOTE" -> card = card.copy(note = value)
                "BDAY" -> card = card.copy(birthday = date(value))
            }
        }
        return card
    }

    private fun meField(body: String, key: String): String? =
        splitUnescaped(body, ';').firstOrNull { it.uppercase().startsWith("$key:") }?.let { unescape(it.substring(key.length + 1)) }

    private fun String?.orNullIfBlank(): String? = this?.trim()?.ifEmpty { null }
}
