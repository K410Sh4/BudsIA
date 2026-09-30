# BudsIA V3 — Adaptive Audio Focus

BudsIA V3 is a local-first adaptive audio filtering platform for Android.

The project is intentionally built as replaceable, observable stages so capture, routing,
deterministic DSP, neural enhancement and future adaptation can evolve independently.

## Current production live path

```
Microphone
  -> Oboe / AAudio
  -> lock-free native rings
  -> deterministic native worker
  -> DSP or verified streaming neural worker
  -> optional private-route monitor
```

The audio callback remains native and bounded. Neural inference never runs inside an Oboe
callback.

## Implemented

- Android 36 / Kotlin / Compose / Hilt
- C++20 native audio core
- Oboe 1.10.0
- device-native sample-rate discovery
- exclusive-low-latency request with shared fallback
- unprocessed-input request with voice-recognition fallback
- SPSC lock-free input/output rings
- dedicated native processing worker
- RAW / DSP / AI modes
- optional live monitor restricted to private outputs
- actual route/device IDs surfaced to Kotlin
- xruns when available
- input drops / AI-input drops / output underruns / output overruns
- measured native callback, DSP and neural inference timings
- realtime-factor watchdog with automatic AI -> DSP fallback
- explicit microphone-active indicator
- automatic stop when the live screen leaves foreground
- no raw-audio persistence
- verified model lifecycle with exact byte-size + SHA-256 validation
- sherpa-onnx 1.13.8 streaming denoiser runtime
- DPDFNet2 48 kHz HR MAX_QUALITY model option
- Kotlin unit tests + host C++ concurrent SPSC test
- debug/release CI
- CI verification that required neural native libraries are packaged

## Neural enhancement

The current AI path is real, local and explicitly gated.

The app starts the audio engine in deterministic DSP while the neural model is prepared and
verified. Only after the model runtime reports a valid frame size and matching sample rate
does the coordinator switch the live engine to AI mode. This prevents stale microphone
backlog from accumulating while the model loads.

Current neural baseline:

- runtime: sherpa-onnx 1.13.8
- model: DPDFNet2 48 kHz HR
- provider: CPU baseline
- model integrity: pinned size + SHA-256
- automatic fallback: AI -> DSP
- realtime watchdog: moving RTF > 1.10 after warmup triggers fallback

The app does **not** claim device-specific latency or quality until physical-device validation
has been completed.

## Next phase

Adaptive profiles will add versioned local preferences and environment-specific behavior
without modifying the immutable factory model.

## Documentation

- `docs/ARCHITECTURE.md`
- `docs/AUDIO_PIPELINE.md`
- `docs/ROADMAP.md`
- `docs/TECHNOLOGY_BASELINE_2026.md`
- `docs/adr/0001-native-realtime-core.md`
- `docs/adr/0002-neural-runtime.md`
