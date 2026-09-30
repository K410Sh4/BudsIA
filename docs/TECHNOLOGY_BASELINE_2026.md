# BudsIA V3 — 2026 technology baseline

This file records technology choices separately from product claims.

## Realtime audio

Production path:

- Oboe 1.10.0;
- AAudio on supported Android versions through Oboe;
- C++20;
- lock-free SPSC rings;
- worker-thread DSP/inference;
- Kotlin/JNI only for control and low-frequency telemetry.

The Oboe callback never performs neural inference or blocking managed-runtime work.

## Neural runtime

Current runtime:

- sherpa-onnx 1.13.8;
- ONNX Runtime supplied by the verified sherpa Android runtime;
- CPU provider baseline;
- streaming online speech denoiser;
- exact sample-rate model selection.

Pinned production candidates:

- DPDFNet2 48 kHz HR for exact 48 kHz routes;
- GTCRN Simple for exact 16 kHz routes.

The app does not run a model at the wrong sample rate and does not hide resampling behind a
quality label.

## 2026 acceleration policy

NNAPI was deprecated in Android 15 / API 35. BudsIA therefore does not treat NNAPI as the
default future acceleration path.

Current policy:

1. CPU is the required correctness baseline.
2. Any XNNPACK or vendor accelerator experiment must be benchmarked against the exact model.
3. Qualcomm QNN may be evaluated only on supported devices through a separately packaged,
   auditable runtime.
4. A hardware accelerator is promoted only when measured latency, energy and output quality
   improve without increasing stream instability.
5. Unsupported acceleration always falls back to the verified CPU path.

No accelerator is selected only because a phone advertises a GPU or NPU.

References:

- https://developer.android.com/ndk/guides/neuralnetworks
- https://onnxruntime.ai/docs/execution-providers/
- https://onnxruntime.ai/docs/build/android.html

## Local adaptation storage

Adaptive preferences use AndroidX DataStore Preferences 1.2.1, the stable 2026 line.

The stored profile contains only bounded preference metadata. Raw PCM and neural activations
are not stored.

## Model policy

Factory neural assets are immutable after integrity verification.

Personal adaptation is kept separately:

Factory -> Adaptive Profile -> Candidate Mapping -> Physical A/B Evaluation -> Promote/Rollback

A profile does not rewrite model weights.

## Measurements

BudsIA distinguishes:

- MEASURED: produced from a device/runtime counter or monotonic timer;
- ESTIMATED: algorithmic estimate;
- UNKNOWN: cannot be measured with the current route/API.

Bluetooth codec latency must never be presented as measured unless it has actually been
measured.

## Promotion rule

No runtime, model, route policy or adaptive mapping is promoted solely because it is newer.

Promotion requires repeatable measurements on the target device and a documented rollback
path.
