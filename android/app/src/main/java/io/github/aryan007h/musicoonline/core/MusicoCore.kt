package io.github.aryan007h.musicoonline.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The single bridge into the shared Rust core (`libmusico_online_lib.so`).
 *
 * Contract (src-tauri/src/mobile.rs): `init(filesDir, cacheDir)` MUST run
 * before any `invoke` — the native app has no Tauri/tao runtime, so the
 * storage dirs the core resolves are passed in explicitly. `invoke` is a
 * blocking call; always go through [call] (Dispatchers.IO).
 *
 * Replies: `{"ok":true,"data":…}` / `{"ok":false,"error":"…"}`.
 */
object MusicoCore {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var initialized = false

    init {
        System.loadLibrary("musico_online_lib")
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initNative(context.filesDir.absolutePath, context.cacheDir.absolutePath)
            initialized = true
        }
    }

    private external fun initNative(filesDir: String, cacheDir: String)

    private external fun invoke(cmd: String, argsJson: String): String

    /** Blocking bridge call — run on IO. Throws [CoreException] on `ok:false`. */
    suspend fun call(cmd: String, args: JsonObject = buildJsonObject {}): JsonObject =
        withContext(Dispatchers.IO) {
            val reply = json.parseToJsonElement(invoke(cmd, args.toString())).let {
                require(it is JsonObject) { "bridge reply was not an object" }
                it
            }
            val ok = (reply["ok"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
            if (!ok) {
                throw CoreException((reply["error"] as? JsonPrimitive)?.content ?: "unknown error")
            }
            (reply["data"] as? JsonObject) ?: buildJsonObject { }
        }

    suspend fun ping(): JsonObject = call("ping")
}

class CoreException(message: String) : Exception(message)

/** Convenience for string args. */
fun argsOf(vararg pairs: Pair<String, String?>): JsonObject = buildJsonObject {
    pairs.forEach { (k, v) -> if (v != null) put(k, v) }
}
