package io.github.aryan007h.musicoonline.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File

actual object MusicoCore {
    @Volatile
    private var initialized = false

    @Volatile
    private var _isLoaded = false

    @Volatile
    private var _loadError: String? = null

    actual val isLoaded: Boolean get() = _isLoaded
    actual val loadError: String? get() = _loadError

    init {
        loadDesktopNativeLibrary()
    }

    private fun loadDesktopNativeLibrary() {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val libFileName = when {
            os.contains("mac") || os.contains("darwin") -> "libmusico_online_lib.dylib"
            os.contains("win") -> "musico_online_lib.dll"
            else -> "libmusico_online_lib.so"
        }

        // 1. Explicit env override (CI / packaged app / custom dev path).
        val envPath = System.getenv("MUSICO_NATIVE_LIB")?.trim()?.takeIf { it.isNotEmpty() }
        if (envPath != null && File(envPath).isFile) {
            try {
                System.load(File(envPath).absolutePath)
                _isLoaded = true
                return
            } catch (t: Throwable) {
                _loadError = "Failed to load MUSICO_NATIVE_LIB ($envPath): ${t.message}"
            }
        }

        // 2. Standard java.library.path lookup.
        try {
            System.loadLibrary("musico_online_lib")
            _isLoaded = true
            return
        } catch (_: UnsatisfiedLinkError) {
            // Fall through to workspace Cargo build outputs.
        }

        // 3. Automatic Cargo workspace discovery (works directly with `gradle :desktopApp:run`).
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(cwd, "src-tauri/target/release/$libFileName"),
            File(cwd, "src-tauri/target/debug/$libFileName"),
            File(cwd, "../src-tauri/target/release/$libFileName"),
            File(cwd, "../src-tauri/target/debug/$libFileName"),
            File(cwd, "../../src-tauri/target/release/$libFileName"),
            File(cwd, "../../src-tauri/target/debug/$libFileName"),
            File(cwd, "target/release/$libFileName"),
            File(cwd, "target/debug/$libFileName"),
            File(cwd, "../target/release/$libFileName"),
            File(cwd, "../target/debug/$libFileName"),
        )
        for (candidate in candidates) {
            if (candidate.isFile) {
                try {
                    System.load(candidate.absolutePath)
                    _isLoaded = true
                    _loadError = null
                    return
                } catch (t: Throwable) {
                    _loadError = "Failed loading ${candidate.absolutePath}: ${t.message}"
                }
            }
        }

        _isLoaded = false
        if (_loadError == null) {
            _loadError = "Native core `$libFileName` not found. Build it once with: cargo build --release --manifest-path src-tauri/Cargo.toml --lib"
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
                throw CoreException(_loadError ?: "Native Rust core library not loaded")
            }
            MusicoCoreCodec.parseReply(invoke(cmd, args.toString()))
        }

    actual suspend fun ping(): JsonObject = call("ping")
}
