package com.bettercallsoul.calllog

import java.time.LocalDate
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Profile
import java.time.MonthDay
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import android.provider.ContactsContract.PhoneLookup
import androidx.core.database.getStringOrNull
import com.bettercallsoul.spam.T9
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class ContactRow(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
    val starred: Boolean,
    /** The favourite's place set by dragging, from 1; 0 or less for one never placed. */
    val pinned: Int = 0,
)

/**
 * The favourites in the order the user dragged them into. Ones never placed come after, in the
 * list's own order, so a new favourite goes to the end once the user has ordered them.
 */
fun List<ContactRow>.favoritesInOrder(): List<ContactRow> =
    filter { it.starred }.sortedBy { if (it.pinned > 0) it.pinned else Int.MAX_VALUE }

data class ContactPhone(val number: String, val label: String?)

/** A contact whose birthday is today or tomorrow ([today] tells which), with the number to call. */
data class Birthday(val contactId: Long, val name: String, val photoUri: String?, val number: String?, val today: Boolean)

/** How near a birthday is: today, tomorrow, or neither (null). */
enum class BirthdayNear { Today, Tomorrow }

fun birthdayNear(birthday: MonthDay?, now: LocalDate = LocalDate.now()): BirthdayNear? = when (birthday) {
    null -> null
    MonthDay.from(now) -> BirthdayNear.Today
    MonthDay.from(now.plusDays(1)) -> BirthdayNear.Tomorrow
    else -> null
}

/**
 * The day and month of a birthday as contacts apps store it: yyyy-MM-dd, --MM-dd (no year), or
 * dd.MM.yyyy. Null when it can't be read.
 */
fun birthdayOf(value: String): MonthDay? {
    val text = value.trim().substringBefore('T')
    val (month, day) = Regex("(\\d{1,2})-(\\d{1,2})$").find(text)?.destructured?.let { (m, d) -> m to d }
        ?: Regex("^(\\d{1,2})[./](\\d{1,2})[./]\\d{2,4}$").find(text)?.destructured?.let { (d, m) -> m to d }
        ?: return null
    return runCatching { MonthDay.of(month.toInt(), day.toInt()) }.getOrNull()
}

/** A value with what it is: "Doğum günü", "Eş", "İş". For a date the value is yyyy-MM-dd or --MM-dd. */
data class DetailItem(val value: String, val label: String)

/** Where contacts come from: an account on the phone, such as a Google account, the SIM or WhatsApp. */
data class ContactSource(val type: String?, val name: String?, val label: String) {
    /** For keeping the choice in settings. */
    val key: String get() = "${type.orEmpty()}|${name.orEmpty()}"
}

data class ContactDetail(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
    val phones: List<ContactPhone>,
    val emails: List<DetailItem>,
    val addresses: List<DetailItem>,
    val company: String?,
    val jobTitle: String?,
    val department: String?,
    val note: String?,
    /** Rows WhatsApp adds to a contact it knows, one per number and kind of call. */
    val whatsApp: List<WhatsAppAction> = emptyList(),
    val starred: Boolean = false,
    val nickname: String? = null,
    val events: List<DetailItem> = emptyList(),
    val relations: List<DetailItem> = emptyList(),
    val websites: List<DetailItem> = emptyList(),
    val sips: List<DetailItem> = emptyList(),
    /** Google Contacts custom fields: the label is the user's own. */
    val customFields: List<DetailItem> = emptyList(),
    /** Google Contacts labels the contact is in. */
    val labels: List<String> = emptyList(),
    /** The accounts holding a copy of this contact, as names to show. */
    val sources: List<String> = emptyList(),
    val birthday: MonthDay? = null,
    /** The contact's own ringtone, which Telecom plays for their calls; null rings the phone's default. */
    val ringtone: String? = null,
)

enum class WhatsAppKind { Voice, Video }

/** Opening [dataUri] with [mimeType] in [packageName] starts the call, as the system contacts app does. */
data class WhatsAppAction(
    val kind: WhatsAppKind,
    /** The WhatsApp number this action calls, as +digits. */
    val account: String,
    val dataUri: String,
    val mimeType: String,
    val packageName: String,
)

/** What the call screens need to know about a saved number. */
data class ContactMatch(
    val id: Long,
    val name: String,
    val photoUri: String?,
    val company: String?,
    val jobTitle: String?,
    val note: String?,
)

/** A contact as the search sees it. [details] are the e-mail, company and nickname lines. */
data class SearchableContact(
    val id: Long,
    val name: String,
    val photoUri: String?,
    val numbers: List<String>,
    val details: List<String>,
)

data class SearchHit(val contactId: Long, val name: String, val number: String, val photoUri: String? = null)

class ContactsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Contacts deleted here. Ids aren't reused, so these stay out of the lists even if the phone is slow to drop them. */
    private val deleted = ConcurrentHashMap.newKeySet<Long>()

    /** Reads the lists again now, after a change made in this app, without waiting for Android to report it. */
    fun refresh(deletedContacts: Collection<Long> = emptyList()) {
        deleted += deletedContacts
        refreshes.tryEmit(Unit)
    }

    /** The contact as a vCard, as Android exports it: every field and the photo. */
    suspend fun vcard(lookupKey: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openInputStream(Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, lookupKey))?.use { it.readBytes() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun observe(): Flow<List<ContactRow>> = changes().map { list() }.flowOn(Dispatchers.IO)

    /** Every contact, those without a number too, re-read when the address book changes. */
    fun observeSearchable(): Flow<List<SearchableContact>> = changes().map { searchable() }.flowOn(Dispatchers.IO)

    private fun changes(): Flow<Unit> = merge(addressBookChanges(), refreshes).conflate()

    private fun addressBookChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(Contacts.CONTENT_URI, true, observer)
        resolver.registerContentObserver(Profile.CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    private fun searchable(): List<SearchableContact> {
        val kinds = arrayOf(
            StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE,
            Organization.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE, GroupMembership.CONTENT_ITEM_TYPE,
        )
        val labels = labelTitles()
        val projection = arrayOf(Data.CONTACT_ID, Data.DISPLAY_NAME_PRIMARY, Data.PHOTO_THUMBNAIL_URI, Data.MIMETYPE, Data.DATA1)
        val cursor = resolver.query(
            Data.CONTENT_URI, projection, "${Data.MIMETYPE} IN (${kinds.joinToString { "?" }})", kinds,
            "${Data.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC, ${Data.IS_SUPER_PRIMARY} DESC, ${Data._ID} ASC",
        ) ?: return emptyList()
        val contacts = LinkedHashMap<Long, SearchableContact>()
        cursor.use {
            while (it.moveToNext()) {
                val name = it.getStringOrNull(1)?.takeIf(String::isNotBlank) ?: continue
                val id = it.getLong(0)
                val contact = contacts.getOrPut(id) { SearchableContact(id, name, it.getStringOrNull(2), emptyList(), emptyList()) }
                val value = it.getStringOrNull(4)?.trim().orEmpty()
                if (value.isEmpty()) continue
                when (it.getString(3)) {
                    StructuredName.CONTENT_ITEM_TYPE -> Unit
                    // A label is stored by its id; the search goes by its name.
                    GroupMembership.CONTENT_ITEM_TYPE -> labels[value.toLongOrNull()]?.let { title ->
                        if (title !in contact.details) contacts[id] = contact.copy(details = contact.details + title)
                    }
                    // Each app that knows a contact repeats the number, in its own format.
                    Phone.CONTENT_ITEM_TYPE -> {
                        val tail = value.filter(Char::isDigit).takeLast(10)
                        if (contact.numbers.none { number -> number.filter(Char::isDigit).takeLast(10) == tail }) {
                            contacts[id] = contact.copy(numbers = contact.numbers + value)
                        }
                    }
                    else -> if (value !in contact.details) contacts[id] = contact.copy(details = contact.details + value)
                }
            }
        }
        return contacts.values.filterNot { it.id in deleted }
    }

    /** The user's own labels by id, leaving out the system ones (My Contacts, Starred). */
    private fun labelTitles(): Map<Long, String> = resolver.query(
        Groups.CONTENT_URI, arrayOf(Groups._ID, Groups.TITLE),
        "${Groups.SYSTEM_ID} IS NULL AND ${Groups.AUTO_ADD} = 0 AND ${Groups.FAVORITES} = 0 AND ${Groups.DELETED} = 0", null, null,
    )?.use { buildMap { while (it.moveToNext()) it.clean(1)?.let { title -> put(it.getLong(0), title) } } }.orEmpty()

    /**
     * The phone owner's own card, Android's "me" profile, which Samsung's and Google's contacts apps
     * fill in too. Null until one is made.
     */
    fun observeProfile(): Flow<ContactRow?> = changes().map { profile() }.flowOn(Dispatchers.IO)

    private fun profile(): ContactRow? = runCatching {
        val projection = arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_THUMBNAIL_URI)
        resolver.query(Profile.CONTENT_URI, projection, null, null, null)?.use {
            if (it.moveToFirst()) ContactRow(it.getLong(0), it.getStringOrNull(1).orEmpty(), it.getStringOrNull(2).orEmpty(), it.getStringOrNull(3), false) else null
        }
    }.getOrNull()

    /** The contacts whose birthday is today or tomorrow, today's first, re-read when the address book changes. */
    fun observeBirthdays(): Flow<List<Birthday>> = changes().map { birthdays(LocalDate.now()) }.flowOn(Dispatchers.IO)

    private fun birthdays(now: LocalDate): List<Birthday> {
        val projection = arrayOf(Data.CONTACT_ID, Data.DISPLAY_NAME_PRIMARY, Data.PHOTO_THUMBNAIL_URI, Event.START_DATE)
        val found = resolver.query(
            Data.CONTENT_URI, projection, "${Data.MIMETYPE} = ? AND ${Event.TYPE} = ?",
            arrayOf(Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString()), null,
        )?.use {
            buildList {
                while (it.moveToNext()) {
                    if (it.getLong(0) in deleted) continue
                    val near = birthdayNear(birthdayOf(it.getStringOrNull(3).orEmpty()), now) ?: continue
                    add(Birthday(it.getLong(0), it.getStringOrNull(1) ?: continue, it.getStringOrNull(2), null, near == BirthdayNear.Today))
                }
            }
        }.orEmpty()
        return found.distinctBy { it.contactId }.sortedByDescending { it.today }
            .map { it.copy(number = phones(it.contactId).firstOrNull()?.number) }
    }

    private fun birthday(contactId: Long): MonthDay? = resolver.query(
        dataUri(contactId), arrayOf(Event.START_DATE), "$DATA_OF_KIND AND ${Event.TYPE} = ?",
        arrayOf(contactId.toString(), Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString()), null,
    )?.use { if (it.moveToFirst()) birthdayOf(it.getStringOrNull(0).orEmpty()) else null }

    /** The phone's own card lives in a store of its own, reached by its own addresses. */
    private fun contactsUri(contactId: Long): Uri = if (ContactsContract.isProfileId(contactId)) Profile.CONTENT_URI else Contacts.CONTENT_URI

    private fun dataUri(contactId: Long): Uri =
        if (ContactsContract.isProfileId(contactId)) Uri.withAppendedPath(Profile.CONTENT_URI, ContactsContract.Contacts.Data.CONTENT_DIRECTORY) else Data.CONTENT_URI

    private fun rawUri(contactId: Long): Uri = if (ContactsContract.isProfileId(contactId)) Profile.CONTENT_RAW_CONTACTS_URI else RawContacts.CONTENT_URI

    private fun list(): List<ContactRow> {
        val projection = arrayOf(
            Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY,
            Contacts.PHOTO_THUMBNAIL_URI, Contacts.STARRED, Contacts.PINNED,
        )
        val cursor = resolver.query(
            Contacts.CONTENT_URI, projection, "${Contacts.HAS_PHONE_NUMBER} = 1", null,
            "${Contacts.SORT_KEY_PRIMARY} COLLATE LOCALIZED ASC",
        ) ?: return emptyList()
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val name = it.getStringOrNull(2) ?: continue
                    if (it.getLong(0) in deleted) continue
                    add(ContactRow(it.getLong(0), it.getString(1), name, it.getStringOrNull(3), it.getInt(4) == 1, it.getInt(5)))
                }
            }
        }
    }

    /**
     * What the keypad shows while digits are typed: contacts whose name the digits spell on the
     * keys (T9), then contacts whose number contains them. One row per contact, each with the
     * number to call: the matching one, or the default for a name match.
     */
    suspend fun keypadSearch(typed: String, limit: Int = 3): List<SearchHit> = withContext(Dispatchers.IO) {
        if (typed.isBlank()) return@withContext emptyList()
        val projection = arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.PHOTO_THUMBNAIL_URI)
        val rows = resolver.query(
            Phone.CONTENT_URI, projection, null, null,
            "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC, ${Phone.IS_SUPER_PRIMARY} DESC, ${Phone._ID} ASC",
        )?.use {
            buildList {
                while (it.moveToNext()) {
                    val name = it.getStringOrNull(1) ?: continue
                    if (it.getLong(0) in deleted) continue
                    add(SearchHit(it.getLong(0), name, it.getStringOrNull(2).orEmpty(), it.getStringOrNull(3)))
                }
            }
        }.orEmpty()
        val byName = rows.filter { T9.matchesName(typed, it.name) }.distinctBy { it.contactId }
        val byNumber = rows.filter { T9.matchesNumber(typed, it.number) }.distinctBy { it.contactId }
        (byName + byNumber).distinctBy { it.contactId }.take(limit)
    }

    /** The first number of the contact with exactly this name, for a caller known only by name. */
    suspend fun numberOf(name: String): String? = withContext(Dispatchers.IO) {
        resolver.query(
            Phone.CONTENT_URI, arrayOf(Phone.NUMBER), "${Phone.DISPLAY_NAME_PRIMARY} = ?", arrayOf(name),
            "${Phone.IS_SUPER_PRIMARY} DESC, ${Phone._ID} ASC",
        )?.use { if (it.moveToFirst()) it.getStringOrNull(0) else null }
    }

    suspend fun match(number: String): ContactMatch? = withContext(Dispatchers.IO) {
        if (number.isBlank()) return@withContext null
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(PhoneLookup.CONTACT_ID, PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_URI)
        val found = resolver.query(uri, projection, null, null, null)?.use {
            if (it.moveToFirst()) Triple(it.getLong(0), it.getStringOrNull(1).orEmpty(), it.getStringOrNull(2)) else null
        } ?: return@withContext null
        val (id, name, photo) = found
        val work = organization(id)
        ContactMatch(id, name, photo, work?.company, work?.title, note(id))
    }

    suspend fun detail(contactId: Long): ContactDetail? = withContext(Dispatchers.IO) {
        if (contactId in deleted) return@withContext null
        val projection = arrayOf(Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_URI, Contacts.STARRED, Contacts.CUSTOM_RINGTONE)
        var starred = false
        var ringtone: String? = null
        val base = resolver.query(
            contactsUri(contactId), projection, "${Contacts._ID} = ?", arrayOf(contactId.toString()), null,
        )?.use {
            if (!it.moveToFirst()) return@use null
            starred = it.getInt(3) == 1
            ringtone = it.getStringOrNull(4)?.takeIf(String::isNotBlank)
            Triple(it.getString(0), it.getStringOrNull(1).orEmpty(), it.getStringOrNull(2))
        } ?: return@withContext null
        val work = organization(contactId)
        ContactDetail(
            id = contactId,
            lookupKey = base.first,
            name = base.second,
            photoUri = base.third,
            phones = phones(contactId),
            emails = items(contactId, ContactField.Email),
            addresses = items(contactId, ContactField.Address, valueColumn = StructuredPostal.FORMATTED_ADDRESS),
            company = work?.company,
            jobTitle = work?.title,
            department = work?.department,
            note = note(contactId),
            whatsApp = whatsApp(contactId),
            starred = starred,
            nickname = single(contactId, Nickname.CONTENT_ITEM_TYPE, Nickname.NAME),
            events = items(contactId, ContactField.Event).sortedBy { it.value.takeLast(5) },
            relations = items(contactId, ContactField.Relation),
            websites = items(contactId, ContactField.Website),
            sips = items(contactId, ContactField.Sip),
            customFields = customFields(contactId),
            labels = labels(contactId),
            birthday = birthday(contactId),
            ringtone = ringtone,
            sources = rawAccounts(contactId).map { (type, name) -> sourceLabel(type, name) }.distinct(),
        )
    }

    /** Every account that holds contacts, the phone's own storage first. */
    suspend fun sources(): List<ContactSource> = withContext(Dispatchers.IO) {
        val accounts = resolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
            "${RawContacts.DELETED} = 0", null, null,
        )?.use { buildSet { while (it.moveToNext()) add(it.getStringOrNull(0) to it.getStringOrNull(1)) } }.orEmpty()
        accounts.map { (type, name) -> ContactSource(type, name, sourceLabel(type, name)) }
            .sortedWith(compareBy({ it.type != null && it.label != appContext.getString(R.string.source_phone) }, { it.type != GOOGLE }, { it.label }))
    }

    /** The contacts with a copy in [source]. */
    suspend fun contactIdsIn(source: ContactSource): Set<Long> = withContext(Dispatchers.IO) {
        val (selection, args) = when {
            source.type == null -> "${RawContacts.ACCOUNT_TYPE} IS NULL" to emptyArray()
            source.name == null -> "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} IS NULL" to arrayOf(source.type)
            else -> "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ?" to arrayOf(source.type, source.name)
        }
        resolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts.CONTACT_ID), "$selection AND ${RawContacts.DELETED} = 0", args, null,
        )?.use { buildSet { while (it.moveToNext()) add(it.getLong(0)) } }.orEmpty()
    }

    private fun rawAccounts(contactId: Long): List<Pair<String?, String?>> = resolver.query(
        rawUri(contactId), arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
        "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0", arrayOf(contactId.toString()), null,
    )?.use { buildList { while (it.moveToNext()) add(it.getStringOrNull(0) to it.getStringOrNull(1)) } }.orEmpty()

    /** A name people know for an account: the e-mail of a Google account, "SIM", "WhatsApp". */
    private fun sourceLabel(type: String?, name: String?): String = when {
        type == null -> appContext.getString(R.string.source_phone)
        type == GOOGLE -> name ?: "Google"
        type.contains("sim", ignoreCase = true) -> appContext.getString(R.string.source_sim)
        type.contains("local", ignoreCase = true) || type == "vnd.sec.contact.phone" -> appContext.getString(R.string.source_phone)
        else -> KNOWN_APPS[type] ?: authenticatorLabel(type) ?: name ?: type
    }

    private fun authenticatorLabel(type: String): String? = runCatching {
        val description = android.accounts.AccountManager.get(appContext).authenticatorTypes.firstOrNull { it.type == type }
            ?: return@runCatching null
        appContext.packageManager.getResourcesForApplication(description.packageName).getString(description.labelId)
    }.getOrNull()?.takeIf(String::isNotBlank)

    /** Values of a repeating kind with their type labels, one row per distinct value. */
    private fun items(contactId: Long, field: ContactField, valueColumn: String = Data.DATA1): List<DetailItem> {
        val cursor = resolver.query(
            dataUri(contactId), arrayOf(valueColumn, Data.DATA2, Data.DATA3), DATA_OF_KIND,
            arrayOf(contactId.toString(), field.mimeType), "${Data._ID} ASC",
        ) ?: return emptyList()
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val value = it.clean(0) ?: continue
                    add(DetailItem(value, contactTypeLabel(appContext, field, it.getStringOrNull(1)?.toIntOrNull() ?: field.defaultType, it.getStringOrNull(2))))
                }
            }.distinctBy { item -> item.value }
        }
    }

    private fun single(contactId: Long, mimeType: String, column: String): String? = resolver.query(
        dataUri(contactId), arrayOf(column), DATA_OF_KIND, arrayOf(contactId.toString(), mimeType), null,
    )?.use { buildList { while (it.moveToNext()) it.clean(0)?.let(::add) }.firstOrNull() }

    private fun customFields(contactId: Long): List<DetailItem> = resolver.query(
        dataUri(contactId), arrayOf(Data.DATA1, Data.DATA2), DATA_OF_KIND, arrayOf(contactId.toString(), CUSTOM_FIELD), null,
    )?.use {
        buildList {
            while (it.moveToNext()) {
                val value = it.clean(1) ?: continue
                add(DetailItem(value, it.clean(0).orEmpty()))
            }
        }.distinct()
    }.orEmpty()

    private fun labels(contactId: Long): List<String> {
        val groupIds = resolver.query(
            dataUri(contactId), arrayOf(GroupMembership.GROUP_ROW_ID), DATA_OF_KIND,
            arrayOf(contactId.toString(), GroupMembership.CONTENT_ITEM_TYPE), null,
        )?.use { buildSet { while (it.moveToNext()) add(it.getLong(0)) } }.orEmpty()
        if (groupIds.isEmpty()) return emptyList()
        return resolver.query(
            Groups.CONTENT_URI, arrayOf(Groups.TITLE),
            "${Groups._ID} IN (${groupIds.joinToString()}) AND ${Groups.SYSTEM_ID} IS NULL AND ${Groups.AUTO_ADD} = 0 AND ${Groups.FAVORITES} = 0",
            null, "${Groups.TITLE} COLLATE LOCALIZED ASC",
        )?.use { buildList { while (it.moveToNext()) it.clean(0)?.let(::add) } }.orEmpty().distinct()
    }

    /** A new favourite has no place yet, so it goes after the ones the user ordered. */
    suspend fun setStarred(contactId: Long, starred: Boolean): Unit = withContext(Dispatchers.IO) {
        val values = android.content.ContentValues().apply {
            put(Contacts.STARRED, if (starred) 1 else 0)
            put(Contacts.PINNED, ContactsContract.PinnedPositions.UNPINNED)
        }
        resolver.update(android.content.ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId), values, null, null)
    }

    /** Gives the contact their own ringtone, or with null takes it away so the phone's default rings. */
    suspend fun setRingtone(contactId: Long, ringtone: String?): Unit = withContext(Dispatchers.IO) {
        val values = android.content.ContentValues().apply {
            if (ringtone == null) putNull(Contacts.CUSTOM_RINGTONE) else put(Contacts.CUSTOM_RINGTONE, ringtone)
        }
        resolver.update(android.content.ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId), values, null, null)
    }

    /**
     * Keeps the favourites in this order, in the address book's own pinned places, which the
     * Google phone app uses for its favourites too.
     */
    suspend fun setFavoriteOrder(contactIds: List<Long>): Unit = withContext(Dispatchers.IO) {
        val operations = contactIds.mapIndexed { place, id ->
            android.content.ContentProviderOperation
                .newUpdate(android.content.ContentUris.withAppendedId(Contacts.CONTENT_URI, id))
                .withValue(Contacts.PINNED, place + 1)
                .build()
        }
        resolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(operations))
    }

    /** The default number first (the one the user marked), then the others in the order they were added. */
    suspend fun numbers(contactId: Long): List<String> = withContext(Dispatchers.IO) {
        phones(contactId).map { it.number }
    }

    private fun phones(contactId: Long): List<ContactPhone> {
        val projection = arrayOf(Phone.NUMBER, Phone.TYPE, Phone.LABEL)
        val cursor = resolver.query(
            dataUri(contactId), projection, DATA_OF_KIND, arrayOf(contactId.toString(), Phone.CONTENT_ITEM_TYPE),
            "${Phone.IS_SUPER_PRIMARY} DESC, ${Phone.IS_PRIMARY} DESC, ${Phone._ID} ASC",
        ) ?: return emptyList()
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val label = contactTypeLabel(appContext, ContactField.Phone, it.getInt(1), it.getStringOrNull(2))
                    add(ContactPhone(it.getString(0), label))
                }
            }.distinctBy { phone -> phone.number.filter(Char::isDigit).takeLast(10) }
        }
    }

    /** How to reach [number] on WhatsApp, if it belongs to a contact WhatsApp has marked as its own. */
    suspend fun whatsAppActions(number: String): List<WhatsAppAction> = withContext(Dispatchers.IO) {
        val contact = match(number) ?: return@withContext emptyList()
        val tail = number.filter(Char::isDigit).takeLast(10)
        whatsApp(contact.id).filter { it.account.filter(Char::isDigit).takeLast(10) == tail }
    }

    private fun whatsApp(contactId: Long): List<WhatsAppAction> {
        val kinds = WHATSAPP_MIME_TYPES
        val cursor = resolver.query(
            dataUri(contactId), arrayOf(Data._ID, Data.MIMETYPE, Data.DATA1),
            "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} IN (${kinds.keys.joinToString { "?" }})",
            arrayOf(contactId.toString()) + kinds.keys, "${Data._ID} ASC",
        ) ?: return emptyList()
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val mime = it.getString(1)
                    val (kind, pkg) = kinds.getValue(mime)
                    add(
                        WhatsAppAction(
                            kind = kind,
                            account = it.clean(2)?.substringBefore('@')?.filter(Char::isDigit)?.let { d -> "+$d" }.orEmpty(),
                            dataUri = android.content.ContentUris.withAppendedId(Data.CONTENT_URI, it.getLong(0)).toString(),
                            mimeType = mime,
                            packageName = pkg,
                        ),
                    )
                }
            }
        }
    }

    private data class Work(val company: String?, val title: String?, val department: String?)

    private fun organization(contactId: Long): Work? {
        val projection = arrayOf(Organization.COMPANY, Organization.TITLE, Organization.DEPARTMENT)
        return resolver.query(
            dataUri(contactId), projection, DATA_OF_KIND,
            arrayOf(contactId.toString(), Organization.CONTENT_ITEM_TYPE), null,
        )?.use {
            if (!it.moveToFirst()) return@use null
            Work(it.clean(0), it.clean(1), it.clean(2)).takeIf { w -> w.company != null || w.title != null || w.department != null }
        }
    }

    private fun note(contactId: Long): String? = resolver.query(
        dataUri(contactId), arrayOf(Note.NOTE), DATA_OF_KIND,
        arrayOf(contactId.toString(), Note.CONTENT_ITEM_TYPE), null,
    )?.use { if (it.moveToFirst()) it.clean(0) else null }

    private fun android.database.Cursor.clean(index: Int): String? = getStringOrNull(index)?.trim()?.ifEmpty { null }

    private companion object {
        val WHATSAPP_MIME_TYPES = mapOf(
            "vnd.android.cursor.item/vnd.com.whatsapp.voip.call" to (WhatsAppKind.Voice to "com.whatsapp"),
            "vnd.android.cursor.item/vnd.com.whatsapp.video.call" to (WhatsAppKind.Video to "com.whatsapp"),
            "vnd.android.cursor.item/vnd.com.whatsapp.w4b.voip.call" to (WhatsAppKind.Voice to "com.whatsapp.w4b"),
            "vnd.android.cursor.item/vnd.com.whatsapp.w4b.video.call" to (WhatsAppKind.Video to "com.whatsapp.w4b"),
        )
        const val DATA_OF_KIND = "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?"

        /** Account types of apps whose names Android won't tell us without seeing their package. */
        val KNOWN_APPS = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "org.telegram.messenger" to "Telegram",
            "org.thunderdog.challegram" to "Telegram X",
            "com.viber.voip" to "Viber",
            "com.skype.contacts.sync" to "Skype",
            "com.skype.raider" to "Skype",
            "org.thoughtcrime.securesms" to "Signal",
            "com.microsoft.office.outlook.USER_ACCOUNT" to "Outlook",
            "com.google.android.gm.exchange" to "Exchange",
            "com.osp.app.signin" to "Samsung",
            "com.huawei.hwid" to "Huawei",
            "com.hihonor.id" to "Honor",
            "com.xiaomi" to "Xiaomi",
        )
    }
}
