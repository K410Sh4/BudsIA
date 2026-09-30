# ADR-0002 — Streaming neural enhancement runtime

Status: Accepted for implementation  
Date: 2026-09-30

## Decision

BudsIA uses sherpa-onnx 1.13.8 with its streaming speech-denoiser API.

The model is selected from the **actual input sample rate reported after Oboe opens the
route**, not from a Bluetooth/device-name guess.

Current pinned models:

### DPDFNet2 48 kHz HR

- role: full-band / MAX_QUALITY
- family: DPDFNet
- sample rate: 48,000 Hz
- asset: `dpdfnet2_48khz_hr.onnx`
- expected size: 10,596,848 bytes
- SHA-256: `0b399f8a58dc4d70d8cd97541f5c39869406145193b957d00a03b66070944928`

### GTCRN Simple 16 kHz

- role: low-bandwidth speech / ECO
- family: GTCRN
- sample rate: 16,000 Hz
- asset: `gtcrn_simple.onnx`
- expected size: 535,638 bytes
- SHA-256: `e77603ac0c23dac3227dd2d7135b3a585cbee2679048aecfa886657d3ae1b534`

Both models are downloaded explicitly and verified before activation. They are not bundled in
the APK.

## Model-selection rule

The runtime requires an exact sample-rate match.

```
native input rate
  -> find verified compatible model
  -> prepare runtime
  -> validate model-reported sample rate
  -> switch DSP -> AI
```

If no verified model matches the route, BudsIA stays on DSP. It does not silently resample or
run a model at the wrong rate.

## Why

Bluetooth headset microphones can expose a lower speech-bandwidth route than the phone
microphone. A single 48 kHz model would therefore make AI unavailable or incorrect on many
communication routes.

The 16 kHz GTCRN path gives the system a lightweight speech-enhancement option without forcing
a hidden resampler.

## Runtime packaging

The sherpa Android AAR is used as a verified source of native runtime libraries. Build tooling
downloads release 1.13.8, verifies SHA-256, then extracts required arm64-v8a and x86_64
shared libraries.

The runtime libraries are not committed to Git.

## Threading

Neural inference never runs inside an Oboe data callback.

The native realtime callback writes to lock-free buffers. A separate AI worker consumes model
frames and invokes the streaming denoiser.

## Gating

AI activates only when:

1. runtime loads successfully;
2. model file exists;
3. exact SHA-256 and byte size match;
4. model-reported sample rate matches its catalog entry;
5. actual audio input rate matches the selected model;
6. the AI worker starts successfully.

Any failed gate leaves DSP available.

## Fallback

AI -> DSP -> RAW

No error in model download, selection or inference may remove the STOP control or corrupt the
factory DSP path.
