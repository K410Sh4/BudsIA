#include <jni.h>

#include <algorithm>
#include <array>
#include <cstdint>
#include <new>

#include "NativeAudioEngine.h"

namespace {

using budsia::audio::NativeAudioEngine;
using budsia::audio::ProcessingMode;

NativeAudioEngine* fromHandle(jlong handle) {
    return reinterpret_cast<NativeAudioEngine*>(
        static_cast<std::intptr_t>(handle)
    );
}

jlong toHandle(NativeAudioEngine* engine) {
    return static_cast<jlong>(
        reinterpret_cast<std::intptr_t>(engine)
    );
}

ProcessingMode toProcessingMode(jint value) {
    if (value == 0) return ProcessingMode::Raw;
    if (value == 2) return ProcessingMode::Ai;
    return ProcessingMode::Dsp;
}

}  // namespace

extern "C"
JNIEXPORT jlong JNICALL
Java_com_k410sh4_budsia_core_audio_nativecore_NativeAudioBridge_nativeCreate(
    JNIEnv*,
    jobject
) {
    try {
        return toHandle(new NativeAudioEngine());
    } catch (const std::bad_alloc&) {
        return 0;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_k410sh4_budsia_core_audio_nativecore_NativeAudioBridge_nativeDestroy(
    JNIEnv*,
    jobject,
    jlong handle
) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr) return;
    delete engine;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_k410sh4_budsia_core_audio_nativecore_NativeAudioBridge_nativeStart(
    JNIEnv*,
    jobject,
    jlong handle,
    jint inputDeviceId,
    jint outputDeviceId,
    jint processingMode
) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr) return -20001;

    return engine->start(
        inputDeviceId,
        outputDeviceId,
        toProcessingMode(processingMode)
    );
}

extern "C"
JNIEXPORT void JNICALL
Java_com_k410sh4_budsia_core_audio_nativecore_NativeAudioBridge_nativeStop(
    JNIEnv*,
    jobject,
    jlong handle
) {
    auto* engine = fromHandle(handle);
    if (engine != nullptr) engine->stop();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_k410sh4_budsia_core_audio_nativecore_NativeAudioBridge_nativeSetMonitoring(
    JNIEnv*,
    jobject,
    jlong handle,
    jboolean enabled
) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr) return -20001;

    return engine->setMonitoring(enabled == JNI_TRUE);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_k410sh4_budsia_core_audio_nativecore_NativeAudioBridge_nativeSetProcessingMode(
    JNIEnv*,
    jobject,
    jlong handle,
    jint processingMode
) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr) return;

    engine->setProcessingMode(toProcessingMode(processingMode));
}

extern "C"
JNIEXPORT jlongArray JNICALL
Java_com_k410sh4_budsia_core_audio_nativecore_NativeAudioBridge_nativeGetStats(
    JNIEnv* env,
    jobject,
    jlong handle
) {
    auto* engine = fromHandle(handle);
    std::array<std::int64_t, NativeAudioEngine::kStatCount> stats{};

    if (engine != nullptr) {
        stats = engine->snapshotStats();
    }

    jlongArray result = env->NewLongArray(
        static_cast<jsize>(stats.size())
    );
    if (result == nullptr) return nullptr;

    env->SetLongArrayRegion(
        result,
        0,
        static_cast<jsize>(stats.size()),
        reinterpret_cast<const jlong*>(stats.data())
    );
    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_k410sh4_budsia_core_audio_nativecore_NativeAudioBridge_nativeGetSignalMetrics(
    JNIEnv* env,
    jobject,
    jlong handle
) {
    auto* engine = fromHandle(handle);
    std::array<float, NativeAudioEngine::kSignalMetricCount> metrics{};

    if (engine != nullptr) {
        metrics = engine->snapshotSignalMetrics();
    }

    jfloatArray result = env->NewFloatArray(
        static_cast<jsize>(metrics.size())
    );
    if (result == nullptr) return nullptr;

    env->SetFloatArrayRegion(
        result,
        0,
        static_cast<jsize>(metrics.size()),
        metrics.data()
    );
    return result;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_k410sh4_budsia_core_audio_nativecore_NativeAudioBridge_nativeGetWaveform(
    JNIEnv* env,
    jobject,
    jlong handle
) {
    auto* engine = fromHandle(handle);
    std::array<float, NativeAudioEngine::kWaveformPoints> waveform{};

    if (engine != nullptr) {
        waveform = engine->snapshotWaveform();
    }

    jfloatArray result = env->NewFloatArray(
        static_cast<jsize>(waveform.size())
    );
    if (result == nullptr) return nullptr;

    env->SetFloatArrayRegion(
        result,
        0,
        static_cast<jsize>(waveform.size()),
        waveform.data()
    );
    return result;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_k410sh4_budsia_core_audio_nativecore_NativeAudioBridge_nativeGetLastError(
    JNIEnv* env,
    jobject,
    jlong handle
) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr) {
        return env->NewStringUTF("Native audio engine handle is invalid.");
    }

    const auto message = engine->lastError();
    return env->NewStringUTF(message.c_str());
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_k410sh4_budsia_core_audio_nativecore_NativeAudioBridge_nativeReadAiInput(
    JNIEnv* env,
    jobject,
    jlong handle,
    jfloatArray destination,
    jint requestedCount
) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr || destination == nullptr || requestedCount <= 0) {
        return 0;
    }

    const jsize arrayLength = env->GetArrayLength(destination);
    const jsize count = std::min(arrayLength, requestedCount);
    if (count <= 0) return 0;

    jboolean isCopy = JNI_FALSE;
    jfloat* samples = env->GetFloatArrayElements(destination, &isCopy);
    if (samples == nullptr) return 0;

    const auto read = engine->readAiInput(
        samples,
        static_cast<std::size_t>(count)
    );

    env->ReleaseFloatArrayElements(destination, samples, 0);
    return static_cast<jint>(read);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_k410sh4_budsia_core_audio_nativecore_NativeAudioBridge_nativeWriteAiOutput(
    JNIEnv* env,
    jobject,
    jlong handle,
    jfloatArray source,
    jint requestedCount
) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr || source == nullptr || requestedCount <= 0) {
        return 0;
    }

    const jsize arrayLength = env->GetArrayLength(source);
    const jsize count = std::min(arrayLength, requestedCount);
    if (count <= 0) return 0;

    jboolean isCopy = JNI_FALSE;
    jfloat* samples = env->GetFloatArrayElements(source, &isCopy);
    if (samples == nullptr) return 0;

    const auto written = engine->writeAiOutput(
        samples,
        static_cast<std::size_t>(count)
    );

    env->ReleaseFloatArrayElements(source, samples, JNI_ABORT);
    return static_cast<jint>(written);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_k410sh4_budsia_core_audio_nativecore_NativeAudioBridge_nativeClearAiTransport(
    JNIEnv*,
    jobject,
    jlong handle
) {
    auto* engine = fromHandle(handle);
    if (engine != nullptr) {
        engine->clearAiTransport();
    }
}
