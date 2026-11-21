package io.github.aryan007h.musicoonline.platform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Lightweight JSON-backed [PlatformPrefs] for Desktop JVM.
 * Persists to `<dataDir>/ui_prefs.json` atomically so settings survive restarts.
 */
class DesktopPlatformPrefs(private val file: File) : PlatformPrefs {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val values = LinkedHashMap<String, String>()

    init {
        load()
    }

    @Synchronized
    private fun load() {
        if (!file.isFile) return
        runCatching {
            val obj = json.parseToJsonElement(file.readText()).jsonObject
            obj.forEach { (k, v) ->
                (v as? JsonPrimitive)?.contentOrNull?.let { values[k] = it }
            }
        }
    }

    @Synchronized
    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            val obj: JsonObject = buildJsonObject {
                values.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
            }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(JsonObject.serializer(), obj))
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    @Synchronized
    override fun getString(key: String, default: String?): String? = values[key] ?: default

    @Synchronized
    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
        persist()
    }

    @Synchronized
    override fun getBoolean(key: String, default: Boolean): Boolean =
        values[key]?.toBooleanStrictOrNull() ?: default

    @Synchronized
    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value.toString()
        persist()
    }

    @Synchronized
    override fun getFloat(key: String, default: Float): Float =
        values[key]?.toFloatOrNull() ?: default

    @Synchronized
    override fun putFloat(key: String, value: Float) {
        values[key] = value.toString()
        persist()
    }

    @Synchronized
    override fun getInt(key: String, default: Int): Int =
        values[key]?.toIntOrNull() ?: default

    @Synchronized
    override fun putInt(key: String, value: Int) {
        values[key] = value.toString()
        persist()
    }

    @Synchronized
    override fun getLong(key: String, default: Long): Long =
        values[key]?.toLongOrNull() ?: default

    @Synchronized
    override fun putLong(key: String, value: Long) {
        values[key] = value.toString()
        persist()
    }
}
