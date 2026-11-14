package desktop.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

// Padanan sempit dari org.json yang dipakai LocalHttpServer di Android (optString/optInt/...),
// supaya bentuk JSON dan perilaku nilai bawaannya tetap sama dengan versi HP.

internal fun parseObject(body: String): JsonObject {
    if (body.isBlank()) return JsonObject(emptyMap())
    return try {
        Json.parseToJsonElement(body) as? JsonObject ?: JsonObject(emptyMap())
    } catch (_: Exception) {
        JsonObject(emptyMap())
    }
}

private fun JsonObject.primitive(key: String): JsonPrimitive? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }

internal fun JsonObject.str(key: String, def: String = ""): String = primitive(key)?.content ?: def

internal fun JsonObject.dbl(key: String, def: Double = 0.0): Double {
    val p = primitive(key) ?: return def
    return p.doubleOrNull ?: p.content.toDoubleOrNull() ?: def
}

internal fun JsonObject.int(key: String, def: Int = 0): Int {
    val p = primitive(key) ?: return def
    return p.intOrNull ?: p.doubleOrNull?.toInt() ?: p.content.toIntOrNull() ?: def
}

internal fun JsonObject.bool(key: String, def: Boolean = false): Boolean {
    val p = primitive(key) ?: return def
    return p.booleanOrNull ?: when (p.content.lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> def
    }
}

internal fun JsonObject.has(key: String): Boolean = containsKey(key)

internal inline fun obj(build: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(build)

internal inline fun <T> List<T>.toJsonArray(item: JsonObjectBuilder.(T) -> Unit): JsonArray =
    buildJsonArray { forEach { add(buildJsonObject { item(it) }) } }

internal fun success(vararg extra: Pair<String, Any?>): JsonObject = obj {
    put("success", true)
    extra.forEach { (k, v) ->
        when (v) {
            null -> put(k, JsonNull)
            is Boolean -> put(k, v)
            is Number -> put(k, v)
            else -> put(k, v.toString())
        }
    }
}

internal fun failure(message: String): JsonObject = obj {
    put("success", false)
    put("message", message)
}
