package com.bettercallsoul

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import com.bettercallsoul.diag.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Calls to make later, set on a person's page or on a missed call. Each one becomes a notification
 * with a call button at its time (see [ReminderReceiver]); they are kept through a restart.
 */
class Reminders(context: Context) {
    /** The call button rings the first of [numbers]; the rest are there to find it on the person's page. */
    data class Reminder(val id: Int, val numbers: List<String>, val note: String?, val at: Long) {
        val number: String get() = numbers.first()
    }

    private val app = context.applicationContext ?: context
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val listeners = HashMap<Int, AlarmManager.OnAlarmListener>()
    private val _all = MutableStateFlow(read())

    val all: StateFlow<List<Reminder>> = _all.asStateFlow()

    /** Whether Android lets the reminders come on the minute; without it they may be some minutes late. */
    val exact: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    @Synchronized
    fun add(numbers: List<String>, note: String?, minutes: Int): Reminder? {
        val usable = numbers.filter(String::isNotBlank)
        if (usable.isEmpty() || minutes <= 0) return null
        val id = prefs.getInt(KEY_NEXT, 1)
        prefs.edit { putInt(KEY_NEXT, if (id >= MAX_ID) 1 else id + 1) }
        val reminder = Reminder(id, usable, note?.trim()?.takeIf(String::isNotEmpty), System.currentTimeMillis() + minutes * 60_000L)
        save(_all.value + reminder)
        schedule(reminder)
        return reminder
    }

    @Synchronized
    fun remove(id: Int) {
        cancel(id)
        save(_all.value.filter { it.id != id })
    }

    /** Takes out the reminder whose time came; null when it was removed or already shown. */
    @Synchronized
    fun take(id: Int): Reminder? {
        val found = _all.value.firstOrNull { it.id == id } ?: return null
        remove(id)
        return found
    }

    /** Android forgets alarms on a restart or an update; one whose time passed meanwhile comes at once. */
    @Synchronized
    fun scheduleAll() = _all.value.forEach(::schedule)

    @SuppressLint("MissingPermission") // Only exact when allowed; a right taken away in between is caught.
    private fun schedule(reminder: Reminder) {
        val due = alarm(reminder.id)
        listeners.remove(reminder.id)?.let(alarms::cancel)
        runCatching {
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, due)
                return
            }
        }.onFailure { DebugLog.e(TAG, "no exact alarm", it) }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, due)
        // Needs no permission and is on time, but only while the app keeps running.
        val listener = AlarmManager.OnAlarmListener { app.sendBroadcast(ReminderReceiver.due(app, reminder.id)) }
        listeners[reminder.id] = listener
        runCatching { alarms.setExact(AlarmManager.RTC_WAKEUP, reminder.at, TAG, listener, main) }
    }

    private fun cancel(id: Int) {
        alarms.cancel(alarm(id))
        listeners.remove(id)?.let(alarms::cancel)
    }

    private fun alarm(id: Int): PendingIntent = PendingIntent.getBroadcast(
        app, id, ReminderReceiver.due(app, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun save(reminders: List<Reminder>) {
        _all.value = reminders
        val json = JSONArray()
        reminders.forEach {
            json.put(JSONObject().put("id", it.id).put("numbers", JSONArray(it.numbers)).putOpt("note", it.note).put("at", it.at))
        }
        prefs.edit { putString(KEY, json.toString()) }
    }

    private fun read(): List<Reminder> = runCatching {
        val json = JSONArray(prefs.getString(KEY, null) ?: "[]")
        (0 until json.length()).map { json.getJSONObject(it) }.mapNotNull { one ->
            val numbers = one.getJSONArray("numbers").let { list -> (0 until list.length()).map(list::getString) }
            if (numbers.isEmpty()) null else Reminder(one.getInt("id"), numbers, if (one.has("note")) one.getString("note") else null, one.getLong("at"))
        }
    }.onFailure { DebugLog.e(TAG, "couldn't read the reminders", it) }.getOrDefault(emptyList())

    companion object {
        private const val TAG = "Reminders"
        private const val KEY = "reminders"
        private const val KEY_NEXT = "next_id"
        private const val MAX_ID = 1_000_000

        /** The reminders about any of these numbers, soonest first. */
        fun about(all: List<Reminder>, numbers: Collection<String>): List<Reminder> {
            val keys = numbers.mapNotNullTo(HashSet(), MutedCallers::keyOf)
            return all.filter { one -> one.numbers.any { it in numbers || MutedCallers.keyOf(it) in keys } }.sortedBy { it.at }
        }
    }
}
