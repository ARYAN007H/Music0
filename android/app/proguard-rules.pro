# JNI bridge — the entry symbols are looked up by name from Rust.
-keep class io.github.aryan007h.musicoonline.MusicoCore { *; }
# kotlinx-serialization DTOs mirror the Rust wire format.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class io.github.aryan007h.musicoonline.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.aryan007h.musicoonline.** {
    kotlinx.serialization.KSerializer serializer(...);
}
