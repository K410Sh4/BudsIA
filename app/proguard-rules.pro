# Remove low-value application logging from optimized releases.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# JNI contracts use exact class/member names from native code.
# sherpa-onnx reads configuration fields with GetFieldID() and constructs
# DenoisedAudio using FindClass(), so obfuscating these types would compile
# successfully but fail at runtime.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# BudsIA's native bridge is also name-bound through JNI exports.
-keep class com.k410sh4.budsia.core.audio.nativecore.NativeAudioBridge { *; }
