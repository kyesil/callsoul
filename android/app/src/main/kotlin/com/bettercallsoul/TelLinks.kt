package com.bettercallsoul

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import com.bettercallsoul.diag.DebugLog

/** Who opens a kind of link tapped in another app. */
sealed interface LinkHolder {
    data object Us : LinkHolder
    data object Nobody : LinkHolder
    data class Other(val packageName: String) : LinkHolder
}

/** Marks the link sent only to bring the system's question up, so it opens nothing when it arrives. */
const val EXTRA_LINK_SETUP = "tel_links_setup"

/**
 * A kind of link from other apps that the system asks about until someone picks an app for
 * always. No app can make that choice for itself, so the best it can do is bring the question up,
 * at a moment of its own, with itself in the list.
 */
abstract class DefaultLinks(private val tag: String) {
    /** The link as other apps send it; [real] when it may actually open somewhere if picked "just once". */
    protected abstract fun link(context: Context, real: Boolean): Intent?

    fun holder(context: Context): LinkHolder {
        val link = link(context, real = false) ?: return LinkHolder.Nobody
        val pm = context.packageManager
        val found = pm.resolveActivity(link, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            ?: return LinkHolder.Nobody
        // Anything that isn't itself one of the link's handlers is the system's chooser, whatever a
        // phone maker calls it: nobody has been picked yet.
        val handlers = pm.queryIntentActivities(link, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.packageName }
        return when (found.packageName) {
            context.packageName -> LinkHolder.Us
            in handlers -> LinkHolder.Other(found.packageName)
            else -> LinkHolder.Nobody
        }
    }

    /**
     * Asks the system's question now. With another app picked for always, only that app's page can
     * undo it ("Open by default"), so that is where it goes instead.
     */
    fun choose(context: Context) {
        val holder = holder(context)
        DebugLog.i(tag, "choose: $holder")
        val intent = when (holder) {
            is LinkHolder.Other -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${holder.packageName}"))
            else -> link(context, real = true)?.putExtra(EXTRA_LINK_SETUP, true) ?: return
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK.takeIf { context !is android.app.Activity } ?: 0))
        } catch (_: ActivityNotFoundException) {
        }
    }

    /** The other app's name, for telling the user whose defaults to clear. */
    fun label(context: Context, packageName: String): String =
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
}

/** tel: links. The phone role only makes this app the one for DIAL; a link comes as VIEW. */
object TelLinks : DefaultLinks("TelLinks") {
    override fun link(context: Context, real: Boolean): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("tel:")).addCategory(Intent.CATEGORY_BROWSABLE)
}

/**
 * A contact opened from another app, such as "Contact info" in a chat app. No role covers these;
 * the system asks between the phone's contacts app and this one.
 */
object ContactLinks : DefaultLinks("ContactLinks") {
    override fun link(context: Context, real: Boolean): Intent? {
        // Picked "just once", the other app opens what it was sent, so the question uses a real
        // contact; only which apps match the type matters for reading the choice back.
        val id = if (real) firstContact(context) ?: return null else 1L
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id), ContactsContract.Contacts.CONTENT_ITEM_TYPE)
    }

    private fun firstContact(context: Context): Long? = runCatching {
        context.contentResolver.query(ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts._ID), null, null, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }.getOrNull()
}
