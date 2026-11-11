package com.bettercallsoul.calllog

import android.provider.ContactsContract.CommonDataKinds.Event
import java.util.Locale

/**
 * [target] with [source] merged into it, [source] taking precedence: its name, work, nickname and
 * note win where it has them, and its values come first and set the type of a value both have. The
 * values only [target] has stay, and so do its row ids, so saving it changes only what differs.
 */
internal fun mergeDrafts(target: ContactDraft, source: ContactDraft, customFields: Boolean): ContactDraft {
    fun pick(theirs: String, ours: String) = ours.trim().ifEmpty { theirs }
    val name = source.nameParts.any(String::isNotEmpty)
    val work = listOf(source.company, source.jobTitle, source.department).any(String::isNotBlank)
    var merged = target.copy(
        prefix = if (name) source.prefix else target.prefix,
        given = if (name) source.given else target.given,
        middle = if (name) source.middle else target.middle,
        family = if (name) source.family else target.family,
        suffix = if (name) source.suffix else target.suffix,
        phoneticGiven = if (name) source.phoneticGiven else target.phoneticGiven,
        phoneticMiddle = if (name) source.phoneticMiddle else target.phoneticMiddle,
        phoneticFamily = if (name) source.phoneticFamily else target.phoneticFamily,
        nickname = pick(target.nickname, source.nickname),
        company = if (work) source.company else target.company,
        jobTitle = if (work) source.jobTitle else target.jobTitle,
        department = if (work) source.department else target.department,
        note = pick(target.note, source.note),
        customFields = if (customFields) mergeCustom(target.customFields, source.customFields) else target.customFields,
        groups = target.groups + source.groups,
    )
    ContactField.entries.forEach { field -> merged = merged.with(field, mergeValues(field, target.values(field), source.values(field))) }
    return merged
}

private fun mergeValues(field: ContactField, theirs: List<EditableValue>, ours: List<EditableValue>): List<EditableValue> {
    val used = HashSet<Int>()
    val seen = HashSet<String>()
    val fromSource = ours.filter { it.value.isNotBlank() && seen.add(valueKey(field, it)) }.map { value ->
        val index = theirs.indices.firstOrNull { it !in used && same(field, theirs[it], value) }
        if (index == null) {
            value.copy(dataId = null)
        } else {
            used += index
            theirs[index].copy(value = value.value, type = value.type, label = value.label)
        }
    }
    return fromSource + theirs.filterIndexed { index, _ -> index !in used }
}

/** One birthday and one anniversary to a person: the source's replaces the other's. */
private val ONE_A_PERSON = setOf(Event.TYPE_BIRTHDAY, Event.TYPE_ANNIVERSARY)

private fun same(field: ContactField, a: EditableValue, b: EditableValue): Boolean =
    valueKey(field, a) == valueKey(field, b) || (field == ContactField.Event && a.type == b.type && a.type in ONE_A_PERSON)

/** What makes two values the same: a number's last ten digits, other text without case or extra spaces. */
private fun valueKey(field: ContactField, value: EditableValue): String {
    val text = value.value.trim()
    if (field == ContactField.Phone) DuplicateContacts.numberKey(text)?.let { return it }
    return text.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}

private fun mergeCustom(theirs: List<CustomField>, ours: List<CustomField>): List<CustomField> {
    val used = HashSet<Int>()
    val fromSource = ours.filter { it.label.isNotBlank() || it.value.isNotBlank() }.map { field ->
        val index = theirs.indices.firstOrNull { it !in used && theirs[it].label.trim().equals(field.label.trim(), ignoreCase = true) }
        if (index == null) field.copy(dataId = null) else theirs[index].copy(value = field.value).also { used += index }
    }
    return fromSource + theirs.filterIndexed { index, _ -> index !in used }
}
