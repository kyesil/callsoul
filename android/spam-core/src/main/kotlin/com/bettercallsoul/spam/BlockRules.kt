package com.bettercallsoul.spam

/** Why an incoming call is turned away before it rings, or null to let it ring. */
enum class BlockReason { Abroad, Spam }

/**
 * The user's rules for turning calls away: calls from outside the country codes they allow, and
 * numbers enough people marked as spam. Saved contacts always ring.
 */
data class BlockRules(
    val blockAbroad: Boolean = false,
    /** Country calling codes, digits only ("90", "49", "1"); calls from these still ring. */
    val allowedCodes: List<String> = emptyList(),
    val blockSpam: Boolean = false,
    /** How many spam votes a number needs before its calls are turned away. */
    val spamVotes: Int = DEFAULT_SPAM_VOTES,
) {
    val any: Boolean get() = blockAbroad || blockSpam

    /** [votes] is asked for only when the spam rule is on and the number isn't already blocked. */
    suspend fun reason(number: String, isContact: Boolean, votes: suspend () -> Int): BlockReason? {
        if (!any || isContact || number.isBlank()) return null
        if (blockAbroad && isAbroad(number, allowedCodes)) return BlockReason.Abroad
        if (blockSpam && votes() >= spamVotes.coerceAtLeast(1)) return BlockReason.Spam
        return null
    }

    companion object {
        const val DEFAULT_SPAM_VOTES = 3
    }
}

/**
 * Whether [number] is an international one ("+49…", "0049…") that starts with none of [allowedCodes].
 * A number written the national way ("0532…", "532…") comes from home and always rings; so does a
 * hidden one, which can't be placed.
 */
fun isAbroad(number: String, allowedCodes: List<String>): Boolean {
    val international = internationalDigits(number) ?: return false
    return allowedCodes.none { code -> code.isNotEmpty() && international.startsWith(code) }
}

/** The digits after the international prefix ("+" or "00"), or null for a number written the national way. */
fun internationalDigits(number: String): String? {
    val trimmed = number.trim()
    val digits = trimmed.filter(Char::isDigit)
    return when {
        trimmed.startsWith("+") -> digits
        digits.startsWith("00") -> digits.drop(2)
        else -> null
    }.takeIf { !it.isNullOrEmpty() }
}

/** A country code as the user typed it ("+90", "0049", " 1 "), as digits only; null when it isn't one. */
fun cleanCountryCode(typed: String): String? {
    val digits = typed.filter(Char::isDigit).let { if (it.startsWith("00")) it.drop(2) else it }
    return digits.takeIf { it.length in 1..4 && it[0] != '0' }
}

/** Country calling codes by ISO 3166 country ("tr" → "90"), for showing the phone's own one. */
object CallingCodes {
    fun of(iso: String?): String? = iso?.lowercase()?.let(TABLE::get)

    private val TABLE: Map<String, String> = """
        af 93 al 355 dz 213 as 1 ad 376 ao 244 ai 1 ag 1 ar 54 am 374 aw 297 au 61 at 43 az 994
        bs 1 bh 973 bd 880 bb 1 by 375 be 32 bz 501 bj 229 bm 1 bt 975 bo 591 ba 387 bw 267 br 55
        bn 673 bg 359 bf 226 bi 257 kh 855 cm 237 ca 1 cv 238 ky 1 cf 236 td 235 cl 56 cn 86 co 57
        km 269 cg 242 cd 243 cr 506 ci 225 hr 385 cu 53 cy 357 cz 420 dk 45 dj 253 dm 1 do 1 ec 593
        eg 20 sv 503 gq 240 er 291 ee 372 sz 268 et 251 fj 679 fi 358 fr 33 ga 241 gm 220 ge 995
        de 49 gh 233 gi 350 gr 30 gl 299 gd 1 gu 1 gt 502 gn 224 gw 245 gy 592 ht 509 hn 504 hk 852
        hu 36 is 354 in 91 id 62 ir 98 iq 964 ie 353 il 972 it 39 jm 1 jp 81 jo 962 kz 7 ke 254
        ki 686 kp 850 kr 82 xk 383 kw 965 kg 996 la 856 lv 371 lb 961 ls 266 lr 231 ly 218 li 423
        lt 370 lu 352 mo 853 mg 261 mw 265 my 60 mv 960 ml 223 mt 356 mh 692 mr 222 mu 230 mx 52
        fm 691 md 373 mc 377 mn 976 me 382 ms 1 ma 212 mz 258 mm 95 na 264 nr 674 np 977 nl 31
        nz 64 ni 505 ne 227 ng 234 mk 389 no 47 om 968 pk 92 pw 680 ps 970 pa 507 pg 675 py 595
        pe 51 ph 63 pl 48 pt 351 pr 1 qa 974 ro 40 ru 7 rw 250 kn 1 lc 1 vc 1 ws 685 sm 378 st 239
        sa 966 sn 221 rs 381 sc 248 sl 232 sg 65 sk 421 si 386 sb 677 so 252 za 27 ss 211 es 34
        lk 94 sd 249 sr 597 se 46 ch 41 sy 963 tw 886 tj 992 tz 255 th 66 tl 670 tg 228 to 676
        tt 1 tn 216 tr 90 tm 993 tc 1 tv 688 ug 256 ua 380 ae 971 gb 44 us 1 uy 598 uz 998 vu 678
        va 39 ve 58 vn 84 vg 1 vi 1 ye 967 zm 260 zw 263 cw 599 sx 1 re 262 gp 590 mq 596 gf 594
        nc 687 pf 689 fo 298 im 44 je 44 gg 44
    """.trim().split(Regex("\\s+")).chunked(2).associate { (iso, code) -> iso to code }
}
