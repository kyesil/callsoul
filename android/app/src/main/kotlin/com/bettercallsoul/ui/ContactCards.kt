package com.bettercallsoul.ui

import android.content.Intent
import androidx.core.content.FileProvider
import com.bettercallsoul.container
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import com.bettercallsoul.calllog.ContactDraft
import com.bettercallsoul.calllog.ContactField
import com.bettercallsoul.calllog.EditableValue
import com.bettercallsoul.codes.ScanResult
import com.bettercallsoul.codes.ScanCodes
import com.bettercallsoul.codes.ScannedContact
import com.bettercallsoul.codes.ValueKind
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The new-contact draft a scanned business card fills in; null when the code isn't a card. */
fun draftFromCode(raw: String): ContactDraft? {
    val card = (ScanCodes.read(raw) as? ScanResult.Contact)?.card ?: return null
    return card.toDraft()
}

fun ScannedContact.toDraft(): ContactDraft {
    val hasParts = listOf(given, family, middle, prefix, suffix).any { !it.isNullOrBlank() }
    return ContactDraft(
        prefix = if (hasParts) prefix.orEmpty() else "",
        given = if (hasParts) given.orEmpty() else formattedName.orEmpty(),
        middle = if (hasParts) middle.orEmpty() else "",
        family = if (hasParts) family.orEmpty() else "",
        suffix = if (hasParts) suffix.orEmpty() else "",
        nickname = nickname.orEmpty(),
        phones = phones.map {
            EditableValue(
                it.value,
                when (it.kind) {
                    ValueKind.Mobile -> Phone.TYPE_MOBILE
                    ValueKind.Home -> Phone.TYPE_HOME
                    ValueKind.Work -> Phone.TYPE_WORK
                    ValueKind.Fax -> Phone.TYPE_FAX_WORK
                    ValueKind.Other -> Phone.TYPE_OTHER
                },
            )
        },
        emails = emails.map { EditableValue(it.value, placeType(it.kind, Email.TYPE_HOME, Email.TYPE_WORK, Email.TYPE_OTHER)) },
        addresses = addresses.map {
            EditableValue(it.value, placeType(it.kind, StructuredPostal.TYPE_HOME, StructuredPostal.TYPE_WORK, StructuredPostal.TYPE_OTHER))
        },
        events = listOfNotNull(birthday?.let { EditableValue(it, Event.TYPE_BIRTHDAY) }),
        websites = urls.map { EditableValue(it, Website.TYPE_HOMEPAGE) },
        company = organization.orEmpty(),
        department = department.orEmpty(),
        jobTitle = title.orEmpty(),
        note = note.orEmpty(),
    )
}

private fun placeType(kind: ValueKind, home: Int, work: Int, other: Int): Int = when (kind) {
    ValueKind.Work -> work
    ValueKind.Other, ValueKind.Fax -> other
    else -> home
}

/** "23 Nisan 1988", or "23 Nisan" for a date kept without its year; the raw text if it isn't a date. */
fun formatContactDate(context: Context, value: String): String {
    val locale = context.resources.configuration.locales[0]
    return runCatching {
        if (value.startsWith("--")) {
            MonthDay.parse(value).atYear(2000).format(DateTimeFormatter.ofPattern("d MMMM", locale))
        } else {
            LocalDate.parse(value.take(10)).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
        }
    }.getOrDefault(value)
}

/**
 * Sends the contact through the share sheet as a .vcf file, the format phones and mail apps read.
 * Only the latest shared card is kept in the cache. False when the card can't be read.
 */
internal suspend fun shareContact(context: Context, lookupKey: String, name: String, title: String): Boolean {
    val file = withContext(Dispatchers.IO) {
        val card = context.container.contacts.vcard(lookupKey) ?: return@withContext null
        val folder = File(context.cacheDir, "shared").apply {
            deleteRecursively()
            mkdirs()
        }
        val fileName = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(60).ifEmpty { "contact" }
        File(folder, "$fileName.vcf").apply { writeBytes(card) }
    } ?: return false
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/x-vcard")
        .putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, "${context.packageName}.logs", file))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, title))
    return true
}
