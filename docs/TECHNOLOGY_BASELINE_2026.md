# BudsIA V3 — 2026 technology baseline

This file records technology choices separately from product claims.

## Realtime audio

Production path:

- Oboe 1.10.0
- AAudio through Oboe on supported Android versions
- C++20
- lock-free SPSC rings
- realtime callbacks limited to bounded memory copies/counters
- worker-thread DSP and neural inference

The neural bridge currently exchanges complete model chunks through JNI outside the Oboe
callback. This is measured and isolated so it can later be replaced by a fully native
inference bridge if device profiling shows JNI transport is material.

## Neural runtime

Current verified runtime:

- sherpa-onnx 1.13.8
- ONNX Runtime native libraries supplied by the pinned sherpa Android release
- streaming OnlineSpeechDenoiser API
- CPU provider baseline
- DPDFNet2 48 kHz HR as the first MAX_QUALITY candidate

The runtime and model are integrity-pinned in CI. The user model is downloaded separately and
verified before activation.

## 2026 accelerator policy

NNAPI is **not** a forward-looking default. Android deprecated NNAPI in Android 15.

BudsIA therefore uses this order:

1. optimized CPU baseline;
2. XNNPACK or runtime-specific CPU acceleration when the selected model/runtime supports it;
3. vendor-specific acceleration only behind an isolated benchmarked provider;
4. Qualcomm QNN only on compatible Snapdragon devices if a future runtime build supports it;
5. no accelerator is selected from device branding alone.

A GPU/NPU path is promoted only if measured latency, energy and output quality are all
acceptable on that exact model/device combination.

References:
- https://developer.android.com/ndk/guides/neuralnetworks
- https://developer.android.com/ndk/guides/neuralnetworks/migration-guide
- https://onnxruntime.ai/docs/tutorials/mobile/
- https://onnxruntime.ai/docs/execution-providers/

## Model policy

Continuous audio models must be causal/streaming or explicitly bounded in algorithmic
latency.

Candidate families include:

- DPDFNet streaming enhancement;
- GTCRN-class compact enhancement for ECO profiles;
- RNNoise-class ultra-light baselines;
- DeepFilterNet-inspired experiments where the device budget permits.

A large Transformer is not the default for a continuous low-latency mobile filter.

## Adaptation policy

Factory model weights are immutable during live listening.

Personalization layers are separated:

```
Factory model
  -> versioned adaptive profile
  -> local feedback dataset (opt-in)
  -> candidate tuning/model
  -> offline evaluation
  -> promote or rollback
```

This prevents uncontrolled model drift.

## Measurements

BudsIA distinguishes:

- MEASURED: produced from runtime/device counters or monotonic timers;
- ESTIMATED: derived from an explicit model/algorithm;
- UNKNOWN: unavailable from the current Android/audio route.

Bluetooth codec latency must never be presented as measured unless it has actually been
measured.
