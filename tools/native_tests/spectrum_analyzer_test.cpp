#include <cassert>
#include <cmath>
#include <cstddef>
#include <iostream>
#include <vector>

#include "../../app/src/main/cpp/RealtimeSpectrumAnalyzer.h"

using budsia::audio::RealtimeSpectrumAnalyzer;

int main() {
    constexpr int kSampleRate = 48'000;
    constexpr float kFrequency = 1'000.0f;
    constexpr float kPi = 3.14159265358979323846f;

    RealtimeSpectrumAnalyzer analyzer;
    std::vector<float> samples(4'800);

    for (std::size_t i = 0; i < samples.size(); ++i) {
        samples[i] =
            0.8f * std::sin(
                2.0f * kPi *
                kFrequency *
                static_cast<float>(i) /
                static_cast<float>(kSampleRate)
            );
    }

    bool updated = false;
    for (
        std::size_t offset = 0;
        offset < samples.size();
        offset += 480
    ) {
        updated = analyzer.push(
            samples.data() + offset,
            std::min<std::size_t>(
                480,
                samples.size() - offset
            ),
            kSampleRate
        ) || updated;
    }

    assert(updated);

    const auto& spectrum = analyzer.spectrum();
    float maxValue = 0.0f;
    std::size_t maxBand = 0;

    for (std::size_t i = 0; i < spectrum.size(); ++i) {
        assert(spectrum[i] >= 0.0f);
        assert(spectrum[i] <= 1.0f);

        if (spectrum[i] > maxValue) {
            maxValue = spectrum[i];
            maxBand = i;
        }
    }

    // A strong 1 kHz tone must create a clearly visible peak away from
    // the extreme low/high display edges.
    assert(maxValue > 0.70f);
    assert(maxBand > 10);
    assert(maxBand < 55);

    std::cout
        << "spectrum_analyzer_test: PASS band="
        << maxBand
        << " level="
        << maxValue
        << "\n";
    return 0;
}
