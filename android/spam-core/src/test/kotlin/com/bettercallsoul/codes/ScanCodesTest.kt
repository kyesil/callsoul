package com.bettercallsoul.codes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ScanCodesTest {
    private fun contact(raw: String): ScannedContact = assertIs<ScanResult.Contact>(ScanCodes.read(raw)).card

    @Test
    fun vcard3WithTypesAndStructuredParts() {
        val card = contact(
            """
            BEGIN:VCARD
            VERSION:3.0
            N:Arslan;Deniz;;Dr.;
            FN:Dr. Deniz Arslan
            ORG:Atlas Mimarlık;Proje Ofisi
            TITLE:Proje yöneticisi
            TEL;TYPE=CELL,VOICE:+90 532 410 27 85
            TEL;TYPE=WORK:0212 455 30 10
            EMAIL;TYPE=INTERNET,WORK:deniz@atlas.example
            ADR;TYPE=WORK:;;Moda Cad. 12;Kadıköy;İstanbul;34710;Türkiye
            URL:https://atlas.example
            BDAY:1988-04-23
            NOTE:Perşembe toplantısı\, sabah\nOfiste
            END:VCARD
            """.trimIndent(),
        )
        assertEquals("Deniz", card.given)
        assertEquals("Arslan", card.family)
        assertEquals("Dr.", card.prefix)
        assertEquals("Atlas Mimarlık", card.organization)
        assertEquals("Proje Ofisi", card.department)
        assertEquals("Proje yöneticisi", card.title)
        assertEquals(listOf(TypedValue("+90 532 410 27 85", ValueKind.Mobile), TypedValue("0212 455 30 10", ValueKind.Work)), card.phones)
        assertEquals(TypedValue("deniz@atlas.example", ValueKind.Work), card.emails.single())
        assertEquals("Moda Cad. 12, 34710 Kadıköy, İstanbul, Türkiye", card.addresses.single().value)
        assertEquals(listOf("https://atlas.example"), card.urls)
        assertEquals("1988-04-23", card.birthday)
        assertEquals("Perşembe toplantısı, sabah\nOfiste", card.note)
    }

    @Test
    fun vcard21WithBareTypesQuotedPrintableAndGroups() {
        val card = contact(
            "BEGIN:VCARD\r\nVERSION:2.1\r\n" +
                "N;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:=C3=96zt=C3=BCrk;Can;;;\r\n" +
                "TEL;CELL:05559087714\r\n" +
                "item1.EMAIL;type=INTERNET:can@example.com\r\n" +
                "NOTE;ENCODING=QUOTED-PRINTABLE:uzun bir =\r\nnot\r\n" +
                "END:VCARD",
        )
        assertEquals("Öztürk", card.family)
        assertEquals("Can", card.given)
        assertEquals(TypedValue("05559087714", ValueKind.Mobile), card.phones.single())
        assertEquals("can@example.com", card.emails.single().value)
        assertEquals("uzun bir not", card.note)
    }

    @Test
    fun foldedLinesAndYearlessBirthday() {
        val card = contact("BEGIN:VCARD\nVERSION:4.0\nFN:Selin\n  Çelik\nBDAY:--0612\nTEL:tel:+905316624450\nEND:VCARD")
        assertEquals("Selin Çelik", card.formattedName)
        assertEquals("--06-12", card.birthday)
        assertEquals("+905316624450", card.phones.single().value)
    }

    @Test
    fun meCard() {
        val card = contact("MECARD:N:Kaya,Elif;TEL:+905351234408;EMAIL:elif@example.com;BDAY:19920130;NOTE:Bir\\; iki;URL:https\\://elif.example;;")
        assertEquals("Elif", card.given)
        assertEquals("Kaya", card.family)
        assertEquals("+905351234408", card.phones.single().value)
        assertEquals("1992-01-30", card.birthday)
        assertEquals("Bir; iki", card.note)
        assertEquals(listOf("https://elif.example"), card.urls)
    }

    @Test
    fun linksPhonesAndText() {
        assertEquals(ScanResult.Link("https://yesil.dev/a?b=c"), ScanCodes.read("https://yesil.dev/a?b=c"))
        assertEquals(ScanResult.Link("https://www.example.com"), ScanCodes.read("www.example.com"))
        assertEquals(ScanResult.Link("https://example.com"), ScanCodes.read("URLTO:example.com"))
        assertEquals(ScanResult.Link("https://example.com"), ScanCodes.read("MEBKM:TITLE:Örnek;URL:https\\://example.com;;"))
        assertEquals(ScanResult.Phone("+90 532 410 27 85"), ScanCodes.read("tel:+90 532 410 27 85"))
        assertEquals(ScanResult.Phone("0532 410 27 85"), ScanCodes.read("0532 410 27 85"))
        assertEquals(ScanResult.Text("WIFI:S:Ev;T:WPA;P:gizli;;"), ScanCodes.read("WIFI:S:Ev;T:WPA;P:gizli;;"))
        assertEquals(ScanResult.Text("merhaba dünya"), ScanCodes.read("merhaba dünya"))
    }

    @Test
    fun emptyCardIsText() {
        assertIs<ScanResult.Text>(ScanCodes.read("BEGIN:VCARD\nVERSION:3.0\nEND:VCARD"))
    }

    @Test
    fun dates() {
        assertEquals("1990-05-12", ScanCodes.date("19900512"))
        assertEquals("1990-05-12", ScanCodes.date("1990-05-12T00:00:00Z"))
        assertEquals("--05-12", ScanCodes.date("--05-12"))
        assertNull(ScanCodes.date("1990-13-01"))
        assertNull(ScanCodes.date("yarın"))
    }
}
