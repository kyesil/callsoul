package com.bettercallsoul

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.bettercallsoul.diag.DebugLog

/**
 * Who opens a tel: link tapped in another app. The phone role only makes this app the one for
 * DIAL; a link comes as VIEW, which the system asks about until someone picks an app for always.
 * No app can make that choice for itself, so the best it can do is bring the question up once,
 * at a moment of its own, with itself in the list.
 */
object TelLinks {
    sealed interface Holder {
        data object Us : Holder
        data object Nobody : Holder
        data class Other(val packageName: String) : Holder
    }

    /** Marks the link sent only to bring the question up, so it opens nothing when it arrives. */
    const val EXTRA_SETUP = "tel_links_setup"

    private fun link() = Intent(Intent.ACTION_VIEW, Uri.parse("tel:")).addCategory(Intent.CATEGORY_BROWSABLE)

    fun holder(context: Context): Holder {
        val pm = context.packageManager
        val found = pm.resolveActivity(link(), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            ?: return Holder.Nobody
        // Anything that isn't itself one of the link's handlers is the system's chooser, whatever a
        // phone maker calls it: nobody has been picked yet.
        val handlers = pm.queryIntentActivities(link(), PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.packageName }
        return when (found.packageName) {
            context.packageName -> Holder.Us
            in handlers -> Holder.Other(found.packageName)
            else -> Holder.Nobody
        }
    }

    /**
     * Asks the system's question now. With another app picked for always, only that app's page can
     * undo it ("Open by default"), so that is where it goes instead.
     */
    fun choose(context: Context) {
        val holder = holder(context)
        DebugLog.i("TelLinks", "choose: $holder")
        val intent = when (holder) {
            is Holder.Other -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${holder.packageName}"))
            else -> link().putExtra(EXTRA_SETUP, true)
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
