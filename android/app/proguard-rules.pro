# JNI bridge — the entry symbols are looked up by name from Rust.
-keep class io.github.aryan007h.musicoonline.core.MusicoCore { *; }
-keep class io.github.aryan007h.musicoonline.MainActivity { *; }
-keep class io.github.aryan007h.musicoonline.player.PlaybackService { *; }
# kotlinx-serialization DTOs mirror the Rust wire format.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class io.github.aryan007h.musicoonline.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.aryan007h.musicoonline.** {
    kotlinx.serialization.KSerializer serializer(...);
}
