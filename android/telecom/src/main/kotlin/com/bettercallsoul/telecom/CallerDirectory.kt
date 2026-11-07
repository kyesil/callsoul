package com.bettercallsoul.telecom

import com.bettercallsoul.diag.DebugLog
import com.bettercallsoul.calllog.CallLogRepository
import com.bettercallsoul.calllog.ContactsRepository
import com.bettercallsoul.calllog.LocalDao
import com.bettercallsoul.calllog.RingTime
import com.bettercallsoul.calllog.SpamCacheEntry
import com.bettercallsoul.spam.LookupResult
import com.bettercallsoul.spam.PhoneNumbers
import com.bettercallsoul.spam.SpamCategory
import com.bettercallsoul.spam.SpamClient
import com.bettercallsoul.spam.Verdict

/** Everything the call screens show about the other side. */
data class CallerInfo(
    val number: String,
    val title: String,
    val subtitle: String? = null,
    val photoUri: String? = null,
    val isContact: Boolean = false,
    val isTagged: Boolean = false,
    val verdict: Verdict = Verdict.Unknown,
    val category: SpamCategory? = null,
    val spamVotes: Int = 0,
    val lastCallAt: Long? = null,
    /** Set for saved contacts, so the call screen can show the rest of their card. */
    val contactId: Long? = null,
)

class CallerDirectory(
    private val contacts: ContactsRepository,
    private val callLog: CallLogRepository,
    private val store: LocalDao,
    private val spam: SpamClient,
    private val lookupAllowed: () -> Boolean,
) {
    suspend fun saveRingTime(number: String, date: Long, seconds: Long) {
        store.saveRingTime(RingTime(number = number, date = date, seconds = seconds))
    }

    /** Answers from the phone alone, so the screen has something to show straight away. */
    suspend fun local(number: String): CallerInfo {
        val lastCallAt = callLog.lastCall(number)?.date
        val contact = contacts.match(number)
        if (contact != null) {
            return CallerInfo(
                number = number,
                title = contact.name,
                subtitle = listOfNotNull(contact.company, contact.jobTitle).joinToString(" · ").ifEmpty { contact.note },
                photoUri = contact.photoUri,
                isContact = true,
                lastCallAt = lastCallAt,
                contactId = contact.id,
            )
        }
        val key = keyOf(number)
        val tag = store.tag(key)
        val cached = store.cached(key)
        val base = CallerInfo(
            number = number,
            title = tag?.label ?: cached?.callerName ?: number,
            subtitle = if (tag != null || cached?.callerName != null) number else null,
            isTagged = tag != null,
            lastCallAt = lastCallAt,
        )
        return if (cached == null) base else base.copy(
            verdict = runCatching { Verdict.valueOf(cached.verdict) }.getOrDefault(Verdict.Unknown),
            category = cached.category?.let { runCatching { SpamCategory.valueOf(it) }.getOrNull() },
            spamVotes = cached.spamVotes,
        )
    }

    /** Numbers saved in the address book are never sent to the server. */
    suspend fun remote(info: CallerInfo): CallerInfo {
        if (info.isContact || !lookupAllowed()) return info
        val e164 = PhoneNumbers.toE164(info.number) ?: return info
        val started = System.currentTimeMillis()
        val result = spam.lookup(e164)
        DebugLog.i(
            "Lookup",
            "${DebugLog.mask(e164)} -> ${result?.let { "${it.verdict}, ${it.spamVotes} spam / ${it.safeVotes} safe" } ?: "no answer"} " +
                "in ${System.currentTimeMillis() - started} ms",
        )
        result ?: return info
        store.saveCached(result.toCache(keyOf(info.number)))
        val name = result.callerName.takeUnless { info.isTagged }
        return info.copy(
            title = name ?: info.title,
            subtitle = if (name != null) info.number else info.subtitle,
            verdict = result.verdict,
            category = result.category,
            spamVotes = result.spamVotes,
        )
    }

    private fun LookupResult.toCache(key: String) = SpamCacheEntry(
        number = key,
        verdict = verdict.name,
        spamVotes = spamVotes,
        category = category?.name,
        callerName = callerName,
        fetchedAt = System.currentTimeMillis(),
    )

    companion object {
        /** One key per number however the network or the user formatted it. */
        fun keyOf(number: String): String = PhoneNumbers.toE164(number) ?: number
    }
}
