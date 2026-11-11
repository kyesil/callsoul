package com.bettercallsoul.calllog

import android.content.ContentValues
import android.provider.ContactsContract.Profile
import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Identity
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.SipAddress
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import androidx.core.database.getStringOrNull
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val GOOGLE = "com.google"
private const val PHOTO_SIZE = 720

/** Google's own kind for the "custom field" of Google Contacts: DATA1 is the label, DATA2 the value. */
internal const val CUSTOM_FIELD = "vnd.com.google.cursor.item/contact_user_defined_field"

/**
 * A list, because the sync adapter's supportsUploading flag can't be trusted: Telegram and Zoom set
 * it too, and their copies of a contact belong to those apps.
 */
private val WRITABLE_ACCOUNTS = setOf(
    GOOGLE,
    SAMSUNG_PHONE,
    // Samsung account.
    "com.osp.app.signin",
    // Exchange and Outlook.
    "com.google.android.gm.exchange",
    "com.android.exchange",
    "com.samsung.android.exchange",
    "com.microsoft.office.outlook.USER_ACCOUNT",
    // CardDAV, the way iCloud contacts come to Android (DAVx⁵ and the like).
    "at.bitfire.davdroid.address_book",
    "bitfire.at.davdroid",
)

internal const val SAMSUNG_PHONE = "vnd.sec.contact.phone"

/** Phone storage: no account, or the phone maker's own for it (Samsung's, "…local…" on others). */
fun isPhoneStorage(type: String?): Boolean =
    type == null || type == SAMSUNG_PHONE || type.contains("local", ignoreCase = true)

/**
 * An address book of the user's own, which contacts can be written to and moved between: the phone,
 * Google, a Samsung, Outlook or iCloud account. Never the SIM, never an app's copy (WhatsApp, Telegram).
 */
fun isWritableAccount(type: String?): Boolean = when {
    type == null -> true
    type.contains("sim", ignoreCase = true) -> false
    type in WRITABLE_ACCOUNTS || isPhoneStorage(type) -> true
    else -> type.contains("icloud", ignoreCase = true) || type.contains("carddav", ignoreCase = true)
}

data class ContactAccount(val name: String?, val type: String?) {
    val isGoogle: Boolean get() = type == GOOGLE
}

/** An address book contacts move between: [label] names it ("Phone", an e-mail, "Outlook"), [detail] says more. */
data class ContactStore(
    val account: ContactAccount,
    val label: String,
    val detail: String?,
    /** An account of this kind is on the phone, but Android shows it to the app only once the user picks it. */
    val needsAccess: Boolean = false,
) {
    val isPhone: Boolean get() = isPhoneStorage(account.type)
}

/**
 * The contacts of [from] to go into [to]: [matches] holds those already there under the same number
 * (or, with no number in common, the same e-mail), source raw contact to the one it merges into.
 */
class MovePlan(val from: ContactAccount, val to: ContactAccount, val rawIds: List<Long>, val matches: Map<Long, Long>)

/**
 * The fields that repeat. They share the row layout value (DATA1), type (DATA2) and custom label
 * (DATA3); the custom type (0) is kept when a contact already has it.
 */
enum class ContactField(val mimeType: String, val defaultType: Int, val types: List<Int>) {
    Phone(
        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
        listOf(
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME,
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK,
            ContactsContract.CommonDataKinds.Phone.TYPE_MAIN,
            ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK,
            ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME,
            ContactsContract.CommonDataKinds.Phone.TYPE_PAGER,
            ContactsContract.CommonDataKinds.Phone.TYPE_OTHER,
        ),
    ),
    Email(
        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Email.TYPE_HOME,
        listOf(
            ContactsContract.CommonDataKinds.Email.TYPE_HOME,
            ContactsContract.CommonDataKinds.Email.TYPE_WORK,
            ContactsContract.CommonDataKinds.Email.TYPE_OTHER,
        ),
    ),
    Address(
        StructuredPostal.CONTENT_ITEM_TYPE,
        StructuredPostal.TYPE_HOME,
        listOf(StructuredPostal.TYPE_HOME, StructuredPostal.TYPE_WORK, StructuredPostal.TYPE_OTHER),
    ),
    /** A date: "yyyy-MM-dd", or "--MM-dd" without the year, as Google Contacts stores it. */
    Event(
        ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY,
        listOf(
            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY,
            ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY,
            ContactsContract.CommonDataKinds.Event.TYPE_OTHER,
        ),
    ),
    Relation(
        ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Relation.TYPE_SPOUSE,
        listOf(
            ContactsContract.CommonDataKinds.Relation.TYPE_SPOUSE,
            ContactsContract.CommonDataKinds.Relation.TYPE_PARTNER,
            ContactsContract.CommonDataKinds.Relation.TYPE_CHILD,
            ContactsContract.CommonDataKinds.Relation.TYPE_MOTHER,
            ContactsContract.CommonDataKinds.Relation.TYPE_FATHER,
            ContactsContract.CommonDataKinds.Relation.TYPE_PARENT,
            ContactsContract.CommonDataKinds.Relation.TYPE_BROTHER,
            ContactsContract.CommonDataKinds.Relation.TYPE_SISTER,
            ContactsContract.CommonDataKinds.Relation.TYPE_RELATIVE,
            ContactsContract.CommonDataKinds.Relation.TYPE_FRIEND,
            ContactsContract.CommonDataKinds.Relation.TYPE_MANAGER,
            ContactsContract.CommonDataKinds.Relation.TYPE_ASSISTANT,
            ContactsContract.CommonDataKinds.Relation.TYPE_DOMESTIC_PARTNER,
            ContactsContract.CommonDataKinds.Relation.TYPE_REFERRED_BY,
        ),
    ),
    Website(
        ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Website.TYPE_HOMEPAGE,
        listOf(
            ContactsContract.CommonDataKinds.Website.TYPE_HOMEPAGE,
            ContactsContract.CommonDataKinds.Website.TYPE_BLOG,
            ContactsContract.CommonDataKinds.Website.TYPE_PROFILE,
            ContactsContract.CommonDataKinds.Website.TYPE_WORK,
            ContactsContract.CommonDataKinds.Website.TYPE_OTHER,
        ),
    ),
    /** Internet call (SIP) address. */
    Sip(
        SipAddress.CONTENT_ITEM_TYPE,
        SipAddress.TYPE_HOME,
        listOf(SipAddress.TYPE_HOME, SipAddress.TYPE_WORK, SipAddress.TYPE_OTHER),
    ),
}

/** The app's own labels, so they follow the app's language rather than the phone's. */
internal fun contactTypeLabel(context: Context, field: ContactField, type: Int, label: String?): String {
    if (type == 0 && !label.isNullOrBlank()) return label
    val own = when (field) {
        ContactField.Phone -> when (type) {
            Phone.TYPE_MOBILE -> R.string.type_mobile
            Phone.TYPE_HOME -> R.string.type_home
            Phone.TYPE_WORK -> R.string.type_work
            Phone.TYPE_MAIN -> R.string.type_main
            Phone.TYPE_FAX_WORK -> R.string.type_fax_work
            Phone.TYPE_FAX_HOME -> R.string.type_fax_home
            Phone.TYPE_PAGER -> R.string.type_pager
            Phone.TYPE_OTHER -> R.string.type_other
            else -> null
        }
        ContactField.Email -> when (type) {
            Email.TYPE_HOME -> R.string.type_home
            Email.TYPE_WORK -> R.string.type_work
            Email.TYPE_OTHER -> R.string.type_other
            Email.TYPE_MOBILE -> R.string.type_mobile
            else -> null
        }
        ContactField.Address -> when (type) {
            StructuredPostal.TYPE_HOME -> R.string.type_home
            StructuredPostal.TYPE_WORK -> R.string.type_work
            StructuredPostal.TYPE_OTHER -> R.string.type_other
            else -> null
        }
        ContactField.Event -> when (type) {
            Event.TYPE_BIRTHDAY -> R.string.type_birthday
            Event.TYPE_ANNIVERSARY -> R.string.type_anniversary
            Event.TYPE_OTHER -> R.string.type_other
            else -> null
        }
        ContactField.Relation -> when (type) {
            Relation.TYPE_ASSISTANT -> R.string.rel_assistant
            Relation.TYPE_BROTHER -> R.string.rel_brother
            Relation.TYPE_CHILD -> R.string.rel_child
            Relation.TYPE_DOMESTIC_PARTNER -> R.string.rel_domestic_partner
            Relation.TYPE_FATHER -> R.string.rel_father
            Relation.TYPE_FRIEND -> R.string.rel_friend
            Relation.TYPE_MANAGER -> R.string.rel_manager
            Relation.TYPE_MOTHER -> R.string.rel_mother
            Relation.TYPE_PARENT -> R.string.rel_parent
            Relation.TYPE_PARTNER -> R.string.rel_partner
            Relation.TYPE_REFERRED_BY -> R.string.rel_referred_by
            Relation.TYPE_RELATIVE -> R.string.rel_relative
            Relation.TYPE_SISTER -> R.string.rel_sister
            Relation.TYPE_SPOUSE -> R.string.rel_spouse
            else -> null
        }
        ContactField.Website -> when (type) {
            Website.TYPE_HOMEPAGE -> R.string.type_homepage
            Website.TYPE_BLOG -> R.string.type_blog
            Website.TYPE_PROFILE -> R.string.type_profile
            Website.TYPE_HOME -> R.string.type_home
            Website.TYPE_WORK -> R.string.type_work
            else -> R.string.type_other
        }
        ContactField.Sip -> when (type) {
            SipAddress.TYPE_HOME -> R.string.type_home
            SipAddress.TYPE_WORK -> R.string.type_work
            else -> R.string.type_other
        }
    }
    return context.getString(own ?: R.string.type_other)
}

/** One value of a [ContactField]. [dataId] is null until the value is saved. */
data class EditableValue(val value: String, val type: Int, val label: String? = null, val dataId: Long? = null)

/** A Google Contacts custom field: a label of the user's choosing and its value. */
data class CustomField(val label: String, val value: String, val dataId: Long? = null)

/** A Google Contacts label ("group") the contact can belong to. */
data class ContactGroup(val id: Long, val title: String)

/** A label as the labels page lists it: in which account, and how many contacts it holds. */
data class LabelInfo(val id: Long, val title: String, val account: ContactAccount, val count: Int)

data class ContactDraft(
    val prefix: String = "",
    val given: String = "",
    val middle: String = "",
    val family: String = "",
    val suffix: String = "",
    val phoneticGiven: String = "",
    val phoneticMiddle: String = "",
    val phoneticFamily: String = "",
    val nickname: String = "",
    val phones: List<EditableValue> = emptyList(),
    val emails: List<EditableValue> = emptyList(),
    val addresses: List<EditableValue> = emptyList(),
    val events: List<EditableValue> = emptyList(),
    val relations: List<EditableValue> = emptyList(),
    val websites: List<EditableValue> = emptyList(),
    val sips: List<EditableValue> = emptyList(),
    val company: String = "",
    val jobTitle: String = "",
    val department: String = "",
    val note: String = "",
    val customFields: List<CustomField> = emptyList(),
    val groups: Set<Long> = emptySet(),
) {
    /** The name as the address book will show it, built from its parts. */
    val displayName: String
        get() = listOf(prefix, given, middle, family, suffix).map(String::trim).filter(String::isNotEmpty).joinToString(" ")

    val nameParts: List<String>
        get() = listOf(prefix, given, middle, family, suffix, phoneticGiven, phoneticMiddle, phoneticFamily).map(String::trim)

    fun values(field: ContactField): List<EditableValue> = when (field) {
        ContactField.Phone -> phones
        ContactField.Email -> emails
        ContactField.Address -> addresses
        ContactField.Event -> events
        ContactField.Relation -> relations
        ContactField.Website -> websites
        ContactField.Sip -> sips
    }

    fun with(field: ContactField, values: List<EditableValue>): ContactDraft = when (field) {
        ContactField.Phone -> copy(phones = values)
        ContactField.Email -> copy(emails = values)
        ContactField.Address -> copy(addresses = values)
        ContactField.Event -> copy(events = values)
        ContactField.Relation -> copy(relations = values)
        ContactField.Website -> copy(websites = values)
        ContactField.Sip -> copy(sips = values)
    }

    /**
     * The same fields with no row ids, ready to be written as a new contact. Labels belong to one
     * account, so they go unless [keepGroups].
     */
    fun detached(keepGroups: Boolean = false): ContactDraft {
        fun List<EditableValue>.free() = map { it.copy(dataId = null) }
        return copy(
            phones = phones.free(), emails = emails.free(), addresses = addresses.free(), events = events.free(),
            relations = relations.free(), websites = websites.free(), sips = sips.free(),
            customFields = customFields.map { it.copy(dataId = null) },
            groups = if (keepGroups) groups else emptySet(),
        )
    }
}

sealed interface PhotoEdit {
    data object Keep : PhotoEdit
    data object Remove : PhotoEdit
    data class Replace(val source: Uri) : PhotoEdit

    /** A photo already encoded as JPEG, such as one kept with a deleted contact. */
    class Bytes(val jpeg: ByteArray) : PhotoEdit
}

/** A contact as it was read, with the row ids that saving needs. */
class StoredContact internal constructor(
    val draft: ContactDraft,
    val account: ContactAccount,
    val photoUri: String?,
    val contactId: Long,
    val starred: Boolean,
    internal val rawContactIds: List<Long>,
    internal val primaryRawContactId: Long,
    internal val nameRows: List<Long>,
    internal val nicknameRow: Long?,
    internal val workRow: Long?,
    internal val noteRow: Long?,
    internal val photoRows: List<Long>,
    /** Group id to the membership row that puts the contact in it. */
    internal val memberships: Map<Long, Long>,
)

/**
 * Writes contacts. One contact can be several raw contacts from different accounts, and accounts
 * like WhatsApp or Telegram are read-only, so only the writable ones are read and changed. Rows are
 * updated one by one, which leaves anything this editor doesn't show alone.
 */
class ContactEditor(
    context: Context,
    /** After every write, with the ids of any contacts it removed. */
    private val onChanged: (deleted: Collection<Long>) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /**
     * Accounts that can hold a new contact: the phone itself first, then each Google account. Android
     * only lists accounts an app may see, so the ones that already sync contacts are added from the
     * address book's own tables.
     */
    fun accounts(): List<ContactAccount> {
        val fromManager = runCatching { AccountManager.get(appContext).getAccountsByType(GOOGLE).map { it.name } }.getOrDefault(emptyList())
        val fromGroups = resolver.query(
            Groups.CONTENT_URI, arrayOf(Groups.ACCOUNT_NAME), "${Groups.ACCOUNT_TYPE} = ?", arrayOf(GOOGLE), null,
        )?.use { buildList { while (it.moveToNext()) it.getStringOrNull(0)?.let(::add) } }.orEmpty()
        val fromContacts = resolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_NAME), "${RawContacts.ACCOUNT_TYPE} = ?", arrayOf(GOOGLE), null,
        )?.use { buildList { while (it.moveToNext()) it.getStringOrNull(0)?.let(::add) } }.orEmpty()
        val google = (fromManager + fromGroups + fromContacts).filter { it.isNotBlank() }.distinct()
        val phone = if (localAllowed() || google.isEmpty()) listOf(ContactAccount(null, null)) else emptyList()
        return phone + google.map { ContactAccount(it, GOOGLE) }
    }

    /**
     * Whether the phone itself may hold new contacts. With the default account set to the cloud
     * (Android 16 and later, Samsung's One UI 9 among them) Android refuses new contacts in phone
     * storage or on the SIM.
     */
    fun localAllowed(): Boolean = systemDefault()?.type == null

    /**
     * Android's own "default account for new contacts" (Android 16 and later): a cloud account, the
     * phone, or null when it isn't set or can't be read.
     */
    fun systemDefault(): ContactAccount? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return null
        return runCatching {
            val chosen = RawContacts.DefaultAccount.getDefaultAccountForNewContacts(resolver)
            when (chosen.state) {
                RawContacts.DefaultAccount.DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_CLOUD ->
                    chosen.account?.let { ContactAccount(it.name, it.type) }
                RawContacts.DefaultAccount.DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_LOCAL -> ContactAccount(null, null)
                else -> null
            }
        }.getOrNull()
    }

    /** Makes a label in [account], as Google Contacts does; it syncs with the account. Returns its id. */
    suspend fun createGroup(account: ContactAccount, title: String): Long? = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(Groups.ACCOUNT_NAME, account.name)
            put(Groups.ACCOUNT_TYPE, account.type)
            put(Groups.TITLE, title.trim())
            put(Groups.GROUP_VISIBLE, 1)
        }
        val id = resolver.insert(Groups.CONTENT_URI, values)?.let(ContentUris::parseId)
        uploadSoon(account)
        id
    }

    /** Every label the user made, in any account, with how many contacts are in it. */
    suspend fun allLabels(): List<LabelInfo> = withContext(Dispatchers.IO) {
        resolver.query(
            Groups.CONTENT_SUMMARY_URI,
            arrayOf(Groups._ID, Groups.TITLE, Groups.ACCOUNT_NAME, Groups.ACCOUNT_TYPE, Groups.SUMMARY_COUNT),
            "${Groups.DELETED} = 0 AND ${Groups.SYSTEM_ID} IS NULL AND ${Groups.AUTO_ADD} = 0 AND ${Groups.FAVORITES} = 0",
            null, "${Groups.TITLE} COLLATE LOCALIZED ASC",
        )?.use {
            buildList {
                while (it.moveToNext()) {
                    val title = it.getStringOrNull(1)?.takeIf(String::isNotBlank) ?: continue
                    add(LabelInfo(it.getLong(0), title, ContactAccount(it.getStringOrNull(2), it.getStringOrNull(3)), it.getInt(4)))
                }
            }
        }.orEmpty()
    }

    suspend fun renameGroup(label: LabelInfo, title: String): Unit = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put(Groups.TITLE, title.trim()) }
        resolver.update(ContentUris.withAppendedId(Groups.CONTENT_URI, label.id), values, null, null)
        uploadSoon(label.account)
        onChanged(emptyList())
    }

    /** The label goes; the contacts in it stay, as in Google Contacts. */
    suspend fun deleteGroup(label: LabelInfo): Unit = withContext(Dispatchers.IO) {
        resolver.delete(ContentUris.withAppendedId(Groups.CONTENT_URI, label.id), null, null)
        uploadSoon(label.account)
        onChanged(emptyList())
    }

    /** The labels an account offers, leaving out the system ones (My Contacts, Starred). */
    suspend fun groups(account: ContactAccount): List<ContactGroup> = withContext(Dispatchers.IO) {
        if (account.name == null || account.type == null) return@withContext emptyList()
        resolver.query(
            Groups.CONTENT_URI, arrayOf(Groups._ID, Groups.TITLE),
            "${Groups.ACCOUNT_NAME} = ? AND ${Groups.ACCOUNT_TYPE} = ? AND ${Groups.DELETED} = 0 AND " +
                "${Groups.SYSTEM_ID} IS NULL AND ${Groups.AUTO_ADD} = 0 AND ${Groups.FAVORITES} = 0",
            arrayOf(account.name, account.type), "${Groups.TITLE} COLLATE LOCALIZED ASC",
        )?.use {
            buildList {
                while (it.moveToNext()) {
                    val title = it.getStringOrNull(1)?.takeIf(String::isNotBlank) ?: continue
                    add(ContactGroup(it.getLong(0), title))
                }
            }
        }.orEmpty()
    }

    fun typeLabel(field: ContactField, type: Int, label: String?): String = contactTypeLabel(appContext, field, type, label)

    /** Null when no account of this contact can be written to. */
    suspend fun load(contactId: Long): StoredContact? = withContext(Dispatchers.IO) { loadFrom(contactId, writableRawContacts(contactId)) }

    /**
     * The contact as one, for an export: what all its copies hold (those of WhatsApp or Telegram
     * too), or only its copies in [account].
     */
    suspend fun snapshot(contactId: Long, account: ContactAccount? = null): StoredContact? = withContext(Dispatchers.IO) {
        val raws = writableRawContacts(contactId, all = account == null).filter { raw ->
            account == null || raw.account == account || (isPhoneStorage(account.type) && isPhoneStorage(raw.account.type))
        }
        loadFrom(contactId, raws)
    }

    /** One raw contact alone, the copy of a contact in one account; null when it is gone. */
    suspend fun loadRaw(rawContactId: Long): StoredContact? = withContext(Dispatchers.IO) {
        val raw = resolver.query(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
            arrayOf(RawContacts.CONTACT_ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE, RawContacts.STARRED, RawContacts.DELETED),
            null, null, null,
        )?.use { if (it.moveToFirst() && it.getInt(4) == 0) Triple(it.getLong(0), ContactAccount(it.getStringOrNull(1), it.getStringOrNull(2)), it.getInt(3) == 1) else null }
            ?: return@withContext null
        val (contactId, account, starred) = raw
        loadFrom(contactId, listOf(RawContact(rawContactId, account)), starred)
    }

    private suspend fun loadFrom(contactId: Long, raws: List<RawContact>, rawStarred: Boolean? = null): StoredContact? {
        val primary = raws.firstOrNull { it.account.isGoogle } ?: raws.firstOrNull() ?: return null
        val rows = rows(raws.map { it.id })
        fun rowsOf(mimeType: String) = rows.filter { it.mimeType == mimeType }
        fun values(field: ContactField) = rowsOf(field.mimeType).filter { it.data(1).isNotEmpty() }.map {
            EditableValue(it.data(1), it.data(2).toIntOrNull() ?: field.defaultType, it.data(3).ifEmpty { null }, it.id)
        }

        val names = rowsOf(StructuredName.CONTENT_ITEM_TYPE)
        val name = names.firstOrNull { it.rawContactId == primary.id } ?: names.firstOrNull()
        val nickname = rowsOf(Nickname.CONTENT_ITEM_TYPE).firstOrNull { it.data(1).isNotEmpty() }
        val work = rowsOf(Organization.CONTENT_ITEM_TYPE).firstOrNull()
        val note = rowsOf(Note.CONTENT_ITEM_TYPE).firstOrNull { it.data(1).isNotEmpty() }
        val photos = rowsOf(Photo.CONTENT_ITEM_TYPE)
        val customs = rowsOf(CUSTOM_FIELD).filter { it.rawContactId == primary.id && (it.data(1).isNotEmpty() || it.data(2).isNotEmpty()) }
        val available = groups(primary.account).map { it.id }.toSet()
        val memberships = rowsOf(GroupMembership.CONTENT_ITEM_TYPE)
            .filter { it.rawContactId == primary.id }
            .mapNotNull { row -> row.data(1).toLongOrNull()?.takeIf(available::contains)?.let { it to row.id } }
            .toMap()

        // A name stored as a whole without its parts goes into the first name, so nothing is lost.
        val hasParts = name != null && (2..9).any { name.data(it).isNotEmpty() }
        return StoredContact(
            draft = ContactDraft(
                prefix = if (hasParts) name!!.data(4) else "",
                given = if (hasParts) name!!.data(2) else name?.data(1).orEmpty(),
                middle = if (hasParts) name!!.data(5) else "",
                family = if (hasParts) name!!.data(3) else "",
                suffix = if (hasParts) name!!.data(6) else "",
                phoneticGiven = name?.data(7).orEmpty(),
                phoneticMiddle = name?.data(8).orEmpty(),
                phoneticFamily = name?.data(9).orEmpty(),
                nickname = nickname?.data(1).orEmpty(),
                phones = values(ContactField.Phone),
                emails = values(ContactField.Email),
                addresses = values(ContactField.Address),
                events = values(ContactField.Event),
                relations = values(ContactField.Relation),
                websites = values(ContactField.Website),
                sips = values(ContactField.Sip),
                company = work?.data(1).orEmpty(),
                jobTitle = work?.data(4).orEmpty(),
                department = work?.data(5).orEmpty(),
                note = note?.data(1).orEmpty(),
                customFields = customs.map { CustomField(it.data(1), it.data(2), it.id) },
                groups = memberships.keys,
            ),
            account = primary.account,
            photoUri = photoUri(contactId),
            contactId = contactId,
            starred = rawStarred ?: starred(contactId),
            rawContactIds = raws.map { it.id },
            primaryRawContactId = primary.id,
            nameRows = names.map { it.id },
            nicknameRow = nickname?.id,
            workRow = work?.id,
            noteRow = note?.id,
            photoRows = photos.map { it.id },
            memberships = memberships,
        )
    }

    /**
     * Saving into a Google account is all it takes to sync: Android's own sync adapter does the rest.
     * Returns the new contact's id.
     */
    suspend fun create(
        draft: ContactDraft,
        account: ContactAccount,
        photo: PhotoEdit = PhotoEdit.Keep,
        starred: Boolean = false,
        profile: Boolean = false,
        notify: Boolean = true,
    ): Long? = withContext(Dispatchers.IO) {
            val rawContactId = insertRaw(draft, account, photo, starred, profile)
            uploadSoon(account)
            if (notify) onChanged(emptyList())
            rawContactId?.let(::contactIdOf)
        }

    /** Writes [draft] as a new raw contact in [account]; returns its id. */
    private fun insertRaw(draft: ContactDraft, account: ContactAccount, photo: PhotoEdit, starred: Boolean, profile: Boolean = false): Long? {
            val encoded = encoded(photo)
            val batch = Batch(rawContactId = null)
            // The owner's own card goes into Android's profile, which stays on the phone.
            batch.operations += if (profile) {
                ContentProviderOperation.newInsert(Profile.CONTENT_RAW_CONTACTS_URI).withValues(ContentValues()).build()
            } else {
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_NAME, account.name)
                    .withValue(RawContacts.ACCOUNT_TYPE, account.type)
                    .withValue(RawContacts.STARRED, if (starred) 1 else 0)
                    .build()
            }
            if (draft.nameParts.any(String::isNotEmpty)) batch.insert(StructuredName.CONTENT_ITEM_TYPE, *nameColumns(draft))
            batch.syncSingle(Nickname.CONTENT_ITEM_TYPE, Nickname.NAME, null, draft.nickname)
            ContactField.entries.forEach { batch.syncValues(it, emptyList(), draft.values(it)) }
            batch.syncWork(null, draft)
            batch.syncSingle(Note.CONTENT_ITEM_TYPE, Note.NOTE, null, draft.note)
            if (account.isGoogle && !profile) batch.syncCustom(emptyList(), draft.customFields)
            if (!profile) {
                defaultGroup(account)?.let { batch.insert(GroupMembership.CONTENT_ITEM_TYPE, GroupMembership.GROUP_ROW_ID to it) }
                draft.groups.forEach { batch.insert(GroupMembership.CONTENT_ITEM_TYPE, GroupMembership.GROUP_ROW_ID to it) }
            }
            encoded?.let(batch::insertPhoto)

            val results = resolver.applyBatch(ContactsContract.AUTHORITY, batch.operations)
            return results.first().uri?.let(ContentUris::parseId)
        }

    /**
     * Saves [edited] into another account: a new contact there with the same fields, photo and star,
     * and the old copies removed. Returns the new contact's id.
     */
    suspend fun move(stored: StoredContact, edited: ContactDraft, photo: PhotoEdit, to: ContactAccount, notify: Boolean = true): Long? =
        withContext(Dispatchers.IO) {
            val keptPhoto = if (photo == PhotoEdit.Keep) photoBytes(stored.contactId)?.let(PhotoEdit::Bytes) else null
            val draft = edited.detached(keepGroups = false).let { if (to.isGoogle) it else it.copy(customFields = emptyList()) }
            val id = create(draft, to, keptPhoto ?: photo, stored.starred, notify = notify) ?: error("contact not created")
            removeWritable(stored)
            if (notify) onChanged(emptyList())
            id
        }

    /**
     * The raw contacts in [account], leaving out those of the [except] contacts. Phone storage is one
     * whatever its account type: no account, Samsung's, or another maker's "local" one.
     */
    suspend fun rawIdsIn(account: ContactAccount, except: Set<Long> = emptySet()): List<Long> = withContext(Dispatchers.IO) {
        val (selection, args) = accountSelection(account)
        resolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts._ID, RawContacts.CONTACT_ID), "$selection AND ${RawContacts.DELETED} = 0", args, null,
        )?.use { buildList { while (it.moveToNext()) if (it.getLong(1) !in except) add(it.getLong(0)) } }.orEmpty()
    }

    /** The contacts with a copy in [account]. */
    suspend fun contactIdsIn(account: ContactAccount): Set<Long> = withContext(Dispatchers.IO) {
        val (selection, args) = accountSelection(account)
        resolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts.CONTACT_ID), "$selection AND ${RawContacts.DELETED} = 0", args, null,
        )?.use { buildSet { while (it.moveToNext()) add(it.getLong(0)) } }.orEmpty()
    }

    private fun accountSelection(account: ContactAccount): Pair<String, Array<String>> = when {
        isPhoneStorage(account.type) ->
            "(${RawContacts.ACCOUNT_TYPE} IS NULL OR ${RawContacts.ACCOUNT_TYPE} = ? OR ${RawContacts.ACCOUNT_TYPE} LIKE ?)" to arrayOf(SAMSUNG_PHONE, "%local%")
        account.name == null -> "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} IS NULL" to arrayOf(account.type!!)
        else -> "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ?" to arrayOf(account.type!!, account.name)
    }

    /**
     * What a move from [from] to [to] would do: each contact of [from] (but the [except] ones) and,
     * for those [to] already has, the one there it merges into. The same number makes a match; with
     * no number in common, the same e-mail.
     */
    suspend fun plan(from: ContactAccount, to: ContactAccount, except: Set<Long> = emptySet()): MovePlan = withContext(Dispatchers.IO) {
        val sources = rawIdsIn(from, except)
        val targets = rawIdsIn(to)
        val byNumber = HashMap<String, Long>()
        val byEmail = HashMap<String, Long>()
        val targetKeys = matchKeys(targets)
        targets.forEach { raw ->
            val keys = targetKeys[raw] ?: return@forEach
            keys.numbers.forEach { byNumber.putIfAbsent(it, raw) }
            keys.emails.forEach { byEmail.putIfAbsent(it, raw) }
        }
        val sourceKeys = matchKeys(sources)
        val matches = buildMap {
            sources.forEach { raw ->
                val keys = sourceKeys[raw] ?: return@forEach
                val match = keys.numbers.firstNotNullOfOrNull(byNumber::get) ?: keys.emails.firstNotNullOfOrNull(byEmail::get)
                if (match != null) put(raw, match)
            }
        }
        MovePlan(from, to, sources, matches)
    }

    private class MatchKeys {
        val numbers = LinkedHashSet<String>()
        val emails = LinkedHashSet<String>()
    }

    private fun matchKeys(rawIds: List<Long>): Map<Long, MatchKeys> {
        val keys = HashMap<Long, MatchKeys>()
        rawIds.chunked(500).forEach { chunk ->
            resolver.query(
                Data.CONTENT_URI, arrayOf(Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1),
                "${Data.RAW_CONTACT_ID} IN (${chunk.joinToString()}) AND ${Data.MIMETYPE} IN (?, ?)",
                arrayOf(Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE), null,
            )?.use {
                while (it.moveToNext()) {
                    val value = it.getStringOrNull(2)?.trim()?.takeIf(String::isNotEmpty) ?: continue
                    val key = keys.getOrPut(it.getLong(0)) { MatchKeys() }
                    if (it.getString(1) == Phone.CONTENT_ITEM_TYPE) {
                        DuplicateContacts.numberKey(value)?.let(key.numbers::add)
                    } else {
                        key.emails += value.lowercase()
                    }
                }
            }
        }
        return keys
    }

    /**
     * Moves one raw contact into [to]: a new one there with everything it holds (labels by name into a
     * Google account, chat addresses, ringtone), then the old one deleted. Writes no notice of change.
     */
    suspend fun moveRaw(rawContactId: Long, to: ContactAccount): Boolean = withContext(Dispatchers.IO) {
        val stored = loadRaw(rawContactId) ?: return@withContext false
        val draft = stored.draft.detached(keepGroups = false)
            .let { if (to.isGoogle) it else it.copy(customFields = emptyList()) }
            .copy(groups = groupsIn(to, stored))
        val photo = rawPhoto(rawContactId)?.let(PhotoEdit::Bytes) ?: PhotoEdit.Keep
        val created = insertRaw(draft, to, photo, stored.starred) ?: return@withContext false
        copyRest(rawContactId, created)
        deleteRaw(rawContactId)
        uploadSoon(to)
        true
    }

    /**
     * Merges one raw contact into [intoRawContactId], its own details taking precedence (see
     * [mergeDrafts]), then deletes it. Writes no notice of change.
     */
    suspend fun mergeRaw(rawContactId: Long, intoRawContactId: Long): Boolean = withContext(Dispatchers.IO) {
        val source = loadRaw(rawContactId) ?: return@withContext false
        val target = loadRaw(intoRawContactId) ?: return@withContext false
        val incoming = source.draft.copy(groups = groupsIn(target.account, source))
        val merged = mergeDrafts(target.draft, incoming, customFields = target.account.isGoogle)
        val photo = rawPhoto(rawContactId)?.let(PhotoEdit::Bytes) ?: PhotoEdit.Keep
        update(target, merged, photo, notify = false)
        if (source.starred && !target.starred) {
            val values = ContentValues().apply { put(RawContacts.STARRED, 1) }
            resolver.update(ContentUris.withAppendedId(RawContacts.CONTENT_URI, intoRawContactId), values, null, null)
        }
        copyRest(rawContactId, intoRawContactId)
        deleteRaw(rawContactId)
        true
    }

    /** The contact's labels as labels of the same name in a Google account [to], made there when missing. */
    private suspend fun groupsIn(to: ContactAccount, stored: StoredContact): Set<Long> {
        if (stored.draft.groups.isEmpty() || !to.isGoogle) return emptySet()
        if (to == stored.account) return stored.draft.groups
        val titles = groups(stored.account).filter { it.id in stored.draft.groups }.map { it.title }
        val there = groups(to).associateTo(HashMap()) { it.title to it.id }
        return titles.mapNotNullTo(HashSet()) { title -> there[title] ?: createGroup(to, title)?.also { there[title] = it } }
    }

    /** The photo of one raw contact: full size where there is one, else the small one in its row. */
    private fun rawPhoto(rawContactId: Long): ByteArray? {
        val raw = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId)
        val full = runCatching {
            resolver.openAssetFileDescriptor(Uri.withAppendedPath(raw, RawContacts.DisplayPhoto.CONTENT_DIRECTORY), "r")
                ?.use { it.createInputStream().use { stream -> stream.readBytes() } }
        }.getOrNull()
        if (full != null && full.isNotEmpty()) return full
        return resolver.query(
            Data.CONTENT_URI, arrayOf(Photo.PHOTO), "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
            arrayOf(rawContactId.toString(), Photo.CONTENT_ITEM_TYPE), null,
        )?.use { if (it.moveToFirst()) it.getBlob(0) else null }?.takeIf(ByteArray::isNotEmpty)
    }

    /** What the editor doesn't show but a move keeps: chat addresses, identities, ringtone, straight to voicemail. */
    private fun copyRest(fromRaw: Long, toRaw: Long) {
        val operations = arrayListOf<ContentProviderOperation>()
        val there = extraRows(toRaw).toSet()
        extraRows(fromRaw).filterNot(there::contains).forEach { (mimeType, columns) ->
            val builder = ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValue(Data.RAW_CONTACT_ID, toRaw)
                .withValue(Data.MIMETYPE, mimeType)
            columns.forEach { (column, value) -> builder.withValue(column, value) }
            operations += builder.build()
        }
        val (ringtone, voicemail) = options(fromRaw)
        val (theirRingtone, theirVoicemail) = options(toRaw)
        if ((ringtone != null && theirRingtone == null) || (voicemail && !theirVoicemail)) {
            operations += ContentProviderOperation.newUpdate(ContentUris.withAppendedId(RawContacts.CONTENT_URI, toRaw))
                .withValue(RawContacts.CUSTOM_RINGTONE, theirRingtone ?: ringtone)
                .withValue(RawContacts.SEND_TO_VOICEMAIL, if (voicemail || theirVoicemail) 1 else 0)
                .build()
        }
        if (operations.isNotEmpty()) resolver.applyBatch(ContactsContract.AUTHORITY, operations)
    }

    private fun extraRows(rawContactId: Long): List<Pair<String, List<Pair<String, String?>>>> {
        val columns = listOf(Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5, Data.DATA6)
        return resolver.query(
            Data.CONTENT_URI, arrayOf(Data.MIMETYPE) + columns,
            "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} IN (?, ?)",
            arrayOf(rawContactId.toString(), Im.CONTENT_ITEM_TYPE, Identity.CONTENT_ITEM_TYPE), null,
        )?.use {
            buildList {
                while (it.moveToNext()) add(it.getString(0) to columns.mapIndexed { i, column -> column to it.getStringOrNull(1 + i) })
            }
        }.orEmpty()
    }

    private fun options(rawContactId: Long): Pair<String?, Boolean> = resolver.query(
        ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
        arrayOf(RawContacts.CUSTOM_RINGTONE, RawContacts.SEND_TO_VOICEMAIL), null, null, null,
    )?.use { if (it.moveToFirst()) it.getStringOrNull(0) to (it.getInt(1) == 1) else null } ?: (null to false)

    private fun deleteRaw(rawContactId: Long) {
        resolver.delete(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId), null, null)
    }

    /** After a run of changes made with notify = false: the lists read the address book again once. */
    fun notifyChanged() = onChanged(emptyList())

    /** The contact's full-size photo as stored, or null when it has none. */
    suspend fun photoBytes(contactId: Long): ByteArray? = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        runCatching {
            ContactsContract.Contacts.openContactPhotoInputStream(resolver, uri, true)?.use { it.readBytes() }
        }.getOrNull()
    }

    /** Asks Google's sync to run now rather than when it next gets round to it. */
    private fun uploadSoon(account: ContactAccount) {
        val name = account.name ?: return
        val type = account.type ?: return
        if (!account.isGoogle) return
        val extras = Bundle().apply { putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true) }
        runCatching { ContentResolver.requestSync(Account(name, type), ContactsContract.AUTHORITY, extras) }
    }

    private fun encoded(photo: PhotoEdit): ByteArray? = when (photo) {
        is PhotoEdit.Replace -> encodePhoto(photo.source)
        is PhotoEdit.Bytes -> photo.jpeg
        else -> null
    }

    private fun contactIdOf(rawContactId: Long): Long? = resolver.query(
        ContentUris.withAppendedId(if (ContactsContract.isProfileId(rawContactId)) Profile.CONTENT_RAW_CONTACTS_URI else RawContacts.CONTENT_URI, rawContactId),
        arrayOf(RawContacts.CONTACT_ID), null, null, null,
    )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun starred(contactId: Long): Boolean = resolver.query(
        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
        arrayOf(ContactsContract.Contacts.STARRED), null, null, null,
    )?.use { it.moveToFirst() && it.getInt(0) == 1 } == true

    suspend fun update(stored: StoredContact, edited: ContactDraft, photo: PhotoEdit, notify: Boolean = true): Unit =
        withContext(Dispatchers.IO) {
            val encoded = encoded(photo)
            val before = stored.draft
            val batch = Batch(stored.primaryRawContactId)

            if (edited.nameParts != before.nameParts) {
                when {
                    edited.nameParts.all(String::isEmpty) -> stored.nameRows.forEach(batch::delete)
                    stored.nameRows.isEmpty() -> batch.insert(StructuredName.CONTENT_ITEM_TYPE, *nameColumns(edited))
                    else -> stored.nameRows.forEach { batch.update(it, *nameColumns(edited)) }
                }
            }
            if (edited.nickname.trim() != before.nickname) {
                batch.syncSingle(Nickname.CONTENT_ITEM_TYPE, Nickname.NAME, stored.nicknameRow, edited.nickname)
            }
            ContactField.entries.forEach { batch.syncValues(it, before.values(it), edited.values(it)) }
            if (edited.work != before.work) batch.syncWork(stored.workRow, edited)
            if (edited.note.trim() != before.note) batch.syncSingle(Note.CONTENT_ITEM_TYPE, Note.NOTE, stored.noteRow, edited.note)
            if (stored.account.isGoogle) batch.syncCustom(before.customFields, edited.customFields)
            (edited.groups - before.groups).forEach { batch.insert(GroupMembership.CONTENT_ITEM_TYPE, GroupMembership.GROUP_ROW_ID to it) }
            (before.groups - edited.groups).forEach { group -> stored.memberships[group]?.let(batch::delete) }
            if (photo != PhotoEdit.Keep) stored.photoRows.forEach(batch::delete)
            encoded?.let(batch::insertPhoto)

            if (batch.operations.isNotEmpty()) resolver.applyBatch(ContactsContract.AUTHORITY, batch.operations)
            uploadSoon(stored.account)
            if (notify) onChanged(emptyList())
        }

    /**
     * Deletes the whole contact, as the phone's own contacts app does, the copies WhatsApp or Telegram
     * keep of it included: left behind, one of those would go on showing the contact in every list
     * until that app gets round to removing it.
     */
    suspend fun deleteContact(contactId: Long, notify: Boolean = true): Unit = withContext(Dispatchers.IO) {
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId), null, null)
        if (notify) onChanged(listOf(contactId))
    }

    /** For a move: the copies in the other apps stay and join the contact in its new account. */
    private fun removeWritable(stored: StoredContact) {
        val operations = stored.rawContactIds.mapTo(ArrayList()) {
            ContentProviderOperation.newDelete(ContentUris.withAppendedId(RawContacts.CONTENT_URI, it)).build()
        }
        resolver.applyBatch(ContactsContract.AUTHORITY, operations)
    }

    private val ContactDraft.work: List<String> get() = listOf(company.trim(), jobTitle.trim(), department.trim())

    /**
     * The parts, with no display name: the provider then builds the shown name from them in the
     * order the phone's language expects.
     */
    private fun nameColumns(draft: ContactDraft): Array<Pair<String, Any?>> {
        val (prefix, given, middle, family, suffix) = draft.nameParts
        return arrayOf(
            StructuredName.DISPLAY_NAME to null,
            StructuredName.PREFIX to prefix.ifEmpty { null },
            StructuredName.GIVEN_NAME to given.ifEmpty { null },
            StructuredName.MIDDLE_NAME to middle.ifEmpty { null },
            StructuredName.FAMILY_NAME to family.ifEmpty { null },
            StructuredName.SUFFIX to suffix.ifEmpty { null },
            StructuredName.PHONETIC_GIVEN_NAME to draft.phoneticGiven.trim().ifEmpty { null },
            StructuredName.PHONETIC_MIDDLE_NAME to draft.phoneticMiddle.trim().ifEmpty { null },
            StructuredName.PHONETIC_FAMILY_NAME to draft.phoneticFamily.trim().ifEmpty { null },
        )
    }

    private class RawContact(val id: Long, val account: ContactAccount)

    private fun writableRawContacts(contactId: Long, all: Boolean = false): List<RawContact> {
        val projection = arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE)
        val raws = if (ContactsContract.isProfileId(contactId)) Profile.CONTENT_RAW_CONTACTS_URI else RawContacts.CONTENT_URI
        val cursor = resolver.query(
            raws, projection,
            "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0", arrayOf(contactId.toString()), null,
        ) ?: return emptyList()
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val account = ContactAccount(it.getStringOrNull(1), it.getStringOrNull(2))
                    if (all || isWritableAccount(account.type)) add(RawContact(it.getLong(0), account))
                }
            }
        }
    }

    /** A data row; [data] holds DATA1 to DATA9, trimmed, empty when missing. */
    private class Row(val id: Long, val rawContactId: Long, val mimeType: String, private val columns: List<String>) {
        fun data(index: Int): String = columns[index - 1]
    }

    private fun rows(rawContactIds: List<Long>): List<Row> {
        val dataColumns = listOf(Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5, Data.DATA6, Data.DATA7, Data.DATA8, Data.DATA9)
        val projection = arrayOf(Data._ID, Data.RAW_CONTACT_ID, Data.MIMETYPE) + dataColumns
        val profile = rawContactIds.any(ContactsContract::isProfileId)
        val cursor = resolver.query(
            if (profile) Uri.withAppendedPath(Profile.CONTENT_URI, ContactsContract.Contacts.Data.CONTENT_DIRECTORY) else Data.CONTENT_URI,
            projection, "${Data.RAW_CONTACT_ID} IN (${rawContactIds.joinToString()})", null, null,
        ) ?: return emptyList()
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val mimeType = it.getString(2)
                    // Photos keep a blob in other columns, and DATA1 of a photo is not text.
                    val columns = if (mimeType == Photo.CONTENT_ITEM_TYPE) {
                        List(dataColumns.size) { "" }
                    } else {
                        dataColumns.indices.map { i -> it.getStringOrNull(3 + i)?.trim().orEmpty() }
                    }
                    add(Row(it.getLong(0), it.getLong(1), mimeType, columns))
                }
            }
        }
    }

    private fun photoUri(contactId: Long): String? = resolver.query(
        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
        arrayOf(ContactsContract.Contacts.PHOTO_URI), null, null, null,
    )?.use { if (it.moveToFirst()) it.getStringOrNull(0) else null }

    /** Without this group a new contact lands in Google's "Other contacts" instead of the address book. */
    private fun defaultGroup(account: ContactAccount): Long? {
        if (!account.isGoogle) return null
        return resolver.query(
            Groups.CONTENT_URI, arrayOf(Groups._ID),
            "${Groups.ACCOUNT_NAME} = ? AND ${Groups.ACCOUNT_TYPE} = ? AND ${Groups.AUTO_ADD} = 1 " +
                "AND ${Groups.FAVORITES} = 0 AND ${Groups.DELETED} = 0",
            arrayOf(account.name, account.type), null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    /** ImageDecoder also applies the rotation stored in the file. */
    private fun encodePhoto(source: Uri): ByteArray {
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, source)) { decoder, info, _ ->
            val scale = PHOTO_SIZE.toFloat() / minOf(info.size.width, info.size.height)
            if (scale < 1f) {
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val side = minOf(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        return ByteArrayOutputStream().also { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    /** Operations for one raw contact. A null id points back at the raw contact inserted first. */
    private class Batch(private val rawContactId: Long?) {
        val operations = arrayListOf<ContentProviderOperation>()

        fun insert(mimeType: String, vararg values: Pair<String, Any?>) {
            val builder = ContentProviderOperation.newInsert(Data.CONTENT_URI).withValue(Data.MIMETYPE, mimeType)
            if (rawContactId == null) {
                builder.withValueBackReference(Data.RAW_CONTACT_ID, 0)
            } else {
                builder.withValue(Data.RAW_CONTACT_ID, rawContactId)
            }
            values.forEach { (key, value) -> builder.withValue(key, value) }
            operations += builder.build()
        }

        /**
         * The photo as an ordinary row in the same write: the contact is done when the batch is, and
         * like any other change it marks the contact for Google's sync. Marked primary, so a photo
         * from WhatsApp's copy of the contact doesn't win over it.
         */
        fun insertPhoto(jpeg: ByteArray) {
            insert(Photo.CONTENT_ITEM_TYPE, Photo.PHOTO to jpeg, Data.IS_PRIMARY to 1, Data.IS_SUPER_PRIMARY to 1)
        }

        fun update(dataId: Long, vararg values: Pair<String, Any?>) {
            val builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Data.CONTENT_URI, dataId))
            values.forEach { (key, value) -> builder.withValue(key, value) }
            operations += builder.build()
        }

        fun delete(dataId: Long) {
            operations += ContentProviderOperation.newDelete(ContentUris.withAppendedId(Data.CONTENT_URI, dataId)).build()
        }

        fun syncValues(field: ContactField, before: List<EditableValue>, after: List<EditableValue>) {
            val kept = after.filter { it.value.isNotBlank() }.map { it.copy(value = it.value.trim()) }
            val keptIds = kept.mapNotNull { it.dataId }.toSet()
            before.mapNotNull { it.dataId }.filterNot(keptIds::contains).forEach(::delete)
            kept.forEach { value ->
                val columns = arrayOf(Data.DATA1 to value.value, Data.DATA2 to value.type, Data.DATA3 to value.label)
                when {
                    value.dataId == null -> insert(field.mimeType, *columns)
                    value !in before -> update(value.dataId, *columns)
                }
            }
        }

        fun syncCustom(before: List<CustomField>, after: List<CustomField>) {
            val kept = after.filter { it.label.isNotBlank() || it.value.isNotBlank() }
                .map { it.copy(label = it.label.trim(), value = it.value.trim()) }
            val keptIds = kept.mapNotNull { it.dataId }.toSet()
            before.mapNotNull { it.dataId }.filterNot(keptIds::contains).forEach(::delete)
            kept.forEach { field ->
                val columns = arrayOf(Data.DATA1 to field.label, Data.DATA2 to field.value)
                when {
                    field.dataId == null -> insert(CUSTOM_FIELD, *columns)
                    field !in before -> update(field.dataId, *columns)
                }
            }
        }

        fun syncWork(row: Long?, draft: ContactDraft) {
            val columns = arrayOf(
                Organization.COMPANY to draft.company.trim(),
                Organization.TITLE to draft.jobTitle.trim(),
                Organization.DEPARTMENT to draft.department.trim(),
            )
            val empty = columns.all { (_, value) -> value.isEmpty() }
            when {
                row == null -> if (!empty) insert(Organization.CONTENT_ITEM_TYPE, Organization.TYPE to Organization.TYPE_WORK, *columns)
                empty -> delete(row)
                else -> update(row, *columns)
            }
        }

        /** A kind with one text value per contact, such as the note or the nickname. */
        fun syncSingle(mimeType: String, column: String, row: Long?, value: String) {
            val text = value.trim()
            when {
                row == null -> if (text.isNotEmpty()) insert(mimeType, column to text)
                text.isEmpty() -> delete(row)
                else -> update(row, column to text)
            }
        }
    }
}
