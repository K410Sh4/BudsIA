#include "NativeAudioEngine.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>
#include <thread>
#include <vector>

namespace budsia::audio {
namespace {

constexpr int kErrorUnsupportedInputFormat = -10001;
constexpr int kErrorOutputUnavailable = -10002;
constexpr int kErrorSampleRateMismatch = -10003;
constexpr int kErrorNotRunning = -10004;

struct Metrics {
    float rms = 0.0f;
    float peak = 0.0f;
    float dcOffset = 0.0f;
    float clippingRatio = 0.0f;
};

Metrics calculateMetrics(const float* samples, std::size_t count) noexcept {
    if (samples == nullptr || count == 0) return {};

    double sum = 0.0;
    double sumSquares = 0.0;
    float peak = 0.0f;
    std::size_t clipped = 0;

    for (std::size_t i = 0; i < count; ++i) {
        const float sample = samples[i];
        const float absolute = std::fabs(sample);
        sum += sample;
        sumSquares += static_cast<double>(sample) * sample;
        peak = std::max(peak, absolute);
        if (absolute >= 0.999f) ++clipped;
    }

    Metrics metrics;
    metrics.rms = static_cast<float>(
        std::sqrt(sumSquares / static_cast<double>(count))
    );
    metrics.peak = peak;
    metrics.dcOffset = static_cast<float>(sum / static_cast<double>(count));
    metrics.clippingRatio =
        static_cast<float>(clipped) / static_cast<float>(count);
    return metrics;
}

int sharingModeCode(oboe::SharingMode mode) noexcept {
    return mode == oboe::SharingMode::Exclusive ? 1 : 2;
}

}  // namespace

NativeAudioEngine::NativeAudioEngine()
    : inputCallback_(std::make_shared<InputCallback>(*this)),
      outputCallback_(std::make_shared<OutputCallback>(*this)),
      errorCallback_(std::make_shared<ErrorCallback>(*this)) {}

NativeAudioEngine::~NativeAudioEngine() {
    stop();
}

int NativeAudioEngine::start(
    int requestedInputDeviceId,
    int requestedOutputDeviceId,
    ProcessingMode mode
) {
    std::lock_guard<std::mutex> lock(lifecycleMutex_);

    if (state_.load(std::memory_order_acquire) == EngineState::Running) {
        return 0;
    }

    state_.store(EngineState::Starting, std::memory_order_release);
    resetRuntimeState();
    processingMode_.store(static_cast<int>(mode), std::memory_order_release);

    const auto inputResult = openInputStream(requestedInputDeviceId);
    if (inputResult != oboe::Result::OK || inputStream_ == nullptr) {
        const int code = static_cast<int>(inputResult);
        setLastError(
            code,
            std::string("Unable to open input stream: ") +
                oboe::convertToText(inputResult)
        );
        state_.store(EngineState::Error, std::memory_order_release);
        closeStreams();
        return code;
    }

    if (inputStream_->getFormat() != oboe::AudioFormat::Float ||
        inputStream_->getChannelCount() != 1) {
        setLastError(
            kErrorUnsupportedInputFormat,
            "Native input stream did not open as mono float PCM."
        );
        state_.store(EngineState::Error, std::memory_order_release);
        closeStreams();
        return kErrorUnsupportedInputFormat;
    }

    inputSampleRate_.store(
        inputStream_->getSampleRate(),
        std::memory_order_release
    );
    inputDeviceId_.store(
        inputStream_->getDeviceId(),
        std::memory_order_release
    );

    const auto outputResult = openOutputStream(requestedOutputDeviceId);
    if (outputResult == oboe::Result::OK && outputStream_ != nullptr) {
        outputAvailable_.store(true, std::memory_order_release);
        outputSampleRate_.store(
            outputStream_->getSampleRate(),
            std::memory_order_release
        );
        outputDeviceId_.store(
            outputStream_->getDeviceId(),
            std::memory_order_release
        );
    } else {
        outputAvailable_.store(false, std::memory_order_release);
        outputSampleRate_.store(0, std::memory_order_release);
        outputDeviceId_.store(0, std::memory_order_release);
    }

    const int sampleRate = inputSampleRate_.load(std::memory_order_acquire);
    processingBlockSamples_ = static_cast<std::size_t>(
        std::max(64, sampleRate / 100)
    );

    workerRunning_.store(true, std::memory_order_release);
    processingThread_ = std::thread(&NativeAudioEngine::processingLoop, this);

    const auto startResult = inputStream_->requestStart();
    if (startResult != oboe::Result::OK) {
        workerRunning_.store(false, std::memory_order_release);
        if (processingThread_.joinable()) processingThread_.join();

        const int code = static_cast<int>(startResult);
        setLastError(
            code,
            std::string("Unable to start input stream: ") +
                oboe::convertToText(startResult)
        );
        state_.store(EngineState::Error, std::memory_order_release);
        closeStreams();
        return code;
    }

    state_.store(EngineState::Running, std::memory_order_release);
    return 0;
}

void NativeAudioEngine::stop() {
    std::lock_guard<std::mutex> lock(lifecycleMutex_);

    const auto current = state_.load(std::memory_order_acquire);
    if (current == EngineState::Stopped) return;

    state_.store(EngineState::Stopping, std::memory_order_release);
    monitorEnabled_.store(false, std::memory_order_release);

    if (outputStream_ != nullptr) {
        outputStream_->requestStop();
    }
    if (inputStream_ != nullptr) {
        inputStream_->requestStop();
    }

    workerRunning_.store(false, std::memory_order_release);
    if (processingThread_.joinable()) {
        processingThread_.join();
    }

    closeStreams();
    inputRing_.clear();
    outputRing_.clear();
    highPassProcessor_.reset();

    state_.store(EngineState::Stopped, std::memory_order_release);
}

int NativeAudioEngine::setMonitoring(bool enabled) {
    std::lock_guard<std::mutex> lock(lifecycleMutex_);

    if (state_.load(std::memory_order_acquire) != EngineState::Running) {
        setLastError(kErrorNotRunning, "Audio engine is not running.");
        return kErrorNotRunning;
    }

    if (!enabled) {
        monitorEnabled_.store(false, std::memory_order_release);
        if (outputStream_ != nullptr) outputStream_->requestStop();
        outputRing_.clear();
        return 0;
    }

    if (!outputAvailable_.load(std::memory_order_acquire) ||
        outputStream_ == nullptr) {
        setLastError(kErrorOutputUnavailable, "No output stream is available.");
        return kErrorOutputUnavailable;
    }

    if (inputSampleRate_.load(std::memory_order_acquire) !=
        outputSampleRate_.load(std::memory_order_acquire)) {
        setLastError(
            kErrorSampleRateMismatch,
            "Input/output sample rates differ; live monitor remains disabled."
        );
        return kErrorSampleRateMismatch;
    }

    outputRing_.clear();
    monitorEnabled_.store(true, std::memory_order_release);

    // Prime one processing window before the output callback begins consuming.
    std::this_thread::sleep_for(std::chrono::milliseconds(12));

    const auto result = outputStream_->requestStart();
    if (result != oboe::Result::OK) {
        monitorEnabled_.store(false, std::memory_order_release);
        const int code = static_cast<int>(result);
        setLastError(
            code,
            std::string("Unable to start output stream: ") +
                oboe::convertToText(result)
        );
        return code;
    }

    return 0;
}

void NativeAudioEngine::setProcessingMode(ProcessingMode mode) {
    processingMode_.store(static_cast<int>(mode), std::memory_order_release);
    if (mode == ProcessingMode::Raw) {
        highPassProcessor_.reset();
    }
}

std::array<std::int64_t, NativeAudioEngine::kStatCount>
NativeAudioEngine::snapshotStats() const {
    std::array<std::int64_t, kStatCount> stats{};

    std::int64_t inputXruns = -1;
    std::int64_t outputXruns = -1;

    if (inputStream_ != nullptr) {
        const auto xruns = inputStream_->getXRunCount();
        if (xruns) inputXruns = xruns.value();
    }

    if (outputStream_ != nullptr) {
        const auto xruns = outputStream_->getXRunCount();
        if (xruns) outputXruns = xruns.value();
    }

    stats[0] = static_cast<std::int64_t>(
        state_.load(std::memory_order_acquire)
    );
    stats[1] = inputSampleRate_.load(std::memory_order_acquire);
    stats[2] = outputSampleRate_.load(std::memory_order_acquire);
    stats[3] = inputDeviceId_.load(std::memory_order_acquire);
    stats[4] = outputDeviceId_.load(std::memory_order_acquire);
    stats[5] = inputFrames_.load(std::memory_order_acquire);
    stats[6] = processedFrames_.load(std::memory_order_acquire);
    stats[7] = outputFrames_.load(std::memory_order_acquire);
    stats[8] = droppedInputSamples_.load(std::memory_order_acquire);
    stats[9] = outputOverrunSamples_.load(std::memory_order_acquire);
    stats[10] = outputUnderrunSamples_.load(std::memory_order_acquire);
    stats[11] = inputCallbacks_.load(std::memory_order_acquire);
    stats[12] = outputCallbacks_.load(std::memory_order_acquire);
    stats[13] = inputXruns;
    stats[14] = outputXruns;
    stats[15] = maxInputCallbackNanos_.load(std::memory_order_acquire);
    stats[16] = maxOutputCallbackNanos_.load(std::memory_order_acquire);
    stats[17] = lastProcessorNanos_.load(std::memory_order_acquire);
    stats[18] = maxProcessorNanos_.load(std::memory_order_acquire);
    stats[19] = inputRingHighWatermark_.load(std::memory_order_acquire);
    stats[20] = outputRingHighWatermark_.load(std::memory_order_acquire);
    stats[21] = disconnectCount_.load(std::memory_order_acquire);
    stats[22] = monitorEnabled_.load(std::memory_order_acquire) ? 1 : 0;
    stats[23] = processingMode_.load(std::memory_order_acquire);
    stats[24] = inputSharingMode_.load(std::memory_order_acquire);
    stats[25] = outputSharingMode_.load(std::memory_order_acquire);
    stats[26] = outputAvailable_.load(std::memory_order_acquire) ? 1 : 0;
    stats[27] = lastErrorCode_.load(std::memory_order_acquire);
    return stats;
}

std::array<float, NativeAudioEngine::kSignalMetricCount>
NativeAudioEngine::snapshotSignalMetrics() const {
    std::lock_guard<std::mutex> lock(signalMutex_);
    return {
        signalSnapshot_.rawRms,
        signalSnapshot_.rawPeak,
        signalSnapshot_.rawDcOffset,
        signalSnapshot_.rawClippingRatio,
        signalSnapshot_.processedRms,
        signalSnapshot_.processedPeak,
        signalSnapshot_.processedDcOffset,
        signalSnapshot_.processedClippingRatio,
    };
}

std::array<float, NativeAudioEngine::kWaveformPoints>
NativeAudioEngine::snapshotWaveform() const {
    std::lock_guard<std::mutex> lock(signalMutex_);
    return signalSnapshot_.waveform;
}

std::string NativeAudioEngine::lastError() const {
    std::lock_guard<std::mutex> lock(errorMutex_);
    return lastErrorText_;
}

oboe::Result NativeAudioEngine::openInputStream(int requestedDeviceId) {
    const std::array<oboe::SharingMode, 2> sharingModes{
        oboe::SharingMode::Exclusive,
        oboe::SharingMode::Shared,
    };
    const std::array<oboe::InputPreset, 2> presets{
        oboe::InputPreset::Unprocessed,
        oboe::InputPreset::VoiceRecognition,
    };

    oboe::Result lastResult = oboe::Result::ErrorUnavailable;

    for (const auto preset : presets) {
        for (const auto sharing : sharingModes) {
            oboe::AudioStreamBuilder builder;
            builder.setDirection(oboe::Direction::Input)
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setSharingMode(sharing)
                ->setFormat(oboe::AudioFormat::Float)
                ->setChannelCount(1)
                ->setInputPreset(preset)
                ->setDataCallback(inputCallback_)
                ->setErrorCallback(errorCallback_);

            if (requestedDeviceId > 0) {
                builder.setDeviceId(requestedDeviceId);
            }

            inputStream_.reset();
            lastResult = builder.openStream(inputStream_);
            if (lastResult == oboe::Result::OK && inputStream_ != nullptr) {
                inputSharingMode_.store(
                    sharingModeCode(sharing),
                    std::memory_order_release
                );
                return lastResult;
            }
        }
    }

    return lastResult;
}

oboe::Result NativeAudioEngine::openOutputStream(int requestedDeviceId) {
    const std::array<oboe::SharingMode, 2> sharingModes{
        oboe::SharingMode::Exclusive,
        oboe::SharingMode::Shared,
    };

    oboe::Result lastResult = oboe::Result::ErrorUnavailable;

    for (const auto sharing : sharingModes) {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(sharing)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(1)
            ->setDataCallback(outputCallback_)
            ->setErrorCallback(errorCallback_);

        if (requestedDeviceId > 0) {
            builder.setDeviceId(requestedDeviceId);
        }

        outputStream_.reset();
        lastResult = builder.openStream(outputStream_);
        if (lastResult == oboe::Result::OK && outputStream_ != nullptr) {
            outputSharingMode_.store(
                sharingModeCode(sharing),
                std::memory_order_release
            );
            return lastResult;
        }
    }

    return lastResult;
}

void NativeAudioEngine::closeStreams() noexcept {
    if (inputStream_ != nullptr) {
        inputStream_->close();
        inputStream_.reset();
    }

    if (outputStream_ != nullptr) {
        outputStream_->close();
        outputStream_.reset();
    }
}

void NativeAudioEngine::resetRuntimeState() noexcept {
    monitorEnabled_.store(false, std::memory_order_release);
    outputAvailable_.store(false, std::memory_order_release);

    inputSampleRate_.store(0, std::memory_order_release);
    outputSampleRate_.store(0, std::memory_order_release);
    inputDeviceId_.store(0, std::memory_order_release);
    outputDeviceId_.store(0, std::memory_order_release);
    inputSharingMode_.store(0, std::memory_order_release);
    outputSharingMode_.store(0, std::memory_order_release);
    lastErrorCode_.store(0, std::memory_order_release);

    inputFrames_.store(0, std::memory_order_release);
    processedFrames_.store(0, std::memory_order_release);
    outputFrames_.store(0, std::memory_order_release);
    droppedInputSamples_.store(0, std::memory_order_release);
    outputOverrunSamples_.store(0, std::memory_order_release);
    outputUnderrunSamples_.store(0, std::memory_order_release);
    inputCallbacks_.store(0, std::memory_order_release);
    outputCallbacks_.store(0, std::memory_order_release);
    maxInputCallbackNanos_.store(0, std::memory_order_release);
    maxOutputCallbackNanos_.store(0, std::memory_order_release);
    lastProcessorNanos_.store(0, std::memory_order_release);
    maxProcessorNanos_.store(0, std::memory_order_release);
    inputRingHighWatermark_.store(0, std::memory_order_release);
    outputRingHighWatermark_.store(0, std::memory_order_release);
    disconnectCount_.store(0, std::memory_order_release);

    inputRing_.clear();
    outputRing_.clear();
    highPassProcessor_.reset();

    {
        std::lock_guard<std::mutex> signalLock(signalMutex_);
        signalSnapshot_ = {};
    }
    {
        std::lock_guard<std::mutex> errorLock(errorMutex_);
        lastErrorText_.clear();
    }
}

void NativeAudioEngine::processingLoop() {
    std::vector<float> raw(processingBlockSamples_);
    std::vector<float> processed(processingBlockSamples_);

    while (workerRunning_.load(std::memory_order_acquire)) {
        if (inputRing_.availableToRead() < processingBlockSamples_) {
            std::this_thread::sleep_for(std::chrono::microseconds(500));
            continue;
        }

        const auto startNanos = monotonicNanos();
        const auto read = inputRing_.read(raw.data(), processingBlockSamples_);
        if (read != processingBlockSamples_) continue;

        std::copy(raw.begin(), raw.end(), processed.begin());

        const auto mode = static_cast<ProcessingMode>(
            processingMode_.load(std::memory_order_acquire)
        );

        if (mode == ProcessingMode::Dsp) {
            highPassProcessor_.process(
                processed.data(),
                processed.size(),
                inputSampleRate_.load(std::memory_order_acquire)
            );
        }

        const auto rawMetrics = calculateMetrics(raw.data(), raw.size());
        const auto processedMetrics =
            calculateMetrics(processed.data(), processed.size());

        SignalSnapshot next;
        next.rawRms = rawMetrics.rms;
        next.rawPeak = rawMetrics.peak;
        next.rawDcOffset = rawMetrics.dcOffset;
        next.rawClippingRatio = rawMetrics.clippingRatio;
        next.processedRms = processedMetrics.rms;
        next.processedPeak = processedMetrics.peak;
        next.processedDcOffset = processedMetrics.dcOffset;
        next.processedClippingRatio = processedMetrics.clippingRatio;

        const std::size_t bucketSize =
            std::max<std::size_t>(1, processed.size() / kWaveformPoints);

        for (std::size_t point = 0; point < kWaveformPoints; ++point) {
            const std::size_t begin = point * bucketSize;
            if (begin >= processed.size()) break;
            const std::size_t end =
                std::min(processed.size(), begin + bucketSize);

            float peak = 0.0f;
            for (std::size_t i = begin; i < end; ++i) {
                peak = std::max(peak, std::fabs(processed[i]));
            }
            next.waveform[point] = peak;
        }

        {
            std::lock_guard<std::mutex> signalLock(signalMutex_);
            signalSnapshot_ = next;
        }

        if (monitorEnabled_.load(std::memory_order_acquire)) {
            const auto written = outputRing_.write(
                processed.data(),
                processed.size()
            );
            if (written < processed.size()) {
                outputOverrunSamples_.fetch_add(
                    static_cast<std::int64_t>(processed.size() - written),
                    std::memory_order_relaxed
                );
            }
            updateHighWatermark(
                outputRingHighWatermark_,
                outputRing_.availableToRead()
            );
        }

        processedFrames_.fetch_add(
            static_cast<std::int64_t>(processed.size()),
            std::memory_order_relaxed
        );

        const auto elapsed = monotonicNanos() - startNanos;
        lastProcessorNanos_.store(elapsed, std::memory_order_release);
        updateMax(maxProcessorNanos_, elapsed);
    }
}

oboe::DataCallbackResult NativeAudioEngine::onInputAudioReady(
    oboe::AudioStream*,
    float* samples,
    int32_t numFrames
) noexcept {
    const auto startNanos = monotonicNanos();

    if (samples == nullptr || numFrames <= 0) {
        return oboe::DataCallbackResult::Continue;
    }

    const auto sampleCount = static_cast<std::size_t>(numFrames);
    const auto written = inputRing_.write(samples, sampleCount);

    if (written < sampleCount) {
        droppedInputSamples_.fetch_add(
            static_cast<std::int64_t>(sampleCount - written),
            std::memory_order_relaxed
        );
    }

    inputFrames_.fetch_add(numFrames, std::memory_order_relaxed);
    inputCallbacks_.fetch_add(1, std::memory_order_relaxed);
    updateHighWatermark(
        inputRingHighWatermark_,
        inputRing_.availableToRead()
    );

    updateMax(
        maxInputCallbackNanos_,
        monotonicNanos() - startNanos
    );
    return oboe::DataCallbackResult::Continue;
}

oboe::DataCallbackResult NativeAudioEngine::onOutputAudioReady(
    oboe::AudioStream*,
    float* samples,
    int32_t numFrames
) noexcept {
    const auto startNanos = monotonicNanos();

    if (samples == nullptr || numFrames <= 0) {
        return oboe::DataCallbackResult::Continue;
    }

    const auto sampleCount = static_cast<std::size_t>(numFrames);

    if (!monitorEnabled_.load(std::memory_order_acquire)) {
        std::fill(samples, samples + sampleCount, 0.0f);
    } else {
        const auto read = outputRing_.read(samples, sampleCount);
        if (read < sampleCount) {
            std::fill(samples + read, samples + sampleCount, 0.0f);
            outputUnderrunSamples_.fetch_add(
                static_cast<std::int64_t>(sampleCount - read),
                std::memory_order_relaxed
            );
        }
    }

    outputFrames_.fetch_add(numFrames, std::memory_order_relaxed);
    outputCallbacks_.fetch_add(1, std::memory_order_relaxed);

    updateMax(
        maxOutputCallbackNanos_,
        monotonicNanos() - startNanos
    );
    return oboe::DataCallbackResult::Continue;
}

void NativeAudioEngine::onStreamError(
    oboe::AudioStream* stream,
    oboe::Result error
) noexcept {
    disconnectCount_.fetch_add(1, std::memory_order_relaxed);
    lastErrorCode_.store(static_cast<int>(error), std::memory_order_release);

    {
        std::lock_guard<std::mutex> lock(errorMutex_);
        lastErrorText_ = std::string("Audio stream disconnected: ") +
            oboe::convertToText(error);
    }

    if (stream == outputStream_.get()) {
        monitorEnabled_.store(false, std::memory_order_release);
        outputAvailable_.store(false, std::memory_order_release);
        return;
    }

    workerRunning_.store(false, std::memory_order_release);
    state_.store(EngineState::Error, std::memory_order_release);
}

void NativeAudioEngine::setLastError(
    int code,
    const std::string& message
) {
    lastErrorCode_.store(code, std::memory_order_release);
    std::lock_guard<std::mutex> lock(errorMutex_);
    lastErrorText_ = message;
}

std::int64_t NativeAudioEngine::monotonicNanos() noexcept {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
        std::chrono::steady_clock::now().time_since_epoch()
    ).count();
}

void NativeAudioEngine::updateMax(
    std::atomic<std::int64_t>& target,
    std::int64_t value
) noexcept {
    auto current = target.load(std::memory_order_relaxed);
    while (value > current &&
           !target.compare_exchange_weak(
               current,
               value,
               std::memory_order_relaxed
           )) {
    }
}

void NativeAudioEngine::updateHighWatermark(
    std::atomic<std::int64_t>& target,
    std::size_t value
) noexcept {
    updateMax(target, static_cast<std::int64_t>(value));
}

oboe::DataCallbackResult NativeAudioEngine::InputCallback::onAudioReady(
    oboe::AudioStream* stream,
    void* audioData,
    int32_t numFrames
) {
    return owner_.onInputAudioReady(
        stream,
        static_cast<float*>(audioData),
        numFrames
    );
}

oboe::DataCallbackResult NativeAudioEngine::OutputCallback::onAudioReady(
    oboe::AudioStream* stream,
    void* audioData,
    int32_t numFrames
) {
    return owner_.onOutputAudioReady(
        stream,
        static_cast<float*>(audioData),
        numFrames
    );
}

void NativeAudioEngine::ErrorCallback::onErrorAfterClose(
    oboe::AudioStream* stream,
    oboe::Result error
) {
    owner_.onStreamError(stream, error);
}

}  // namespace budsia::audio
