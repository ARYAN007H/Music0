package io.github.aryan007h.musicoonline.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Cross-platform JNI bridge into the shared Rust core (`musico_online_lib`).
 *
 * Implemented as `actual object MusicoCore` on Android and Desktop JVM so
 * both targets bind to the exact same JNI symbol names:
 * - `Java_io_github_aryan007h_musicoonline_core_MusicoCore_initNative`
 * - `Java_io_github_aryan007h_musicoonline_core_MusicoCore_invoke`
 */
expect object MusicoCore {
    val isLoaded: Boolean
    val loadError: String?

    fun init(filesDir: String, cacheDir: String)

    suspend fun call(cmd: String, args: JsonObject = buildJsonObject {}): JsonObject

    suspend fun callElement(cmd: String, args: JsonObject = buildJsonObject {}): JsonElement

    suspend fun ping(): JsonObject
}

internal object MusicoCoreCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseReply(raw: String): JsonElement {
        val reply = json.parseToJsonElement(raw).let {
            require(it is JsonObject) { "bridge reply was not an object" }
            it
        }
        val ok = (reply["ok"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
        if (!ok) {
            throw CoreException((reply["error"] as? JsonPrimitive)?.content ?: "unknown error")
        }
        return reply["data"] ?: JsonNull
    }
}

class CoreException(message: String) : Exception(message)

/** Convenience for string args. */
fun argsOf(vararg pairs: Pair<String, String?>): JsonObject = buildJsonObject {
    pairs.forEach { (k, v) -> if (v != null) put(k, v) }
}
