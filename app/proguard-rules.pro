-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# BudsIA JNI entrypoints use name-based native symbols.
-keepclasseswithmembernames,includedescriptorclasses class com.k410sh4.budsia.core.audio.nativecore.** {
    native <methods>;
}

# sherpa-onnx 1.13.8 JNI resolves these Kotlin ABI classes by exact package,
# class, constructor and native method names. Keep the surface stable in release.
-keep class com.k2fsa.sherpa.onnx.** { *; }
