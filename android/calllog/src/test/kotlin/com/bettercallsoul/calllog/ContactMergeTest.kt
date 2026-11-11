package com.bettercallsoul.calllog

import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContactMergeTest {
    private val target = ContactDraft(
        given = "Ali", family = "Kaya", company = "Eski AŞ", note = "eski not",
        phones = listOf(
            EditableValue("0532 111 22 33", Phone.TYPE_HOME, dataId = 1),
            EditableValue("0212 444 55 66", Phone.TYPE_WORK, dataId = 2),
        ),
        emails = listOf(EditableValue("ali@eski.com", 1, dataId = 3)),
        events = listOf(EditableValue("1980-01-01", Event.TYPE_BIRTHDAY, dataId = 4)),
        groups = setOf(10),
    )

    @Test
    fun sourceWinsAndNothingIsLost() {
        val source = ContactDraft(
            given = "Ali Rıza", family = "Kaya", note = "",
            phones = listOf(EditableValue("+90 532 111 22 33", Phone.TYPE_MOBILE), EditableValue("0555 000 00 00", Phone.TYPE_MOBILE)),
            emails = listOf(EditableValue("ALI@eski.com", 2)),
            events = listOf(EditableValue("1981-02-02", Event.TYPE_BIRTHDAY)),
            groups = setOf(20),
        )
        val merged = mergeDrafts(target, source, customFields = true)
        assertEquals("Ali Rıza", merged.given)
        // No work in the source: the target's stays, and so does its note.
        assertEquals("Eski AŞ", merged.company)
        assertEquals("eski not", merged.note)
        // The shared number keeps its row and takes the source's type and writing.
        val shared = merged.phones.single { it.dataId == 1L }
        assertEquals(Phone.TYPE_MOBILE, shared.type)
        assertEquals("+90 532 111 22 33", shared.value)
        assertEquals(3, merged.phones.size)
        assertTrue(merged.phones.any { it.dataId == 2L })
        assertTrue(merged.phones.any { it.dataId == null && it.value == "0555 000 00 00" })
        assertEquals(1, merged.emails.size)
        // One birthday: the source's, in the target's row.
        assertEquals(listOf(EditableValue("1981-02-02", Event.TYPE_BIRTHDAY, dataId = 4)), merged.events)
        assertEquals(setOf(10L, 20L), merged.groups)
    }

    @Test
    fun emptySourceChangesNothing() {
        assertEquals(target, mergeDrafts(target, ContactDraft(), customFields = true))
    }

    @Test
    fun csvQuotesAndNumbersColumns() {
        val person = ExportPerson(
            ContactDraft(given = "Ayşe", family = "Yılmaz, Dr", phones = listOf(EditableValue("0532", 2), EditableValue("0533 222", 0, label = "Tekne"))),
            starred = true, labels = listOf("Aile"),
        )
        val lines = contactsCsv(listOf(person)).split("\r\n")
        val header = lines[0].split(",")
        assertEquals("Phone 2 - Value", header.last())
        assertTrue(lines[1].startsWith("Ayşe,,\"Yılmaz, Dr\","))
        assertTrue(lines[1].contains("Aile ::: * starred"))
        assertTrue(lines[1].endsWith("Mobile,0532,Tekne,0533 222"))
    }

    @Test
    fun vCardEscapesAndCarriesThePhoto() {
        val person = ExportPerson(
            ContactDraft(
                given = "Ayşe", family = "Yılmaz", note = "a; b, c",
                phones = listOf(EditableValue("0532 111 22 33", Phone.TYPE_MOBILE), EditableValue("+90 532 111 22 33", Phone.TYPE_MOBILE), EditableValue("0212", 0, label = "Ev 2")),
                events = listOf(EditableValue("1990-05-01", Event.TYPE_BIRTHDAY)),
            ),
            starred = false, labels = listOf("Aile"),
        )
        val card = vCard(person, byteArrayOf(1, 2, 3)).split("\r\n")
        assertEquals("BEGIN:VCARD", card.first())
        assertTrue("N:Yılmaz;Ayşe;;;" in card)
        assertTrue("TEL;TYPE=CELL:0532 111 22 33" in card)
        assertTrue("TEL;TYPE=X-Ev-2:0212" in card)
        assertEquals(1, card.count { it.startsWith("TEL;TYPE=CELL") })
        assertTrue("BDAY:1990-05-01" in card)
        assertTrue("NOTE:a\\; b\\, c" in card)
        assertTrue("CATEGORIES:Aile" in card)
        assertTrue("PHOTO;ENCODING=b;TYPE=JPEG:AQID" in card)
    }
}
