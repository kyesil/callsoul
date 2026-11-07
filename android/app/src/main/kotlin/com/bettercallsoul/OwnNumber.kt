package com.bettercallsoul

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.os.Build
import android.telephony.SubscriptionManager
import com.bettercallsoul.calllog.ContactAccount
import com.bettercallsoul.calllog.ContactDraft
import com.bettercallsoul.calllog.ContactField
import com.bettercallsoul.calllog.EditableValue

/**
 * Saves the phone's own number as a "Ben" contact the first time the SIM reveals it. Many SIMs
 * don't store their number; the job then stays pending in case a later SIM does. The contact is
 * kept in phone storage so that it doesn't sync anywhere.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun numberOf(subscriptions: SubscriptionManager, info: SubscriptionInfo): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) subscriptions.getPhoneNumber(info.subscriptionId) else info.number.orEmpty()

suspend fun seedOwnNumber(context: Context) {
    val app = context.container
    if (app.ownNumberSeeded) return
    if (context.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) return
    if (context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) return

    val subscriptions = context.getSystemService(SubscriptionManager::class.java)
    val numbers = subscriptions.activeSubscriptionInfoList.orEmpty()
        .map { numberOf(subscriptions, it) }
        .filter(String::isNotBlank)
        .distinct()
    if (numbers.isEmpty()) return

    numbers.forEachIndexed { index, number ->
        if (app.contacts.match(number) == null) {
            val base = context.getString(R.string.me)
            app.editor.create(
                ContactDraft(
                    given = if (index == 0) base else "$base ${index + 1}",
                    phones = listOf(EditableValue(number, ContactField.Phone.defaultType)),
                ),
                ContactAccount(null, null),
            )
        }
    }
    app.ownNumberSeeded = true
}
