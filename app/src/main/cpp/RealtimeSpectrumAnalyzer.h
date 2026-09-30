#pragma once

#include <algorithm>
#include <array>
#include <cmath>
#include <complex>
#include <cstddef>

namespace budsia::audio {

/**
 * Fixed-size spectrum analyzer designed for the non-callback native worker.
 *
 * - 1024-sample Hann window
 * - radix-2 FFT
 * - 64 logarithmically-spaced display bands
 * - output normalized from -80 dBFS..0 dBFS to 0..1
 *
 * No heap allocation occurs during push()/analyze().
 */
class RealtimeSpectrumAnalyzer {
public:
    static constexpr std::size_t kFftSize = 1024;
    static constexpr std::size_t kBandCount = 64;

    void reset() noexcept {
        samples_.fill(0.0f);
        spectrum_.fill(0.0f);
        writeIndex_ = 0;
        filled_ = 0;
        samplesSinceAnalysis_ = 0;
        sampleRateHz_ = 0;
    }

    bool push(
        const float* samples,
        std::size_t count,
        int sampleRateHz
    ) noexcept {
        if (
            samples == nullptr ||
            count == 0 ||
            sampleRateHz <= 0
        ) {
            return false;
        }

        if (sampleRateHz_ != sampleRateHz) {
            reset();
            sampleRateHz_ = sampleRateHz;
        }

        for (std::size_t i = 0; i < count; ++i) {
            samples_[writeIndex_] = samples[i];
            writeIndex_ = (writeIndex_ + 1) % kFftSize;
            filled_ = std::min(kFftSize, filled_ + 1);
        }

        samplesSinceAnalysis_ += count;

        // Roughly 20 analyses/second at 48 kHz, lower CPU than per-block FFT.
        const std::size_t hop = static_cast<std::size_t>(
            std::max(1, sampleRateHz / 20)
        );

        if (
            filled_ < kFftSize ||
            samplesSinceAnalysis_ < hop
        ) {
            return false;
        }

        samplesSinceAnalysis_ = 0;
        analyze();
        return true;
    }

    [[nodiscard]] const std::array<float, kBandCount>&
    spectrum() const noexcept {
        return spectrum_;
    }

private:
    static constexpr float kPi =
        3.14159265358979323846f;
    static constexpr float kMinDb = -80.0f;
    static constexpr float kMinDisplayHz = 40.0f;

    void analyze() noexcept {
        std::array<std::complex<float>, kFftSize> fft{};

        // writeIndex_ points at the oldest sample after the ring is full.
        for (std::size_t i = 0; i < kFftSize; ++i) {
            const std::size_t source =
                (writeIndex_ + i) % kFftSize;
            const float window =
                0.5f -
                0.5f * std::cos(
                    2.0f * kPi *
                    static_cast<float>(i) /
                    static_cast<float>(kFftSize - 1)
                );
            fft[i] = std::complex<float>(
                samples_[source] * window,
                0.0f
            );
        }

        fftInPlace(fft);

        const float nyquist =
            static_cast<float>(sampleRateHz_) * 0.5f;
        const float maxDisplayHz =
            std::max(kMinDisplayHz * 2.0f, nyquist);

        for (
            std::size_t band = 0;
            band < kBandCount;
            ++band
        ) {
            const float lowT =
                static_cast<float>(band) /
                static_cast<float>(kBandCount);
            const float highT =
                static_cast<float>(band + 1) /
                static_cast<float>(kBandCount);

            const float lowHz =
                kMinDisplayHz *
                std::pow(
                    maxDisplayHz / kMinDisplayHz,
                    lowT
                );
            const float highHz =
                kMinDisplayHz *
                std::pow(
                    maxDisplayHz / kMinDisplayHz,
                    highT
                );

            const std::size_t lowBin =
                frequencyToBin(lowHz);
            const std::size_t highBin =
                std::max(
                    lowBin + 1,
                    frequencyToBin(highHz)
                );

            float peakMagnitude = 0.0f;
            const std::size_t boundedHigh =
                std::min(
                    highBin,
                    kFftSize / 2
                );

            for (
                std::size_t bin = lowBin;
                bin <= boundedHigh;
                ++bin
            ) {
                peakMagnitude = std::max(
                    peakMagnitude,
                    std::abs(fft[bin])
                );
            }

            // Hann coherent gain is 0.5. Normalize a full-scale sinusoid
            // approximately to 0 dBFS before display compression.
            const float normalized =
                peakMagnitude /
                (static_cast<float>(kFftSize) * 0.25f);

            const float db = 20.0f * std::log10(
                std::max(normalized, 0.0001f)
            );

            spectrum_[band] = std::clamp(
                (db - kMinDb) / -kMinDb,
                0.0f,
                1.0f
            );
        }
    }

    std::size_t frequencyToBin(
        float frequencyHz
    ) const noexcept {
        const float raw =
            frequencyHz *
            static_cast<float>(kFftSize) /
            static_cast<float>(sampleRateHz_);

        return static_cast<std::size_t>(
            std::clamp(
                raw,
                1.0f,
                static_cast<float>(kFftSize / 2)
            )
        );
    }

    static void fftInPlace(
        std::array<
            std::complex<float>,
            kFftSize
        >& values
    ) noexcept {
        // Bit-reversal permutation.
        for (
            std::size_t i = 1, j = 0;
            i < kFftSize;
            ++i
        ) {
            std::size_t bit = kFftSize >> 1;
            for (; j & bit; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;

            if (i < j) {
                std::swap(values[i], values[j]);
            }
        }

        for (
            std::size_t length = 2;
            length <= kFftSize;
            length <<= 1
        ) {
            const float angle =
                -2.0f * kPi /
                static_cast<float>(length);
            const std::complex<float> root(
                std::cos(angle),
                std::sin(angle)
            );

            for (
                std::size_t start = 0;
                start < kFftSize;
                start += length
            ) {
                std::complex<float> weight(
                    1.0f,
                    0.0f
                );
                const std::size_t half =
                    length >> 1;

                for (
                    std::size_t offset = 0;
                    offset < half;
                    ++offset
                ) {
                    const auto even =
                        values[start + offset];
                    const auto odd =
                        values[
                            start + offset + half
                        ] * weight;

                    values[start + offset] =
                        even + odd;
                    values[
                        start + offset + half
                    ] = even - odd;

                    weight *= root;
                }
            }
        }
    }

    std::array<float, kFftSize> samples_{};
    std::array<float, kBandCount> spectrum_{};
    std::size_t writeIndex_ = 0;
    std::size_t filled_ = 0;
    std::size_t samplesSinceAnalysis_ = 0;
    int sampleRateHz_ = 0;
};

}  // namespace budsia::audio
