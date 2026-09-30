# Audio Pipeline

## Capture baseline

- 48 kHz
- mono
- PCM 16-bit from Android AudioRecord
- 20 ms frames
- in-memory conversion to normalized Float PCM

These values are a baseline, not a claim that every hardware route exposes the same
bandwidth or quality.

## DSP foundation

A stateful first-order 70 Hz high-pass filter removes DC and very-low-frequency drift.
It is intentionally simple, deterministic and separately testable.

## Enhancement

`AudioEnhancementEngine` is the boundary for the neural system.
The foundation uses `BypassAudioEnhancementEngine`, which returns the DSP frame unchanged
and reports `applied=false`.

No fake AI scores are generated.

## Metrics

Current measurements:

- RMS
- peak
- DC offset
- clipping ratio
- per-stage processing duration using a monotonic clock

Future additions:

- FFT/STFT timing
- underruns/overruns
- route latency where measurable
- model inference latency
- estimated SNR with an explicit ESTIMATED label
