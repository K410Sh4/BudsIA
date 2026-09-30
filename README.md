# BudsIA V3 — Adaptive Audio Focus

BudsIA V3 is a local-first adaptive audio filtering platform for Android.

The system is built as replaceable, observable stages so capture, routing, DSP, neural
enhancement and later personalization can be improved independently.

## Current production live path

```
Microphone
  -> Oboe / AAudio
  -> lock-free native input ring
  -> deterministic realtime worker
  -> optional AI input ring
  -> streaming neural denoiser
  -> lock-free output ring
  -> private audio output
```

Kotlin/Compose controls lifecycle, models, UI and low-frequency telemetry. Oboe callbacks do
not run neural inference and do not call Kotlin.

## Implemented

- Android 36 / Kotlin / Compose / Hilt
- C++20 native audio core
- Oboe 1.10.0
- actual sample-rate/device discovery
- exclusive-low-latency request with shared fallback
- unprocessed-input request with voice-recognition fallback
- lock-free SPSC audio rings
- dedicated native DSP worker
- RAW / DSP / AI modes
- explicit private-route live monitor guard
- xruns, drops, underruns, overruns and queue telemetry
- measured callback/DSP/inference timing
- verified on-demand neural model installation
- DPDFNet2 48 kHz HR factory candidate
- sherpa-onnx 1.13.8 streaming denoiser runtime
- measured moving realtime factor with automatic AI -> DSP fallback
- no raw-audio persistence
- foreground-only live capture
- unit/native tests
- debug/release CI

## What is not claimed yet

CI proves build/test/runtime packaging, not acoustic quality on a physical device.
End-to-end latency, Bluetooth behavior, thermal stability and perceived enhancement remain
device-validation items.

## Documentation

- `docs/ARCHITECTURE.md`
- `docs/AUDIO_PIPELINE.md`
- `docs/NEURAL_PIPELINE.md`
- `docs/DEVICE_VALIDATION.md`
- `docs/ROADMAP.md`
- `docs/TECHNOLOGY_BASELINE_2026.md`
- `docs/adr/0001-native-realtime-core.md`
- `docs/adr/0002-neural-runtime.md`
