package com.kesepain.kemoapp.ui.components

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val PrettyJson = kotlinx.serialization.json.Json { prettyPrint = true }

class JsonRecord internal constructor(internal val source: JsonObject) {
    fun text(vararg keys: String): String {
        keys.forEach { key ->
            val value = source[key]
            if (value is JsonPrimitive) value.contentOrNull?.takeIf(String::isNotBlank)?.let { return it }
        }
        return ""
    }

    fun boolean(vararg keys: String): Boolean? {
        keys.forEach { key -> (source[key] as? JsonPrimitive)?.booleanOrNull?.let { return it } }
        return null
    }

    fun number(vararg keys: String): Double? {
        keys.forEach { key -> (source[key] as? JsonPrimitive)?.doubleOrNull?.let { return it } }
        return null
    }

    fun children(vararg keys: String): List<JsonRecord> {
        keys.forEach { key ->
            val records = source[key].directRecords()
            if (records.isNotEmpty()) return records
        }
        return emptyList()
    }

    fun values(exclude: Set<String> = emptySet(), limit: Int = 8): List<Pair<String, String>> = source.entries
        .asSequence()
        .filterNot { it.key in exclude }
        .mapNotNull { (key, value) -> value.scalarText()?.let { key to it } }
        .take(limit)
        .toList()

    fun raw(): JsonElement = source

    fun element(vararg keys: String): JsonElement? = keys.firstNotNullOfOrNull(source::get)
}

fun JsonElement?.records(vararg containerKeys: String): List<JsonRecord> {
    if (this == null || this is JsonNull) return emptyList()
    var explicitContainerFound = false
    containerKeys.forEach { key ->
        val found = findKey(key)
        if (found != null) explicitContainerFound = true
        if (key == "expands") found.groupedExpandRecords().takeIf { it.isNotEmpty() }?.let { return it }
        if (key == "knowledge") found.groupedItemRecords().takeIf { it.isNotEmpty() }?.let { return it }
        if (key == "sources") found.sourceRecords().takeIf { it.isNotEmpty() }?.let { return it }
        found?.directRecords()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    // An explicitly present but empty collection is authoritative. Falling
    // through to the response envelope would turn path/count/pagination
    // metadata into a fake blank record on clean installations.
    if (explicitContainerFound) return emptyList()
    val direct = directRecords()
    if (direct.any(JsonRecord::hasIdentity)) return direct
    val nested = mutableListOf<JsonRecord>()
    collectIdentifiedRecords(this, nested)
    return nested.distinctBy { it.text("id", "plan_id", "task_id", "path", "name", "module_name", "title", "model") }
        .ifEmpty { if (this is JsonObject && values.any { it is JsonPrimitive }) listOf(JsonRecord(this)) else direct }
}

fun JsonElement?.stringItems(vararg containerKeys: String): List<String> {
    if (this == null) return emptyList()
    containerKeys.forEach { key ->
        val found = findKey(key)
        if (found is JsonArray) {
            val values = found.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            if (values.isNotEmpty()) return values
        }
    }
    return if (this is JsonArray) mapNotNull { (it as? JsonPrimitive)?.contentOrNull } else emptyList()
}

fun JsonElement?.metricValues(limit: Int = 8): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    fun walk(value: JsonElement, path: String, depth: Int) {
        if (result.size >= limit || depth > 3) return
        when (value) {
            is JsonObject -> value.forEach { (key, nested) -> walk(nested, if (path.isBlank()) key else "$path · $key", depth + 1) }
            is JsonPrimitive -> value.contentOrNull?.let { result += path to it }
            else -> Unit
        }
    }
    this?.let { walk(it, "", 0) }
    return result.filter { it.first.isNotBlank() }.take(limit)
}

fun JsonElement?.pretty(): String = when (this) {
    null, JsonNull -> ""
    is JsonPrimitive -> if (isString) content else toString()
    else -> runCatching {
        PrettyJson.encodeToString(JsonElement.serializer(), this)
    }.getOrDefault(toString())
}

fun JsonElement?.summary(): String = when (this) {
    null, JsonNull -> "—"
    is JsonArray -> size.toString()
    is JsonObject -> size.toString()
    is JsonPrimitive -> contentOrNull.orEmpty()
}

fun cronPayload(title: String, type: String, schedule: String, enabled: Boolean = true): String = buildJsonObject {
    put("title", title)
    put("type", type)
    put("schedule", schedule)
    put("enabled", enabled)
}.toString()

fun enabledPayload(enabled: Boolean): String = buildJsonObject { put("enabled", enabled) }.toString()

private fun JsonElement?.directRecords(): List<JsonRecord> = when (this) {
    is JsonArray -> mapNotNull { (it as? JsonObject)?.let(::JsonRecord) }
    is JsonObject -> values.mapNotNull { (it as? JsonObject)?.let(::JsonRecord) }
    else -> emptyList()
}

private fun JsonElement?.groupedExpandRecords(): List<JsonRecord> {
    val groups = this as? JsonArray ?: return emptyList()
    return groups.flatMap { groupElement ->
        val group = groupElement as? JsonObject ?: return@flatMap emptyList()
        val scope = (group["scope"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val items = group["items"] as? JsonArray ?: return@flatMap emptyList()
        items.mapNotNull { itemElement ->
            val item = itemElement as? JsonObject ?: return@mapNotNull null
            JsonRecord(buildJsonObject {
                item.forEach { (key, value) -> put(key, value) }
                if ("scope" !in item && scope.isNotBlank()) put("scope", scope)
            })
        }
    }
}

private fun JsonElement?.groupedItemRecords(): List<JsonRecord> {
    val groups = this as? JsonArray ?: return emptyList()
    return groups.flatMap { groupElement ->
        val group = groupElement as? JsonObject ?: return@flatMap emptyList()
        val scope = (group["scope"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val items = group["items"] as? JsonArray ?: return@flatMap listOf(JsonRecord(group))
        items.mapNotNull { itemElement ->
            val item = itemElement as? JsonObject ?: return@mapNotNull null
            JsonRecord(buildJsonObject {
                item.forEach { (key, value) -> put(key, value) }
                if ("source" !in item && scope.isNotBlank()) put("source", scope)
            })
        }
    }
}

private fun JsonElement?.sourceRecords(): List<JsonRecord> = when (this) {
    is JsonArray -> mapNotNull { item ->
        when (item) {
            is JsonObject -> JsonRecord(item)
            is JsonPrimitive -> item.contentOrNull?.let { name -> JsonRecord(buildJsonObject { put("name", name) }) }
            else -> null
        }
    }
    else -> emptyList()
}

private fun JsonElement.findKey(target: String): JsonElement? = when (this) {
    is JsonObject -> this[target] ?: values.firstNotNullOfOrNull { it.findKey(target) }
    is JsonArray -> firstNotNullOfOrNull { it.findKey(target) }
    else -> null
}

private fun JsonElement.scalarText(): String? = when (this) {
    is JsonPrimitive -> contentOrNull
    else -> null
}

private fun JsonRecord.hasIdentity(): Boolean = text("id", "plan_id", "task_id", "path", "name", "module_name", "title", "model").isNotBlank()

private fun collectIdentifiedRecords(value: JsonElement, destination: MutableList<JsonRecord>) {
    when (value) {
        is JsonObject -> {
            val record = JsonRecord(value)
            if (record.hasIdentity()) destination += record
            value.values.forEach { collectIdentifiedRecords(it, destination) }
        }
        is JsonArray -> value.forEach { collectIdentifiedRecords(it, destination) }
        else -> Unit
    }
}
