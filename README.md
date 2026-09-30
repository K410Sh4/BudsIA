# BudsIA V3 — Adaptive Audio Focus

BudsIA V3 is a local-first adaptive audio filtering platform for Android.

The project is intentionally built as replaceable, observable stages so capture, routing,
DSP, neural inference and adaptation can be improved independently.

## Current production live path

```
Microphone
  -> Oboe / AAudio
  -> lock-free input ring
  -> native worker
  -> deterministic DSP
  -> optional private-route monitor
```

Kotlin/Compose controls the engine and reads low-frequency telemetry. PCM frames do not cross
JNI one-by-one.

## Implemented

- Android 36 / Kotlin / Compose / Hilt
- C++20 native audio core
- Oboe 1.10.0
- device-native sample rate discovery
- exclusive-low-latency request with shared fallback
- unprocessed-input request with voice-recognition fallback
- SPSC lock-free input/output rings
- dedicated DSP worker thread
- RAW vs DSP A/B switch
- optional live monitor restricted to private outputs
- actual route/device IDs surfaced to Kotlin
- xruns when available
- input drops / output underruns / output overruns
- measured native callback and processing timings
- explicit microphone-active indicator
- automatic stop when the live screen leaves foreground
- no raw-audio persistence
- Kotlin unit tests + host C++ ring-buffer test
- debug/release CI

## Neural enhancement

A neural engine is **not** faked in the current build.

The next gated phase introduces causal streaming enhancement through ONNX Runtime Mobile.
Execution-provider choice will be benchmark-driven: CPU/XNNPACK baselines first, then NNAPI
only when it measurably improves the current model/device combination.

## Documentation

- `docs/ARCHITECTURE.md`
- `docs/AUDIO_PIPELINE.md`
- `docs/ROADMAP.md`
- `docs/TECHNOLOGY_BASELINE_2026.md`
- `docs/adr/0001-native-realtime-core.md`
