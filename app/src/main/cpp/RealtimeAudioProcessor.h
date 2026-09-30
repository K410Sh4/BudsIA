#pragma once

#include <cmath>
#include <cstddef>

namespace budsia::audio {

enum class ProcessingMode : int {
    Raw = 0,
    Dsp = 1,
    Ai = 2,
};

class HighPassProcessor {
public:
    explicit HighPassProcessor(float cutoffHz = 70.0f)
        : cutoffHz_(cutoffHz) {}

    void reset() noexcept {
        previousInput_ = 0.0f;
        previousOutput_ = 0.0f;
        sampleRateHz_ = 0;
    }

    void process(float* samples, std::size_t count, int sampleRateHz) noexcept {
        if (samples == nullptr || count == 0 || sampleRateHz <= 0) return;

        if (sampleRateHz_ != sampleRateHz) {
            reset();
            sampleRateHz_ = sampleRateHz;
        }

        constexpr double kPi = 3.14159265358979323846;
        const double dt = 1.0 / static_cast<double>(sampleRateHz);
        const double rc = 1.0 / (2.0 * kPi * static_cast<double>(cutoffHz_));
        const float alpha = static_cast<float>(rc / (rc + dt));

        for (std::size_t i = 0; i < count; ++i) {
            const float input = samples[i];
            const float output =
                alpha * (previousOutput_ + input - previousInput_);

            previousInput_ = input;
            previousOutput_ = output;
            samples[i] = std::fmax(-1.0f, std::fmin(1.0f, output));
        }
    }

private:
    float cutoffHz_;
    float previousInput_ = 0.0f;
    float previousOutput_ = 0.0f;
    int sampleRateHz_ = 0;
};

}  // namespace budsia::audio
