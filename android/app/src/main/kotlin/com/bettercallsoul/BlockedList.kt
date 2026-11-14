package com.bettercallsoul

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The phone's own blocked list. Only the default phone app may read or write it, so before that
 * every call quietly answers "not blocked" and changes nothing. The system matches a number in any
 * format, so +90 532... finds 0532... blocked from the list.
 */
object BlockedList {
    suspend fun isBlocked(context: Context, number: String): Boolean = withContext(Dispatchers.IO) {
        number.isNotBlank() && runCatching { BlockedNumberContract.isBlocked(context, number) }.getOrDefault(false)
    }

    /** True once the number is on the list, whether it was added now or already there. */
    suspend fun block(context: Context, number: String): Boolean = withContext(Dispatchers.IO) {
        if (number.isBlank()) return@withContext false
        if (isBlocked(context, number)) return@withContext true
        runCatching {
            val values = ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }
            context.contentResolver.insert(BlockedNumbers.CONTENT_URI, values) != null
        }.getOrDefault(false)
    }

    /** Removes every entry that matches the number, however it was written when it was blocked. */
    suspend fun unblock(context: Context, number: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { BlockedNumberContract.unblock(context, number) > 0 }.getOrDefault(false)
    }
}
