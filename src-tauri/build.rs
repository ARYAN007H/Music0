fn main() {
    tauri_build::build();

    // The audio stack (cpal → oboe) is C++; on Android the NDK's shared
    // runtime must be linked explicitly or the .so carries undefined
    // __cxa_* symbols with no DT_NEEDED to satisfy them — dlopen fails at
    // Activity.onCreate ("cannot locate symbol __cxa_pure_virtual") and the
    // app crashes instantly on launch.  Desktop targets are untouched.
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("android") {
        println!("cargo:rustc-link-lib=dylib=c++_shared");
    }
}
