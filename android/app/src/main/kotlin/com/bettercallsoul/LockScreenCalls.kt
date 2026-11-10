package com.bettercallsoul

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Whether a ringing call may wake the screen and open over the lock screen. Android lets a calling
 * app do it through a full-screen notification, but on Play installs the permission can arrive
 * turned off, and then a call on a sleeping phone is only a notification. Some phones (Honor) also
 * ignore the call screen's own request to turn the screen on, so this is the one way that works.
 */
object LockScreenCalls {
    /** Before Android 14 the permission comes with the install and can't be taken away. */
    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    /** Only the system page can turn it on. */
    fun open(context: Context) {
        val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                context.startActivity(fallback)
                return
            }
            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri()))
        } catch (_: ActivityNotFoundException) {
            runCatching { context.startActivity(fallback) }
        }
    }
}
