package com.bettercallsoul

import android.content.Context
import android.text.format.DateUtils
import android.widget.Toast

/** How long the buttons on the notifications mute or wait. */
const val QUICK_MINUTES = 15

/** All of a saved caller's numbers and their name, so quieting them covers every line; else just the number. */
suspend fun AppContainer.personOf(number: String): Pair<List<String>, String?> {
    val contact = runCatching { contacts.match(number)?.let { contacts.detail(it.id) } }.getOrNull()
    return (listOf(number) + contact?.phones.orEmpty().map { it.number }) to contact?.name
}

/** A reminder in a quarter of an hour, from a notification, with a word on when it comes. */
fun quickReminder(context: Context, numbers: List<String>, note: String?) {
    val set = context.container.reminders.add(numbers, note, QUICK_MINUTES) ?: return
    Toast.makeText(context, context.getString(R.string.remind_at, clockTime(context, set.at)), Toast.LENGTH_SHORT).show()
}

/** A time of day, with the date when that is not today. */
fun clockTime(context: Context, at: Long): String {
    val flags = DateUtils.FORMAT_SHOW_TIME or if (DateUtils.isToday(at)) 0 else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
    return DateUtils.formatDateTime(context, at, flags)
}
