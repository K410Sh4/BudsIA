# BudsIA V3 — Adaptive Audio Focus

BudsIA V3 is being rebuilt as a local-first adaptive audio filtering platform for Android.

The current branch is the audited foundation. It intentionally does **not** pretend that a
neural model exists before one is integrated and verified.

## What works in this foundation

- real microphone capture through Android AudioRecord
- 48 kHz mono / 20 ms frame baseline
- deterministic high-pass DSP
- real signal metrics
- real per-stage processing timing
- live waveform
- explicit microphone-active indicator
- no raw-audio persistence
- Hilt-based replaceable engines
- unit tests
- debug/release CI

## Architecture

See:
- `docs/ARCHITECTURE.md`
- `docs/AUDIO_PIPELINE.md`
- `docs/ROADMAP.md`

## Current enhancement mode

`DSP_ONLY`.

The neural enhancement contract already exists as `AudioEnhancementEngine`, but the
production binding is an explicit bypass engine until a verified on-device model is added.

No simulated AI confidence or invented latency is shown.
