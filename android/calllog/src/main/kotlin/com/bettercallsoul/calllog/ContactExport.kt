package com.bettercallsoul.calllog

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import java.io.OutputStream
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Writes contacts to a file: as vCards (3.0, with full-size photos), which phones, Google and iCloud read, or as
 * a CSV in Google Contacts' own layout, which Google Contacts, Outlook and spreadsheets read.
 */
class ContactExport(context: Context, private val editor: ContactEditor) {
    enum class Format(val extension: String, val mimeType: String) {
        Vcf("vcf", "text/x-vcard"),
        Csv("csv", "text/csv"),
    }

    private val resolver = context.applicationContext.contentResolver

    /** Every contact in the address book, or those with a copy in [account]. */
    suspend fun contactIds(account: ContactAccount?): List<Long> = withContext(Dispatchers.IO) {
        if (account != null) return@withContext editor.contactIdsIn(account).toList()
        resolver.query(Contacts.CONTENT_URI, arrayOf(Contacts._ID), null, null, null)
            ?.use { buildList { while (it.moveToNext()) add(it.getLong(0)) } }.orEmpty()
    }

    /** Returns how many contacts went into the file. */
    suspend fun write(
        format: Format,
        contactIds: List<Long>,
        account: ContactAccount?,
        out: OutputStream,
        onEach: (Int) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        when (format) {
            Format.Vcf -> writeVcf(contactIds, account, out, onEach)
            Format.Csv -> writeCsv(contactIds, account, out, onEach)
        }
    }

    /** One vCard 3.0 after another, each with the contact's full-size photo. */
    private suspend fun writeVcf(contactIds: List<Long>, account: ContactAccount?, out: OutputStream, onEach: (Int) -> Unit): Int {
        var written = 0
        readPeople(contactIds, account, onEach) { person, contactId ->
            out.write(vCard(person, editor.photoBytes(contactId)).toByteArray(Charsets.UTF_8))
            written++
        }
        out.flush()
        return written
    }

    private suspend fun writeCsv(contactIds: List<Long>, account: ContactAccount?, out: OutputStream, onEach: (Int) -> Unit): Int {
        val people = ArrayList<ExportPerson>()
        readPeople(contactIds, account, onEach) { person, _ -> people += person }
        out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        out.write(contactsCsv(people).toByteArray(Charsets.UTF_8))
        out.flush()
        return people.size
    }

    /** Each contact that holds anything, with the names of its labels. */
    private suspend fun readPeople(
        contactIds: List<Long>,
        account: ContactAccount?,
        onEach: (Int) -> Unit,
        use: suspend (ExportPerson, Long) -> Unit,
    ) {
        val groupTitles = HashMap<ContactAccount, Map<Long, String>>()
        contactIds.forEachIndexed { index, id ->
            currentCoroutineContext().ensureActive()
            val contact = editor.snapshot(id, account)
            val draft = contact?.draft
            if (draft != null && (draft.displayName.isNotBlank() || draft.phones.isNotEmpty() || draft.emails.isNotEmpty() || draft.company.isNotBlank())) {
                val titles = groupTitles.getOrPut(contact.account) { editor.groups(contact.account).associate { it.id to it.title } }
                use(ExportPerson(draft, contact.starred, draft.groups.mapNotNull(titles::get)), id)
            }
            onEach(index + 1)
        }
    }
}

internal class ExportPerson(val draft: ContactDraft, val starred: Boolean, val labels: List<String>)

/** Google Contacts' CSV: fixed columns, then numbered ones for as many values as the longest contact has. */
internal fun contactsCsv(people: List<ExportPerson>): String {
    fun distinct(field: ContactField, values: List<EditableValue>) = values.filter { it.value.isNotBlank() }.distinctBy { value ->
        if (field == ContactField.Phone) DuplicateContacts.numberKey(value.value) ?: value.value.trim() else value.value.trim().lowercase()
    }
    class Line(val person: ExportPerson) {
        val events = person.draft.events.filter { it.type != Event.TYPE_BIRTHDAY }
        val values = mapOf(
            ContactField.Email to distinct(ContactField.Email, person.draft.emails),
            ContactField.Phone to distinct(ContactField.Phone, person.draft.phones),
            ContactField.Address to distinct(ContactField.Address, person.draft.addresses),
            ContactField.Relation to distinct(ContactField.Relation, person.draft.relations),
            ContactField.Website to distinct(ContactField.Website, person.draft.websites),
            ContactField.Event to distinct(ContactField.Event, events),
        )
    }
    val lines = people.map(::Line)
    fun most(field: ContactField) = lines.maxOfOrNull { it.values.getValue(field).size } ?: 0
    val customs = lines.maxOfOrNull { it.person.draft.customFields.size } ?: 0
    val repeating = listOf(
        ContactField.Email to "E-mail",
        ContactField.Phone to "Phone",
        ContactField.Address to "Address",
        ContactField.Relation to "Relation",
        ContactField.Website to "Website",
        ContactField.Event to "Event",
    ).map { (field, title) -> Triple(field, title, most(field)) }

    val header = buildList {
        addAll(FIXED_COLUMNS)
        repeating.forEach { (field, title, count) ->
            for (n in 1..count) {
                add("$title $n - Label")
                add(if (field == ContactField.Address) "$title $n - Formatted" else "$title $n - Value")
            }
        }
        for (n in 1..customs) {
            add("Custom Field $n - Label")
            add("Custom Field $n - Value")
        }
    }
    val rows = lines.map { line ->
        val draft = line.person.draft
        val birthday = draft.events.firstOrNull { it.type == Event.TYPE_BIRTHDAY }?.value.orEmpty()
        val labels = (line.person.labels + if (line.person.starred) listOf("* starred") else emptyList()).joinToString(" ::: ")
        buildList {
            addAll(
                listOf(
                    draft.given, draft.middle, draft.family, draft.phoneticGiven, draft.phoneticMiddle, draft.phoneticFamily,
                    draft.prefix, draft.suffix, draft.nickname, "", draft.company, draft.jobTitle, draft.department,
                    birthday, draft.note, "", labels,
                ),
            )
            repeating.forEach { (field, _, count) ->
                val values = line.values.getValue(field)
                for (n in 0 until count) {
                    val value = values.getOrNull(n)
                    add(value?.let { csvLabel(field, it) }.orEmpty())
                    add(value?.value.orEmpty())
                }
            }
            for (n in 0 until customs) {
                val field = draft.customFields.getOrNull(n)
                add(field?.label.orEmpty())
                add(field?.value.orEmpty())
            }
        }
    }
    return (listOf(header) + rows).joinToString("") { row -> row.joinToString(",", transform = ::csvCell) + "\r\n" }
}

private val FIXED_COLUMNS = listOf(
    "First Name", "Middle Name", "Last Name", "Phonetic First Name", "Phonetic Middle Name", "Phonetic Last Name",
    "Name Prefix", "Name Suffix", "Nickname", "File As", "Organization Name", "Organization Title",
    "Organization Department", "Birthday", "Notes", "Photo", "Labels",
)

private fun csvCell(text: String): String {
    val value = text.trim()
    return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}

/** The type in the English words Google Contacts writes and reads; a custom label as it is. */
private fun csvLabel(field: ContactField, value: EditableValue): String {
    if (value.type == 0) return value.label?.trim().orEmpty()
    val words = when (field) {
        ContactField.Phone -> mapOf(
            Phone.TYPE_MOBILE to "Mobile", Phone.TYPE_HOME to "Home", Phone.TYPE_WORK to "Work", Phone.TYPE_MAIN to "Main",
            Phone.TYPE_FAX_WORK to "Work Fax", Phone.TYPE_FAX_HOME to "Home Fax", Phone.TYPE_PAGER to "Pager",
            Phone.TYPE_WORK_MOBILE to "Work Mobile", Phone.TYPE_OTHER to "Other",
        )
        ContactField.Email -> mapOf(Email.TYPE_HOME to "Home", Email.TYPE_WORK to "Work", Email.TYPE_MOBILE to "Mobile", Email.TYPE_OTHER to "Other")
        ContactField.Address -> mapOf(StructuredPostal.TYPE_HOME to "Home", StructuredPostal.TYPE_WORK to "Work", StructuredPostal.TYPE_OTHER to "Other")
        ContactField.Website -> mapOf(
            Website.TYPE_HOMEPAGE to "Homepage", Website.TYPE_BLOG to "Blog", Website.TYPE_PROFILE to "Profile",
            Website.TYPE_HOME to "Home", Website.TYPE_WORK to "Work", Website.TYPE_FTP to "FTP", Website.TYPE_OTHER to "Other",
        )
        ContactField.Relation -> mapOf(
            Relation.TYPE_SPOUSE to "Spouse", Relation.TYPE_CHILD to "Child", Relation.TYPE_MOTHER to "Mother",
            Relation.TYPE_FATHER to "Father", Relation.TYPE_PARENT to "Parent", Relation.TYPE_BROTHER to "Brother",
            Relation.TYPE_SISTER to "Sister", Relation.TYPE_FRIEND to "Friend", Relation.TYPE_RELATIVE to "Relative",
            Relation.TYPE_MANAGER to "Manager", Relation.TYPE_ASSISTANT to "Assistant", Relation.TYPE_PARTNER to "Partner",
            Relation.TYPE_DOMESTIC_PARTNER to "Domestic Partner", Relation.TYPE_REFERRED_BY to "Referred By",
        )
        ContactField.Event -> mapOf(Event.TYPE_ANNIVERSARY to "Anniversary", Event.TYPE_OTHER to "Other")
        ContactField.Sip -> emptyMap()
    }
    return words[value.type] ?: "Other"
}

/** A vCard 3.0, in the forms Android's own importer reads back, labels as CATEGORIES. */
internal fun vCard(person: ExportPerson, photo: ByteArray?): String {
    val draft = person.draft
    val lines = ArrayList<String>()
    fun add(name: String, vararg values: String) {
        if (values.any(String::isNotBlank)) lines += name + ":" + values.joinToString(";") { vEscape(it.trim()) }
    }
    lines += "BEGIN:VCARD"
    lines += "VERSION:3.0"
    lines += "N:" + listOf(draft.family, draft.given, draft.middle, draft.prefix, draft.suffix).joinToString(";") { vEscape(it.trim()) }
    lines += "FN:" + vEscape(draft.displayName.ifBlank { draft.company.ifBlank { draft.phones.firstOrNull()?.value.orEmpty() } })
    add("X-PHONETIC-FIRST-NAME", draft.phoneticGiven)
    add("X-PHONETIC-MIDDLE-NAME", draft.phoneticMiddle)
    add("X-PHONETIC-LAST-NAME", draft.phoneticFamily)
    add("NICKNAME", draft.nickname)
    if (draft.company.isNotBlank() || draft.department.isNotBlank()) add("ORG", draft.company, draft.department)
    add("TITLE", draft.jobTitle)
    fun typed(name: String, field: ContactField, value: EditableValue) {
        val type = vType(field, value)
        add(if (type.isEmpty()) name else "$name;TYPE=$type", value.value)
    }
    draft.phones.distinctBy { DuplicateContacts.numberKey(it.value) ?: it.value.trim() }.forEach { typed("TEL", ContactField.Phone, it) }
    draft.emails.distinctBy { it.value.trim().lowercase() }.forEach { typed("EMAIL", ContactField.Email, it) }
    draft.addresses.distinctBy { it.value.trim().lowercase() }.forEach { value ->
        // Only the address as written: it goes where the street does, which every importer shows.
        val type = vType(ContactField.Address, value)
        lines += (if (type.isEmpty()) "ADR" else "ADR;TYPE=$type") + ":;;" + vEscape(value.value.trim()) + ";;;;"
    }
    draft.websites.distinctBy { it.value.trim() }.forEach { add("URL", it.value) }
    draft.sips.distinctBy { it.value.trim() }.forEach { add("X-SIP", it.value) }
    draft.events.forEach { event ->
        if (event.type == Event.TYPE_BIRTHDAY) {
            add("BDAY", event.value)
        } else {
            lines += "X-ANDROID-CUSTOM:vnd.android.cursor.item/contact_event;" + vEscape(event.value.trim()) + ";" + event.type + ";" + vEscape(event.label.orEmpty()) + ";;;;;;;;;;;;"
        }
    }
    draft.relations.forEach { relation ->
        lines += "X-ANDROID-CUSTOM:vnd.android.cursor.item/relation;" + vEscape(relation.value.trim()) + ";" + relation.type + ";" + vEscape(relation.label.orEmpty()) + ";;;;;;;;;;;;"
    }
    add("NOTE", draft.note)
    if (person.labels.isNotEmpty()) lines += "CATEGORIES:" + person.labels.joinToString(",") { vEscape(it) }
    // On one line: Android's importer doesn't read a folded photo.
    if (photo != null) lines += "PHOTO;ENCODING=b;TYPE=JPEG:" + Base64.getEncoder().encodeToString(photo)
    lines += "END:VCARD"
    return lines.joinToString("\r\n", postfix = "\r\n")
}

private fun vEscape(text: String): String =
    text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n")

/** The vCard TYPE for a value: the standard words where there is one, X- and the label for a custom one. */
private fun vType(field: ContactField, value: EditableValue): String {
    if (value.type == 0) return value.label?.trim()?.takeIf(String::isNotEmpty)?.let { "X-" + it.replace(Regex("[^\\p{L}\\p{N}-]+"), "-") }.orEmpty()
    return when (field) {
        ContactField.Phone -> when (value.type) {
            Phone.TYPE_MOBILE -> "CELL"
            Phone.TYPE_HOME -> "HOME"
            Phone.TYPE_WORK -> "WORK"
            Phone.TYPE_FAX_WORK -> "WORK,FAX"
            Phone.TYPE_FAX_HOME -> "HOME,FAX"
            Phone.TYPE_PAGER -> "PAGER"
            Phone.TYPE_MAIN -> "PREF"
            Phone.TYPE_WORK_MOBILE -> "WORK,CELL"
            else -> "VOICE"
        }
        ContactField.Email -> when (value.type) {
            Email.TYPE_HOME -> "HOME"
            Email.TYPE_WORK -> "WORK"
            Email.TYPE_MOBILE -> "CELL"
            else -> "INTERNET"
        }
        ContactField.Address -> when (value.type) {
            StructuredPostal.TYPE_HOME -> "HOME"
            StructuredPostal.TYPE_WORK -> "WORK"
            else -> ""
        }
        else -> ""
    }
}
