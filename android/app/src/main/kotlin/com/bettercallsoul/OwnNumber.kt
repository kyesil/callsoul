package com.bettercallsoul

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import java.util.Locale
import android.telephony.SubscriptionInfo
import android.os.Build
import android.telephony.SubscriptionManager
import com.bettercallsoul.calllog.ContactAccount
import com.bettercallsoul.calllog.ContactDraft
import com.bettercallsoul.calllog.ContactField
import com.bettercallsoul.calllog.EditableValue
import com.bettercallsoul.calllog.PhotoEdit
import com.bettercallsoul.diag.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "OwnNumber"

/**
 * Puts the phone's own number on My card the first time the SIM reveals it. Many SIMs don't store
 * their number; the job then stays pending in case a later SIM does.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun numberOf(subscriptions: SubscriptionManager, info: SubscriptionInfo): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return info.number.orEmpty()
    // The plain call answers from one source only; Samsung often leaves that one empty while the SIM,
    // the carrier or IMS knows the number, so each is asked in turn.
    val sources = listOf(
        SubscriptionManager.PHONE_NUMBER_SOURCE_UICC,
        SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER,
        SubscriptionManager.PHONE_NUMBER_SOURCE_IMS,
    )
    val found = sequenceOf(runCatching { subscriptions.getPhoneNumber(info.subscriptionId) }.getOrNull()) +
        sources.asSequence().map { runCatching { subscriptions.getPhoneNumber(info.subscriptionId, it) }.getOrNull() } +
        sequenceOf(info.number)
    return found.firstOrNull { !it.isNullOrBlank() }.orEmpty()
}

suspend fun seedOwnNumber(context: Context) {
    val app = context.container
    relinkMyCard(context)
    if (app.ownNumberSeeded) return
    if (context.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) return
    if (context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) return

    val subscriptions = context.getSystemService(SubscriptionManager::class.java)
    val numbers = subscriptions.activeSubscriptionInfoList.orEmpty()
        .map { numberOf(subscriptions, it) }
        .filter(String::isNotBlank)
        .distinct()
    DebugLog.i(TAG, "SIM numbers: ${numbers.size}")
    if (numbers.isEmpty()) return
    val phones = numbers.map { EditableValue(it, ContactField.Phone.defaultType) }

    // My card: Android's own profile, which stays on the phone, or where the phone hides that (Samsung)
    // a "Me" contact in phone storage. One that has a number already is the owner's to keep as it is;
    // an empty one (Samsung makes one) gets the SIM's numbers.
    val card = withContext(Dispatchers.IO) { app.contacts.myCard() }
    val stored = card?.let { app.editor.load(it.id) }
    when {
        stored == null && app.contacts.profileHidden ->
            app.editor.create(ContactDraft(given = context.getString(R.string.me), phones = phones), myCardAccount(app))
                ?.let { app.myCardKey = app.contacts.lookupKeyOf(it) }
        stored == null -> app.editor.create(ContactDraft(phones = phones), ContactAccount(null, null), profile = true)
        stored.draft.phones.none { it.value.isNotBlank() } -> app.editor.update(stored, stored.draft.copy(phones = phones), PhotoEdit.Keep)
    }
    app.ownNumberSeeded = true
}

/**
 * Where the phone hides its profile, the app keeps which contact is My card in its settings, and a
 * reinstall forgets that: the contact is found again in phone storage by the name the app gave it.
 */
private suspend fun relinkMyCard(context: Context) = withContext(Dispatchers.IO) {
    val app = context.container
    if (app.myCardKey != null || !app.contacts.profileHidden) return@withContext
    val id = app.contacts.localContactNamed(meNames(context)) ?: return@withContext
    DebugLog.i(TAG, "my card found again")
    app.myCardKey = app.contacts.lookupKeyOf(id)
}

/** "Me" in each language the app speaks: the names it may have given the contact that is My card. */
private fun meNames(context: Context): Set<String> = AppLook.LANGUAGES.filter(String::isNotEmpty).mapTo(mutableSetOf()) { tag ->
    val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
    context.createConfigurationContext(config).getString(R.string.me)
}

/** Where the "Me" contact goes: phone storage, or Android's place for new contacts when it refuses that. */
internal fun myCardAccount(app: AppContainer): ContactAccount =
    if (app.editor.localAllowed()) ContactAccount(null, null) else app.editor.systemDefault() ?: ContactAccount(null, null)

/** The first number a SIM in the phone reveals, if any; many SIMs don't store theirs. */
@SuppressLint("MissingPermission")
fun ownNumber(context: Context): String? {
    if (context.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) return null
    val subscriptions = context.getSystemService(SubscriptionManager::class.java)
    return runCatching { subscriptions.activeSubscriptionInfoList.orEmpty().map { numberOf(subscriptions, it) }.firstOrNull(String::isNotBlank) }.getOrNull()
}
