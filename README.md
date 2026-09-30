# BudsIA V3 — Adaptive Audio Focus

BudsIA V3 is a local-first adaptive audio filtering platform for Android.

The project is built as replaceable, observable stages so capture, routing, DSP, neural
inference, adaptation and persistence can be improved independently.

## Current live path

```
Microphone
  -> Oboe / AAudio
  -> lock-free input ring
  -> native realtime worker
  -> AI transport
  -> sherpa-onnx streaming denoiser
  -> bounded adaptive profile mix
  -> native output ring
  -> private audio output when monitoring is enabled
```

Oboe callbacks remain free of model inference and managed-runtime calls.

## Implemented

- Android 36 / Kotlin / Jetpack Compose / Hilt
- C++20 native audio core
- Oboe 1.10.0
- device-native sample-rate discovery
- exclusive low-latency request with shared fallback
- unprocessed-input request with voice-recognition fallback
- SPSC lock-free input/output/AI transport rings
- RAW / DSP / IA modes
- verified sherpa-onnx 1.13.8 runtime
- DPDFNet2 48 kHz HR MAX_QUALITY model
- explicit model download with exact size + SHA-256 validation
- measured neural inference latency and realtime factor
- automatic AI -> DSP fallback when sustained realtime performance fails
- environment-specific adaptive profiles
- local Teach AI feedback
- bounded diminishing-step profile learning
- relational Room feedback audit trail with model identity
- no raw-audio persistence
- private-route monitoring guard
- real route, xrun, drop, underrun and clipping diagnostics
- unit tests + native SPSC concurrency test
- debug and release CI

## Self-learning policy

BudsIA does **not** let the factory neural model rewrite its own weights continuously.

Current learning path:

```
immutable factory model
        ↓
local environment profile
        ↓
bounded neural/original mix
        ↓
explicit user feedback
        ↓
audited Room event
```

This gives the app real local personalization while preserving rollback and preventing
uncontrolled model drift.

Future local model training must create a separate candidate model and pass offline
regression tests before promotion.

## Current validation boundary

CI verifies build integrity, native tests, model/runtime hashes and release compilation.

Physical-device acoustic quality, thermal behavior, battery drain and Bluetooth round-trip
latency still require measurements on the target Android device. They are not claimed here.

## Documentation

- `docs/ARCHITECTURE.md`
- `docs/AUDIO_PIPELINE.md`
- `docs/ROADMAP.md`
- `docs/TECHNOLOGY_BASELINE_2026.md`
- `docs/adr/0001-native-realtime-core.md`
- `docs/adr/0002-neural-runtime.md`
- `docs/adr/0003-adaptive-self-learning.md`
