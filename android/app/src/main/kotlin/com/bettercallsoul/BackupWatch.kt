package com.bettercallsoul

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.content.Context
import android.os.ParcelFileDescriptor

/**
 * Android's own Auto Backup, unchanged, with one thing added: it notes when the phone last backed
 * the app up, so Settings can show it. Apps can't ask whether the phone's backup is on (only system
 * apps may), so the time of the last backup is the honest sign of it.
 *
 * Backups run with a bare Application, not [BcsApp], so this keeps to plain shared preferences. The
 * note's own file stays out of the backup, or a new phone would show the old one's date.
 */
class BackupWatch : BackupAgent() {
    override fun onFullBackup(data: FullBackupDataOutput) {
        super.onFullBackup(data)
        lastBackupStore(this).edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
    }

    // Key/value backup isn't used: fullBackupOnly sends everything through onFullBackup.
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) = Unit

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) = Unit

    companion object {
        const val FILE = "backup_watch"
        private const val KEY_LAST = "last_backup"

        private fun lastBackupStore(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        /** When the phone last backed the app up, in epoch millis, or null if it hasn't since install. */
        fun lastBackup(context: Context): Long? =
            lastBackupStore(context).getLong(KEY_LAST, 0L).takeIf { it > 0L }
    }
}
