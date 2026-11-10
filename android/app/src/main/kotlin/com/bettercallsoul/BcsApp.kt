package com.bettercallsoul

import com.bettercallsoul.calllog.Birthday
import com.bettercallsoul.calllog.NumberTag
import com.bettercallsoul.calllog.ContactRow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import com.bettercallsoul.diag.DebugLog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import androidx.compose.runtime.snapshotFlow
import coil3.SingletonImageLoader
import com.bettercallsoul.calllog.CallEntry
import com.bettercallsoul.calllog.CallHistory
import com.bettercallsoul.calllog.WhatsAppCallLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.bettercallsoul.calllog.CallLogRepository
import com.bettercallsoul.calllog.ContactEditor
import com.bettercallsoul.calllog.ContactTrash
import com.bettercallsoul.calllog.ContactsRepository
import com.bettercallsoul.calllog.LocalDatabase
import com.bettercallsoul.calllog.withRingTimes
import com.bettercallsoul.telecom.CallChannels
import com.bettercallsoul.spam.SpamClient
import com.bettercallsoul.telecom.CallManager
import com.bettercallsoul.telecom.WhatsAppTelecomCalls
import com.bettercallsoul.telecom.CallerDirectory
import java.util.UUID

/** A little wider than Android's back gesture zone at its default sensitivity. */
const val DEFAULT_SWIPE_EDGE = 32
const val MAX_SWIPE_EDGE = 80

/** What a left swipe on a contact or call row opens. [key] is what Settings stores. */
enum class SwipeMessage(val key: String) {
    WhatsApp("whatsapp"),
    Sms("sms"),
}

class AppContainer(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val contacts = ContactsRepository(context)
    val callLog = CallLogRepository(context)
    val editor = ContactEditor(context) { deleted ->
        // A contact's small photo keeps its address when the picture changes, so the old one has to go.
        SingletonImageLoader.get(context).memoryCache?.clear()
        contacts.refresh(deleted)
        callLog.refresh()
    }
    val store = LocalDatabase.create(context).dao()
    val trash = ContactTrash(context, store, editor)
    val history = CallHistory(context, store) { historyDays }
    val whatsApp = WhatsAppCallLog(store, callLog, contacts) { historyDays }

    /** How many days of calls the app keeps on the phone; a year unless the user picks otherwise. */
    var historyDays by mutableStateOf(prefs.getInt(KEY_HISTORY_DAYS, CallHistory.KEEP_DAYS))
        private set

    /** A shorter time deletes what falls outside it right away. */
    suspend fun saveHistoryDays(days: Int) {
        historyDays = days
        prefs.edit { putInt(KEY_HISTORY_DAYS, days) }
        history.sync()
        whatsApp.prune()
    }
    val spam = SpamClient(listOf(BuildConfig.API_URL) + BuildConfig.API_BACKUP_URLS.split(",").map { it.trim() })
    val directory = CallerDirectory(contacts, callLog, store, spam) { spamLookupEnabled }

    /**
     * The lists the tabs show, kept for a while after the last screen stops watching them. A tab
     * opened again then has its rows at once, so it comes back scrolled where it was left.
     */
    private val lists = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error -> DebugLog.w("App", "list failed", error) })
    val contactRows: StateFlow<List<ContactRow>> by lazy { contacts.observe().stateIn(lists, SharingStarted.WhileSubscribed(KEEP_LISTS_MILLIS), emptyList()) }
    val callRows: StateFlow<List<CallEntry>> by lazy { observeCalls(limit = 1000).stateIn(lists, SharingStarted.WhileSubscribed(KEEP_LISTS_MILLIS), emptyList()) }
    val tagRows: StateFlow<List<NumberTag>> by lazy { store.observeTags().stateIn(lists, SharingStarted.WhileSubscribed(KEEP_LISTS_MILLIS), emptyList()) }
    val profileRow: StateFlow<ContactRow?> by lazy { contacts.observeProfile().stateIn(lists, SharingStarted.WhileSubscribed(KEEP_LISTS_MILLIS), null) }
    val birthdayRows: StateFlow<List<Birthday>> by lazy { contacts.observeBirthdays().stateIn(lists, SharingStarted.WhileSubscribed(KEEP_LISTS_MILLIS), emptyList()) }

    /** Everything the recents show: the phone's calls and, when switched on, the WhatsApp ones. */
    fun observeCalls(limit: Int = 300): Flow<List<CallEntry>> = combine(
        callLog.observe(limit),
        whatsApp.observe(),
        snapshotFlow { whatsAppCallsEnabled },
        store.observeRingTimes(),
    ) { phone, whatsApp, enabled, rings ->
        val calls = phone.withRingTimes(rings)
        if (enabled && whatsApp.isNotEmpty()) (calls + whatsApp).sortedByDescending { it.date } else calls
    }

    /** A number's calls for its own page: the phone's and, when switched on, the WhatsApp ones. */
    suspend fun callHistory(numbers: Collection<String>, name: String? = null, limit: Int = 50): List<CallEntry> {
        val rings = store.ringTimes()
        val phone = numbers.flatMap { callLog.historyFor(it, limit) }.distinctBy { it.id }.withRingTimes(rings)
        val calls = if (whatsAppCallsEnabled) phone + whatsApp.historyFor(numbers, name) else phone
        return calls.sortedByDescending { it.date }.take(limit)
    }

    /** Removes calls from the phone's log and from the statistics; WhatsApp ones have negative ids. */
    suspend fun deleteCalls(ids: Collection<Long>) {
        val (fromWhatsApp, fromPhone) = ids.partition { it < 0 }
        callLog.delete(fromPhone)
        history.forget(fromPhone)
        whatsApp.forget(fromWhatsApp)
    }

    /** What the user searched for and then picked a result of, newest first. It stays on the phone. */
    var recentSearches by mutableStateOf(
        prefs.getString(KEY_RECENT_SEARCHES, null)?.split('\n')?.filter(String::isNotBlank).orEmpty(),
    )
        private set

    /** The recent searches that were a contact label, shown with a label's icon. */
    var labelSearches by mutableStateOf(prefs.getStringSet(KEY_LABEL_SEARCHES, null).orEmpty().toSet())
        private set

    fun rememberSearch(text: String, label: Boolean = false) {
        saveRecentSearches((listOf(text) + recentSearches.filterNot { it.equals(text, ignoreCase = true) }).take(MAX_RECENT_SEARCHES))
        if (label && text !in labelSearches) saveLabelSearches(labelSearches + text)
    }

    fun forgetSearch(text: String) {
        saveRecentSearches(recentSearches - text)
        if (text in labelSearches) saveLabelSearches(labelSearches - text)
    }

    private fun saveLabelSearches(labels: Set<String>) {
        // Only those still among the recent searches are worth keeping.
        labelSearches = labels.filterTo(HashSet()) { it in recentSearches }
        prefs.edit { putStringSet(KEY_LABEL_SEARCHES, labelSearches) }
    }

    private fun saveRecentSearches(searches: List<String>) {
        recentSearches = searches
        prefs.edit { putString(KEY_RECENT_SEARCHES, searches.joinToString("\n")) }
    }

    /**
     * The contacts and numbers the user opened or called from a search, newest first, as
     * [OPENED_CONTACT] or [OPENED_NUMBER] keys. It stays on the phone.
     */
    var openedFromSearch by mutableStateOf(
        prefs.getString(KEY_SEARCH_OPENED, null)?.split('\n')?.filter(String::isNotBlank).orEmpty(),
    )
        private set

    fun rememberOpenedContact(contactId: Long) = rememberOpened(OPENED_CONTACT + contactId)

    fun rememberOpenedNumber(number: String) = rememberOpened(OPENED_NUMBER + number)

    private fun rememberOpened(key: String) = saveOpened((listOf(key) + openedFromSearch.filterNot { it == key }).take(MAX_SEARCH_OPENED))

    fun forgetOpened() = saveOpened(emptyList())

    private fun saveOpened(keys: List<String>) {
        openedFromSearch = keys
        prefs.edit { putString(KEY_SEARCH_OPENED, keys.joinToString("\n")) }
    }

    /** On unless turned off; it works once the user gives notification access, which Android asks for. */
    var whatsAppCallsEnabled by mutableStateOf(prefs.getBoolean(KEY_WHATSAPP_CALLS, true))
        private set

    fun saveWhatsAppCalls(enabled: Boolean) {
        whatsAppCallsEnabled = enabled
        prefs.edit { putBoolean(KEY_WHATSAPP_CALLS, enabled) }
    }

    /** A call coming in while the phone is in use rings in the pill instead of a pop-up, unless turned off. */
    var ringInPill by mutableStateOf(prefs.getBoolean(KEY_RING_IN_PILL, true))
        private set

    fun saveRingInPill(enabled: Boolean) {
        ringInPill = enabled
        prefs.edit { putBoolean(KEY_RING_IN_PILL, enabled) }
    }

    /**
     * The phone held a WhatsApp notification back from the app lately: it stops the app when idle.
     * Forgotten after a few days without another one, or once the user goes to fix it.
     */
    var whatsAppLate by mutableStateOf(System.currentTimeMillis() - prefs.getLong(KEY_WHATSAPP_LATE_AT, 0) < LATE_FOR_MILLIS)
        private set

    fun noteWhatsAppLate() {
        val now = System.currentTimeMillis()
        whatsAppLate = true
        if (now - prefs.getLong(KEY_WHATSAPP_LATE_AT, 0) > LATE_WRITE_MILLIS) prefs.edit { putLong(KEY_WHATSAPP_LATE_AT, now) }
    }

    fun forgetWhatsAppLate() {
        whatsAppLate = false
        prefs.edit { remove(KEY_WHATSAPP_LATE_AT) }
    }

    /** The call pill in WhatsApp calls. On with the WhatsApp calls, unless turned off. */
    var whatsAppPillEnabled by mutableStateOf(prefs.getBoolean(KEY_WHATSAPP_PILL, true))
        private set

    fun saveWhatsAppPill(enabled: Boolean) {
        whatsAppPillEnabled = enabled
        prefs.edit { putBoolean(KEY_WHATSAPP_PILL, enabled) }
    }

    /** On by default; the user can turn it off in Settings. Contacts are never sent. */
    var spamLookupEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOOKUP, true)
        set(value) = prefs.edit { putBoolean(KEY_LOOKUP, value) }

    /** The first-run question about being the default phone app has been answered, either way. */
    var defaultPhoneAsked: Boolean
        get() = prefs.getBoolean(KEY_DEFAULT_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_DEFAULT_ASKED, value) }

    /** Whether the question about tel: links was put once, after the phone role came. */
    var telLinksAsked: Boolean
        get() = prefs.getBoolean(KEY_TEL_LINKS_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_TEL_LINKS_ASKED, value) }

    /** A call arriving on a phone lying on its face rings silently, with the screen on. */
    var silentWhenFaceDown: Boolean
        get() = prefs.getBoolean(KEY_FACE_DOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_FACE_DOWN, value) }

    /** A short beep when a call connects. */
    var toneOnConnect: Boolean
        get() = prefs.getBoolean(KEY_TONE_CONNECT, false)
        set(value) = prefs.edit { putBoolean(KEY_TONE_CONNECT, value) }

    /** Two short beeps when a call ends. */
    var toneOnEnd: Boolean
        get() = prefs.getBoolean(KEY_TONE_END, false)
        set(value) = prefs.edit { putBoolean(KEY_TONE_END, value) }

    /** A volume key pressed while a call rings turns it down, instead of only quieting it. */
    var declineOnKey: Boolean
        get() = prefs.getBoolean(KEY_DECLINE_ON_KEY, false)
        set(value) = prefs.edit { putBoolean(KEY_DECLINE_ON_KEY, value) }

    /** Groups of contacts the user said are different people, by their sorted contact ids. */
    var keptApart: Set<String>
        get() = prefs.getStringSet(KEY_KEPT_APART, null).orEmpty().toSet()
        set(value) = prefs.edit { putStringSet(KEY_KEPT_APART, value) }

    /** Light, dark or the phone's; Compose state, so the open screens follow at once. */
    var themeChoice by mutableStateOf(
        runCatching { ThemeChoice.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeChoice.System),
    )
        private set

    fun saveTheme(choice: ThemeChoice) {
        themeChoice = choice
        prefs.edit { putString(KEY_THEME, choice.name) }
    }

    /** Compose state, so flipping the switch in Settings restyles the open screens at once. */
    var animationsEnabled by mutableStateOf(prefs.getBoolean(KEY_ANIMATIONS, true))
        private set

    fun setAnimations(enabled: Boolean) {
        animationsEnabled = enabled
        prefs.edit { putBoolean(KEY_ANIMATIONS, enabled) }
    }

    /** The swipe-up menu on the home screen's people; Compose state, so the switch takes effect at once. */
    var quickMenuEnabled by mutableStateOf(prefs.getBoolean(KEY_QUICK_MENU, true))
        private set

    fun setQuickMenu(enabled: Boolean) {
        quickMenuEnabled = enabled
        prefs.edit { putBoolean(KEY_QUICK_MENU, enabled) }
    }

    /**
     * How far from the screen's side edges a swipe on a list row has to start, in dp; closer than
     * that it is left to Android's back gesture. Compose state, so the lists follow the slider.
     */
    var swipeEdgeDp by mutableIntStateOf(prefs.getInt(KEY_SWIPE_EDGE, DEFAULT_SWIPE_EDGE))
        private set

    fun setSwipeEdge(dp: Int) {
        swipeEdgeDp = dp
        prefs.edit { putInt(KEY_SWIPE_EDGE, dp) }
    }

    /** Compose state, so the rows take the chosen colour as soon as it changes. WhatsApp unless changed. */
    var swipeMessage by mutableStateOf(
        SwipeMessage.entries.firstOrNull { it.key == prefs.getString(KEY_SWIPE_MESSAGE, null) } ?: SwipeMessage.WhatsApp,
    )
        private set

    fun saveSwipeMessage(choice: SwipeMessage) {
        swipeMessage = choice
        prefs.edit { putString(KEY_SWIPE_MESSAGE, choice.key) }
    }

    /** The account the last new contact went to, as "type|name", so the next one goes there too. */
    var newContactAccount: String?
        get() = prefs.getString(KEY_NEW_CONTACT_ACCOUNT, null)
        set(value) = prefs.edit { putString(KEY_NEW_CONTACT_ACCOUNT, value) }

    /** The source the contacts list is narrowed to, as [com.bettercallsoul.calllog.ContactSource.key]; null for all. */
    /** The accounts the contact list is narrowed to; none means everyone. */
    var contactsSources: Set<String>
        get() = prefs.getStringSet(KEY_CONTACTS_SOURCES, null) ?: setOfNotNull(prefs.getString(KEY_CONTACTS_SOURCE, null))
        set(value) = prefs.edit { putStringSet(KEY_CONTACTS_SOURCES, value) }

    /** The ready-made texts for turning a call down with a message, editable in Settings. */
    var quickReplies by mutableStateOf(
        prefs.getString(KEY_QUICK_REPLIES, null)?.split(REPLY_SEPARATOR)?.filter(String::isNotBlank)
            ?: context.resources.getStringArray(R.array.quick_replies).toList(),
    )
        private set

    fun saveQuickReplies(replies: List<String>) {
        quickReplies = replies.map(String::trim).filter(String::isNotEmpty)
        prefs.edit { putString(KEY_QUICK_REPLIES, quickReplies.joinToString(REPLY_SEPARATOR)) }
    }

    /** On by default in debug and preview builds, which are the ones handed out for testing. */
    var debugLogEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_LOG, BuildConfig.LOG_BY_DEFAULT)
        set(value) = prefs.edit { putBoolean(KEY_DEBUG_LOG, value) }

    var ownNumberSeeded: Boolean
        get() = prefs.getBoolean(KEY_OWN_NUMBER, false)
        set(value) = prefs.edit { putBoolean(KEY_OWN_NUMBER, value) }

    /** Random per install, so one phone counts as one vote without identifying its owner. */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString(KEY_DEVICE_ID, it) }
        }

    private companion object {
        const val KEY_LOOKUP = "spam_lookup"
        const val KEY_DEFAULT_ASKED = "default_phone_asked"
        const val KEY_TEL_LINKS_ASKED = "tel_links_asked"
        const val KEY_FACE_DOWN = "silent_face_down"
        const val KEY_TONE_CONNECT = "tone_on_connect"
        const val KEY_THEME = "theme"
        const val KEY_TONE_END = "tone_on_end"
        const val KEY_DECLINE_ON_KEY = "decline_on_key"
        const val KEY_KEPT_APART = "duplicates_kept_apart"
        const val KEY_ANIMATIONS = "animations"
        const val KEY_QUICK_MENU = "quick_menu"
        const val KEY_DEBUG_LOG = "debug_log"
        const val KEY_OWN_NUMBER = "own_number_seeded"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_SWIPE_EDGE = "swipe_edge_dp"
        const val KEY_NEW_CONTACT_ACCOUNT = "new_contact_account"
        const val KEY_CONTACTS_SOURCE = "contacts_source"
        const val KEY_CONTACTS_SOURCES = "contacts_sources"
        const val KEY_QUICK_REPLIES = "quick_replies"
        const val KEY_SWIPE_MESSAGE = "swipe_message"
        const val KEY_RECENT_SEARCHES = "recent_searches"
        const val KEY_HISTORY_DAYS = "history_days"
        const val KEEP_LISTS_MILLIS = 5 * 60 * 1000L
        const val KEY_LABEL_SEARCHES = "label_searches"
        const val KEY_SEARCH_OPENED = "search_opened"
        const val MAX_SEARCH_OPENED = 20
        const val KEY_WHATSAPP_CALLS = "whatsapp_calls"
        const val KEY_WHATSAPP_PILL = "whatsapp_pill"
        const val KEY_RING_IN_PILL = "ring_in_pill"
        const val KEY_WHATSAPP_LATE_AT = "whatsapp_late_at"
        const val LATE_FOR_MILLIS = 3 * 24 * 60 * 60 * 1000L
        const val LATE_WRITE_MILLIS = 60 * 60 * 1000L
        const val REPLY_SEPARATOR = "\u001F"
    }
}

/** How many past searches the search page keeps and shows. */
const val MAX_RECENT_SEARCHES = 4

/** Keys of what was opened from a search: a contact by id, or a number. */
const val OPENED_CONTACT = "contact:"
const val OPENED_NUMBER = "number:"

class BcsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DebugLog.init(this) { container.debugLogEnabled }
        DebugLog.i("App", "start ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})")
        CallManager.init(
            container.directory,
            silentWhenFaceDown = { container.silentWhenFaceDown },
            ringInPill = { container.ringInPill },
            toneOnConnect = { container.toneOnConnect },
            toneOnEnd = { container.toneOnEnd },
            declineOnKey = { container.declineOnKey },
        )
        WhatsAppTelecomCalls.init { call -> if (container.whatsAppCallsEnabled) container.whatsApp.save(call) }
        CallChannels.create(this, getString(R.string.channel_incoming), getString(R.string.channel_ongoing))
        MissedCallReceiver.createChannel(this)
        // Android connects the listener by itself on most phones, which takes a moment.
        Handler(Looper.getMainLooper()).postDelayed({
            if (container.whatsAppCallsEnabled) WhatsAppCallListener.reconnect(this)
        }, LISTENER_WAIT_MILLIS)
    }

    private companion object {
        const val LISTENER_WAIT_MILLIS = 5_000L
    }
}

val Context.container: AppContainer get() = (applicationContext as BcsApp).container
