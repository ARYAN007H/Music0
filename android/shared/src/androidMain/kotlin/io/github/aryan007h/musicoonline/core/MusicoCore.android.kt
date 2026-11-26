package io.github.aryan007h.musicoonline.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

actual object MusicoCore {
    private const val TAG = "MusicoCore"

    @Volatile
    private var initialized = false

    @Volatile
    private var _isLoaded = false

    @Volatile
    private var _loadError: String? = null

    actual val isLoaded: Boolean get() = _isLoaded
    actual val loadError: String? get() = _loadError

    init {
        try {
            System.loadLibrary("musico_online_lib")
            _isLoaded = true
        } catch (firstErr: UnsatisfiedLinkError) {
            // Defense-in-depth: if an APK ships libc++_shared without DT_NEEDED,
            // load it explicitly before retrying musico_online_lib.
            try {
                runCatching { System.loadLibrary("c++_shared") }
                System.loadLibrary("musico_online_lib")
                _isLoaded = true
            } catch (t: Throwable) {
                _isLoaded = false
                _loadError = t.message ?: firstErr.message ?: "Failed to load libmusico_online_lib.so"
                Log.e(TAG, "Failed to load native core library", t)
            }
        } catch (t: Throwable) {
            _isLoaded = false
            _loadError = t.message ?: "Failed to load libmusico_online_lib.so"
            Log.e(TAG, "Failed to load native core library", t)
        }
    }

    actual fun init(filesDir: String, cacheDir: String) {
        if (initialized || !_isLoaded) return
        synchronized(this) {
            if (initialized || !_isLoaded) return
            initNative(filesDir, cacheDir)
            initialized = true
        }
    }

    private external fun initNative(filesDir: String, cacheDir: String)

    private external fun invoke(cmd: String, argsJson: String): String

    actual suspend fun call(cmd: String, args: JsonObject): JsonObject {
        val data = callElement(cmd, args)
        return data as? JsonObject
            ?: throw CoreException("bridge contract: `$cmd` returned non-object data — wrap it Rust-side or use callElement")
    }

    actual suspend fun callElement(cmd: String, args: JsonObject): JsonElement =
        withContext(Dispatchers.IO) {
            if (!_isLoaded) {
                throw CoreException(_loadError ?: "Native Rust core library is not loaded")
            }
            MusicoCoreCodec.parseReply(invoke(cmd, args.toString()))
        }

    actual suspend fun ping(): JsonObject = call("ping")
}
