# BudsIA V3 — Adaptive Audio Focus

BudsIA V3 is a local-first adaptive audio filtering platform for Android.

The project is built as replaceable, observable stages so capture, Bluetooth routing, DSP,
neural inference, adaptation and persistence can be changed independently.

## Current live path

```
Selected Android input
  -> route preparation (when needed)
  -> Oboe / AAudio
  -> lock-free native input ring
  -> native realtime worker
  -> DSP or dedicated AI transport
  -> sherpa-onnx streaming denoiser
  -> bounded adaptive profile mix
  -> native output ring
  -> private output when monitoring is enabled
```

Neural inference never executes inside an Oboe callback.

## Implemented

- Android 36 / Kotlin / Jetpack Compose / Hilt
- C++20 native audio core
- Oboe 1.10.0
- actual device/sample-rate discovery
- exclusive low-latency request with shared fallback
- unprocessed input with voice-communication/recognition fallback where appropriate
- lock-free SPSC realtime and AI transport rings
- RAW / DSP / IA modes
- selectable input/output routes while stopped
- Android 12+ communication-device routing for Bluetooth microphone use
- BLUETOOTH_CONNECT only where paired Bluetooth route control requires it
- actual opened route shown as source of truth
- verified sherpa-onnx 1.13.8 runtime
- DPDFNet2 48 kHz HR MAX_QUALITY model
- explicit model download with exact size + SHA-256 verification
- measured inference current/average/max and realtime factor
- automatic AI -> DSP fallback when sustained realtime processing cannot keep up
- environment profiles: Geral, Casa, Rua, Carro, Trabalho
- local Teach AI feedback
- bounded diminishing-step personalization
- Room 2.8.5 relational persistence
- atomic profile update + feedback audit event with model identity
- no raw-audio persistence for learning
- xrun/drop/underrun/clipping diagnostics
- release R8/JNI integrity checks
- unit tests + native SPSC concurrency test
- debug/release CI

## Safe self-learning policy

The factory model is immutable.

```
factory model
   ↓
neural output ─────┐
                   ├─> bounded adaptive mixer -> output
original frame ────┘
          ↑
environment profile
          ↑
explicit local feedback
          ↓
atomic Room audit event
```

Current learning adjusts a bounded per-environment mix. It does not silently rewrite neural
weights. Future local training must create a separate candidate and pass regression tests
before promotion.

## Bluetooth routing policy

A connected headset is never assumed to be the active microphone. The user selects an input
and output route while audio is stopped. When a Bluetooth microphone requires communication
routing, Android owns that communication route and BudsIA reports the route actually opened
by the native stream.

Galaxy Buds microphone bandwidth, routing stability and end-to-end latency remain physical
device validation items.

## Validation boundary

CI verifies build integrity, native concurrency, runtime/model hashes, Room schema, release
minification contracts and APK packaging.

Acoustic quality, battery drain, thermal behavior and Bluetooth round-trip latency require
measurements on the target device and are not claimed by CI.

## Documentation

- `docs/ARCHITECTURE.md`
- `docs/AUDIO_PIPELINE.md`
- `docs/ROADMAP.md`
- `docs/TECHNOLOGY_BASELINE_2026.md`
- `docs/adr/0001-native-realtime-core.md`
- `docs/adr/0002-neural-runtime.md`
- `docs/adr/0003-explicit-bluetooth-routing.md`
- `docs/adr/0004-adaptive-self-learning.md`
