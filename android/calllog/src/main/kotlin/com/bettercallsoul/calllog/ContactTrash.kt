package com.bettercallsoul.calllog

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The app's recycle bin. Deleting a contact here first keeps a copy of its fields and photo on the
 * phone, so it can be put back for [KEEP_DAYS] days. Google keeps its own trash for contacts synced
 * there, but that lives on Google's servers and has no Android API.
 */
class ContactTrash(context: Context, private val dao: LocalDao, private val editor: ContactEditor) {
    private val folder = File(context.filesDir, "trash").apply { mkdirs() }

    fun observe(): Flow<List<TrashedContact>> = dao.observeTrash()

    /** Moves the contacts to the bin; returns how many could be deleted (SIM and app-owned ones can't). */
    suspend fun trash(contactIds: Collection<Long>): Int = withContext(Dispatchers.IO) {
        var done = 0
        contactIds.forEach { contactId ->
            val stored = editor.load(contactId) ?: return@forEach
            val photo = editor.photoBytes(contactId)
            val id = dao.saveTrashed(
                TrashedContact(
                    name = stored.draft.displayName.ifBlank { stored.draft.company },
                    accountName = stored.account.name,
                    accountType = stored.account.type,
                    payload = encode(stored.draft, stored.starred),
                    hasPhoto = photo != null,
                    deletedAt = System.currentTimeMillis(),
                ),
            )
            photo?.let { photoFile(id).writeBytes(it) }
            editor.deleteContact(contactId)
            done++
        }
        done
    }

    /** Writes the contact back, into [to] or the account it came from. */
    suspend fun restore(entry: TrashedContact, to: ContactAccount = entry.account): Long? = withContext(Dispatchers.IO) {
        val (draft, starred) = decode(entry.payload)
        val photo = photoFile(entry.id).takeIf { entry.hasPhoto && it.exists() }?.readBytes()
        // Labels only exist in their own account, and may have been deleted since.
        val labels = if (to == entry.account) editor.groups(to).map { it.id }.toSet() else emptySet()
        val id = editor.create(
            draft.detached(keepGroups = true)
                .copy(groups = draft.groups intersect labels)
                .let { if (to.isGoogle) it else it.copy(customFields = emptyList()) },
            to,
            photo?.let(PhotoEdit::Bytes) ?: PhotoEdit.Keep,
            starred,
        )
        remove(entry)
        id
    }

    /** Deletes it for good. */
    suspend fun remove(entry: TrashedContact) = withContext(Dispatchers.IO) {
        dao.deleteTrashed(entry.id)
        photoFile(entry.id).delete()
    }

    /** Drops what has been in the bin longer than [KEEP_DAYS] days. */
    suspend fun purgeOld() = withContext(Dispatchers.IO) {
        val before = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(KEEP_DAYS.toLong())
        dao.trashedBefore(before).forEach { remove(it) }
    }

    /** The kept photo as a file:// address an image loader can show, or null. */
    fun photoUri(entry: TrashedContact): String? =
        photoFile(entry.id).takeIf { entry.hasPhoto && it.exists() }?.let { Uri.fromFile(it).toString() }

    private fun photoFile(id: Long) = File(folder, "$id.jpg")

    companion object {
        const val KEEP_DAYS = 30

        internal fun encode(draft: ContactDraft, starred: Boolean): String {
            fun values(list: List<EditableValue>) = JSONArray().apply {
                list.forEach { put(JSONObject().put("v", it.value).put("t", it.type).put("l", it.label ?: JSONObject.NULL)) }
            }
            return JSONObject()
                .put("prefix", draft.prefix).put("given", draft.given).put("middle", draft.middle)
                .put("family", draft.family).put("suffix", draft.suffix)
                .put("phoneticGiven", draft.phoneticGiven).put("phoneticMiddle", draft.phoneticMiddle)
                .put("phoneticFamily", draft.phoneticFamily).put("nickname", draft.nickname)
                .apply { ContactField.entries.forEach { put(it.name, values(draft.values(it))) } }
                .put("company", draft.company).put("jobTitle", draft.jobTitle).put("department", draft.department)
                .put("note", draft.note)
                .put("custom", JSONArray().apply { draft.customFields.forEach { put(JSONObject().put("l", it.label).put("v", it.value)) } })
                .put("groups", JSONArray(draft.groups.toList()))
                .put("starred", starred)
                .toString()
        }

        internal fun decode(payload: String): Pair<ContactDraft, Boolean> {
            val json = JSONObject(payload)
            fun values(name: String): List<EditableValue> {
                val array = json.optJSONArray(name) ?: return emptyList()
                return (0 until array.length()).map { i ->
                    val item = array.getJSONObject(i)
                    EditableValue(item.optString("v"), item.optInt("t"), if (item.isNull("l")) null else item.optString("l"))
                }
            }
            val custom = json.optJSONArray("custom")
            val groups = json.optJSONArray("groups")
            var draft = ContactDraft(
                prefix = json.optString("prefix"), given = json.optString("given"), middle = json.optString("middle"),
                family = json.optString("family"), suffix = json.optString("suffix"),
                phoneticGiven = json.optString("phoneticGiven"), phoneticMiddle = json.optString("phoneticMiddle"),
                phoneticFamily = json.optString("phoneticFamily"), nickname = json.optString("nickname"),
                company = json.optString("company"), jobTitle = json.optString("jobTitle"),
                department = json.optString("department"), note = json.optString("note"),
                customFields = custom?.let { a -> (0 until a.length()).map { CustomField(a.getJSONObject(it).optString("l"), a.getJSONObject(it).optString("v")) } }.orEmpty(),
                groups = groups?.let { a -> (0 until a.length()).map { a.getLong(it) }.toSet() }.orEmpty(),
            )
            ContactField.entries.forEach { draft = draft.with(it, values(it.name)) }
            return draft to json.optBoolean("starred")
        }
    }
}
