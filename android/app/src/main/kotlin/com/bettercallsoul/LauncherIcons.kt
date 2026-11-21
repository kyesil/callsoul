package com.bettercallsoul

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * The home screen icons to choose from. Each is an activity-alias of MainActivity with its own icon,
 * and exactly one of them is enabled; [Glass] is the one the manifest enables.
 */
enum class LauncherIcon(val alias: String, @DrawableRes val preview: Int, @StringRes val label: Int) {
    Glass(".LauncherGlass", R.drawable.launcher_glass, R.string.icon_glass),
    Clear(".LauncherClear", R.drawable.launcher_clear, R.string.icon_clear),
    Green(".LauncherGreen", R.drawable.launcher_green, R.string.icon_green),
    Blue(".LauncherBlue", R.drawable.launcher_blue, R.string.icon_blue),
}

object LauncherIcons {
    /** Enables [icon]'s alias and disables the others, without closing the app. */
    fun apply(context: Context, icon: LauncherIcon) {
        val packages = context.packageManager
        // The new one first, so there is never a moment without a launcher entry.
        val ordered = listOf(icon) + LauncherIcon.entries.filter { it != icon }
        for (entry in ordered) {
            val component = ComponentName(context, "com.bettercallsoul" + entry.alias)
            val state = if (entry == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (packages.getComponentEnabledSetting(component) != state) {
                runCatching { packages.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP) }
            }
        }
    }
}
