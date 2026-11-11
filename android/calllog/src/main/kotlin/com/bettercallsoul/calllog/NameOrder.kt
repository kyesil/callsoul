package com.bettercallsoul.calllog

import android.provider.ContactsContract.Contacts

/**
 * How names read everywhere in the app: first name first ("Deniz Arslan"), or family name first
 * ("Arslan, Deniz"), as Android itself writes the second order. Lists sort the way names read.
 */
object NameOrder {
    @Volatile
    var familyFirst: Boolean = false

    /** The name column in that order; Contacts, Data, Phone, RawContacts and PhoneLookup all share it. */
    val nameColumn: String get() = if (familyFirst) Contacts.DISPLAY_NAME_ALTERNATIVE else Contacts.DISPLAY_NAME_PRIMARY

    /** The column to sort names by, in that order. */
    val sortColumn: String get() = if (familyFirst) Contacts.SORT_KEY_ALTERNATIVE else Contacts.SORT_KEY_PRIMARY
}
