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
- explicit opt-in candidate adaptive mix between original and neural output
- atomic live profile updates without restarting the audio stream
- no silent model-weight training
- measured Android thermal, battery and memory protection
- process CPU load explicitly labeled ESTIMATED
- mandatory AI -> DSP fallback on severe thermal / Android low-memory
- optional user-configured low-battery AI fallback, OFF by default
- current + 10-second thermal headroom estimates
- Android 16 CPU headroom estimate when available
- Android Performance Hint session on the stable neural inference thread
- measured inference duration reported to ADPF every neural cycle
- Android 15+ power-efficiency scheduling hint when the governor allows it

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

This is intentionally a **preference-learning layer**, not hidden model retraining.

A deterministic candidate mapping is now available behind an explicit switch that is OFF by
default:

```
output = original * (1 - strength) + neural * strength
```

The mapping is bounded, reversible and never changes neural weights. It remains experimental
until physical A/B validation passes.

## AI performance protection

BudsIA observes Android thermal state, predictive thermal headroom, battery, charging state,
power-save mode and memory. On Android 16 it also consumes the platform CPU-headroom estimate.

Severe thermal conditions, near-severe 10-second thermal forecast and Android low-memory
signals force AI -> DSP to preserve stability.

The neural worker runs on one long-lived thread. On supported Android versions a
Performance Hint session receives the measured duration of every inference cycle; Android 15+
may also receive a power-efficiency scheduling preference when the governor determines it is
appropriate.

A low-battery fallback is available as a user setting and is OFF by default. When enabled,
its threshold is configurable from 5% to 30%.

MAX_QUALITY / BALANCED / ECO remain recommendations; BudsIA never claims that a different
model is active unless route and verified model are actually compatible.

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
- `docs/ADAPTIVE_PROFILES.md`
- `docs/ADAPTIVE_CONTROL_CANDIDATE.md`
- `docs/AI_PERFORMANCE.md`
- `docs/adr/0006-ai-performance-governor.md`
- `docs/adr/0007-adpf-performance-hints.md`
- `docs/adr/0005-candidate-adaptive-control.md`
