#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <thread>

#include <oboe/Oboe.h>

#include "LockFreeSpscRingBuffer.h"
#include "RealtimeAudioProcessor.h"

namespace budsia::audio {

class NativeAudioEngine {
public:
    static constexpr std::size_t kWaveformPoints = 72;
    static constexpr std::size_t kSignalMetricCount = 8;
    static constexpr std::size_t kStatCount = 30;

    enum class EngineState : std::int64_t {
        Stopped = 0,
        Starting = 1,
        Running = 2,
        Stopping = 3,
        Error = 4,
    };

    NativeAudioEngine();
    ~NativeAudioEngine();

    NativeAudioEngine(const NativeAudioEngine&) = delete;
    NativeAudioEngine& operator=(const NativeAudioEngine&) = delete;

    int start(
        int inputDeviceId,
        int outputDeviceId,
        ProcessingMode mode,
        bool communicationMode
    );
    void stop();

    int setMonitoring(bool enabled);
    void setProcessingMode(ProcessingMode mode);

    // AI transport is consumed/produced only by the non-realtime AI worker.
    std::size_t readAiInput(float* destination, std::size_t count) noexcept;
    std::size_t writeAiOutput(const float* source, std::size_t count) noexcept;
    void clearAiTransport() noexcept;

    [[nodiscard]] std::array<std::int64_t, kStatCount> snapshotStats() const;
    [[nodiscard]] std::array<float, kSignalMetricCount> snapshotSignalMetrics() const;
    [[nodiscard]] std::array<float, kWaveformPoints> snapshotWaveform() const;
    [[nodiscard]] std::string lastError() const;

private:
    class InputCallback final : public oboe::AudioStreamDataCallback {
    public:
        explicit InputCallback(NativeAudioEngine& owner) : owner_(owner) {}

        oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* stream,
            void* audioData,
            int32_t numFrames
        ) override;

    private:
        NativeAudioEngine& owner_;
    };

    class OutputCallback final : public oboe::AudioStreamDataCallback {
    public:
        explicit OutputCallback(NativeAudioEngine& owner) : owner_(owner) {}

        oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* stream,
            void* audioData,
            int32_t numFrames
        ) override;

    private:
        NativeAudioEngine& owner_;
    };

    class ErrorCallback final : public oboe::AudioStreamErrorCallback {
    public:
        explicit ErrorCallback(NativeAudioEngine& owner) : owner_(owner) {}

        void onErrorAfterClose(
            oboe::AudioStream* stream,
            oboe::Result error
        ) override;

    private:
        NativeAudioEngine& owner_;
    };

    struct SignalSnapshot {
        float rawRms = 0.0f;
        float rawPeak = 0.0f;
        float rawDcOffset = 0.0f;
        float rawClippingRatio = 0.0f;
        float processedRms = 0.0f;
        float processedPeak = 0.0f;
        float processedDcOffset = 0.0f;
        float processedClippingRatio = 0.0f;
        std::array<float, kWaveformPoints> waveform{};
    };

    static constexpr std::size_t kRingCapacity = 1u << 16;

    oboe::Result openInputStream(
        int requestedDeviceId,
        bool communicationMode
    );
    oboe::Result openOutputStream(int requestedDeviceId);
    void closeStreams() noexcept;
    void resetRuntimeState() noexcept;
    void processingLoop();

    oboe::DataCallbackResult onInputAudioReady(
        oboe::AudioStream* stream,
        float* samples,
        int32_t numFrames
    ) noexcept;

    oboe::DataCallbackResult onOutputAudioReady(
        oboe::AudioStream* stream,
        float* samples,
        int32_t numFrames
    ) noexcept;

    void onStreamError(
        oboe::AudioStream* stream,
        oboe::Result error
    ) noexcept;

    void setLastError(int code, const std::string& message);
    static std::int64_t monotonicNanos() noexcept;
    static void updateMax(std::atomic<std::int64_t>& target, std::int64_t value) noexcept;
    static void updateHighWatermark(
        std::atomic<std::int64_t>& target,
        std::size_t value
    ) noexcept;

    mutable std::mutex lifecycleMutex_;
    mutable std::mutex signalMutex_;
    mutable std::mutex errorMutex_;

    std::shared_ptr<InputCallback> inputCallback_;
    std::shared_ptr<OutputCallback> outputCallback_;
    std::shared_ptr<ErrorCallback> errorCallback_;

    std::shared_ptr<oboe::AudioStream> inputStream_;
    std::shared_ptr<oboe::AudioStream> outputStream_;

    LockFreeSpscRingBuffer<float, kRingCapacity> inputRing_;
    LockFreeSpscRingBuffer<float, kRingCapacity> aiInputRing_;
    LockFreeSpscRingBuffer<float, kRingCapacity> outputRing_;

    HighPassProcessor highPassProcessor_;
    SignalSnapshot signalSnapshot_;

    std::thread processingThread_;
    std::atomic<bool> workerRunning_{false};
    std::atomic<bool> monitorEnabled_{false};
    std::atomic<int> processingMode_{
        static_cast<int>(ProcessingMode::Dsp)
    };
    std::atomic<EngineState> state_{EngineState::Stopped};

    std::atomic<int> inputSampleRate_{0};
    std::atomic<int> outputSampleRate_{0};
    std::atomic<int> inputDeviceId_{0};
    std::atomic<int> outputDeviceId_{0};
    std::atomic<int> inputSharingMode_{0};
    std::atomic<int> outputSharingMode_{0};
    std::atomic<bool> outputAvailable_{false};
    std::atomic<int> lastErrorCode_{0};

    std::atomic<std::int64_t> inputFrames_{0};
    std::atomic<std::int64_t> processedFrames_{0};
    std::atomic<std::int64_t> outputFrames_{0};
    std::atomic<std::int64_t> droppedInputSamples_{0};
    std::atomic<std::int64_t> outputOverrunSamples_{0};
    std::atomic<std::int64_t> outputUnderrunSamples_{0};
    std::atomic<std::int64_t> inputCallbacks_{0};
    std::atomic<std::int64_t> outputCallbacks_{0};
    std::atomic<std::int64_t> maxInputCallbackNanos_{0};
    std::atomic<std::int64_t> maxOutputCallbackNanos_{0};
    std::atomic<std::int64_t> lastProcessorNanos_{0};
    std::atomic<std::int64_t> maxProcessorNanos_{0};
    std::atomic<std::int64_t> inputRingHighWatermark_{0};
    std::atomic<std::int64_t> outputRingHighWatermark_{0};
    std::atomic<std::int64_t> disconnectCount_{0};
    std::atomic<std::int64_t> aiInputDroppedSamples_{0};
    std::atomic<std::int64_t> aiEnhancedSamples_{0};

    std::size_t processingBlockSamples_ = 480;
    std::string lastErrorText_;
};

}  // namespace budsia::audio
