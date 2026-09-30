# Remove low-value application logging from optimized releases.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# BudsIA native bridge is name-bound through static JNI exports.
-keep class com.k410sh4.budsia.core.audio.nativecore.NativeAudioBridge { *; }

# sherpa-onnx 1.13.8 JNI resolves Kotlin ABI classes, fields, constructors and
# native methods by their exact JVM names.
-keep class com.k2fsa.sherpa.onnx.** { *; }
