# BudsIA V3 — Adaptive Audio Focus

BudsIA V3 is a local-first adaptive audio filtering platform for Android.

The project is intentionally built as replaceable, observable stages so capture, routing,
deterministic DSP, neural enhancement and adaptation can evolve independently.

## Current production live path

```
Microphone / selected headset input
  -> Android communication-route preparation when needed
  -> Oboe / AAudio
  -> lock-free native rings
  -> deterministic DSP
  -> sample-rate-aware neural model selection
  -> streaming neural worker
  -> optional private-route monitor
```

The audio callback remains native and bounded. Neural inference never runs inside an Oboe
callback.

## Implemented

- Android 36 / Kotlin / Compose / Hilt
- C++20 native audio core
- Oboe 1.10.0
- device-native sample-rate discovery
- selectable microphone and output routes
- Android 12+ Bluetooth communication routing
- SPSC lock-free input/output rings
- RAW / DSP / AI modes
- actual route/device IDs surfaced after native stream open
- xruns, drops, underruns, callback timing and processor telemetry
- verified model lifecycle with exact byte-size + SHA-256 validation
- sherpa-onnx 1.13.8 streaming denoiser runtime
- DPDFNet2 48 kHz HR for full-band 48 kHz routes
- GTCRN Simple 16 kHz for compatible 16 kHz speech/Bluetooth routes
- automatic neural model choice from the native route's real sample rate
- measured neural inference timing + moving realtime factor
- automatic AI -> DSP fallback
- explicit microphone-active indicator
- automatic stop when the live screen leaves foreground
- no raw-audio persistence
- debug/release CI and native runtime packaging verification
- stable AndroidX DataStore 1.2.1 for local adaptive preferences
- independent versioned profiles for Geral / Casa / Rua / Trabalho / Carro
- bounded explicit feedback learning: Mais filtro / Mais natural / Está bom assim
- no silent model-weight training
- measured thermal / battery / memory monitoring
- estimated normalized BudsIA process CPU telemetry
- automatic AI -> DSP fallback on severe thermal, low-memory or critical-battery conditions
- explicit ECO / BALANCED / MAX_QUALITY recommendations without pretending they already change runtime behavior
- in-app 30-second physical-device validation lab
- PASS / WARN / FAIL / UNKNOWN technical checks without saving microphone audio

## Sample-rate-aware AI

The app does not force every route into one model.

After the native stream opens, BudsIA reads the actual input sample rate and selects the best
verified local model with an exact rate match:

- **48,000 Hz:** DPDFNet2 48 kHz HR / MAX_QUALITY
- **16,000 Hz:** GTCRN Simple / ECO
- other rates: deterministic DSP fallback until a verified compatible model or measured
  resampler path is added

This avoids pretending that a 48 kHz model can directly process a 16 kHz Bluetooth microphone.

## Bluetooth routing

A connected headset is not assumed to be the active microphone. BudsIA lets the user select
an input and output route while audio is stopped. For Bluetooth HFP/SCO or BLE-headset input
on Android 12+, it prepares Android's communication route, then opens Oboe using the selected
input device and reports the device that the stream actually opened.

When a Bluetooth microphone is used, Android owns the paired communication output route rather
than BudsIA trying to force a simultaneous A2DP output.

## Local adaptive profiles

BudsIA now stores explicit listening preferences per acoustic environment in app-private
DataStore. The tuner uses a bounded decaying step, so repeated feedback changes the preferred
strength progressively less over time.

This is intentionally a **preference-learning layer**, not hidden model retraining. The
stored preference does not automatically change realtime audio yet. A future profile-to-audio
mapping must pass physical A/B validation and retain a factory rollback path before activation.

## AI performance governor

BudsIA continuously samples Android thermal, battery and memory state. Severe thermal pressure or Android low-memory state forces a safe AI -> DSP fallback during a
live session. Battery fallback defaults to 15% while not charging, can be adjusted from 5% to
30%, and can be disabled explicitly without disabling thermal or memory protection.

CPU percentage is shown as **ESTIMATED**, derived from process CPU time and elapsed wall time.
Thermal, battery and memory values are reported from Android APIs.

ECO / BALANCED / MAX_QUALITY are currently recommendations only. They are intentionally not
used to change inference threads or providers mid-session until that behavior is measured on
the physical target device.

## Device Validation Lab

While the audio pipeline is already running, BudsIA can collect 30 seconds of technical
telemetry and evaluate realtime continuity, sample-rate stability, drops, underruns, XRuns,
thermal pressure, neural model/rate compatibility and neural realtime factor.

The report is intentionally **not** an acoustic-quality score. It does not save raw audio or
conversation content, and AI validation can start only after the neural runtime is RUNNING.

## Validation status

CI verifies code, model hashes/sizes, native runtime packaging, unit tests, debug APK and
release compilation. Physical Galaxy Buds routing, acoustic quality, thermal behavior and
device-specific latency remain device-validation pending.

## Documentation

- `docs/ARCHITECTURE.md`
- `docs/AUDIO_PIPELINE.md`
- `docs/ROADMAP.md`
- `docs/NEURAL_PIPELINE.md`
- `docs/DEVICE_VALIDATION.md`
- `docs/TECHNOLOGY_BASELINE_2026.md`
- `docs/adr/0001-native-realtime-core.md`
- `docs/adr/0002-neural-runtime.md`
- `docs/adr/0003-explicit-bluetooth-routing.md`
- `docs/adr/0004-local-adaptive-profiles.md`
- `docs/adr/0005-ai-performance-governor.md`
- `docs/adr/0006-device-validation-lab.md`
- `docs/ADAPTIVE_PROFILES.md`
