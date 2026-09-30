# ADR-0002 — Streaming neural enhancement runtime

Status: Accepted for implementation  
Date: 2026-09-30

## Decision

BudsIA's first production neural speech-enhancement runtime uses sherpa-onnx 1.13.8
with its streaming speech-denoiser API.

The initial MAX QUALITY candidate is:

- model: DPDFNet2 48 kHz high-resolution
- asset: dpdfnet2_48khz_hr.onnx
- expected size: 10,596,848 bytes
- SHA-256: 0b399f8a58dc4d70d8cd97541f5c39869406145193b957d00a03b66070944928
- provider baseline: CPU

The model is downloaded explicitly by the user and verified before activation.
It is not bundled inside the APK.

## Why DPDFNet2 48 kHz first

The realtime audio core commonly exposes full-band mobile routes near 48 kHz and this
model is provided specifically for 48 kHz enhancement output. Using the 48 kHz model avoids
silently degrading the live path to 16 kHz just to satisfy a smaller speech model.

The lighter GTCRN model remains a planned ECO profile. It expects 16 kHz and therefore needs
a measured, high-quality streaming resampling stage before it can be enabled without hidden
quality tradeoffs.

## Runtime packaging

The sherpa Android AAR is used only as a verified source of its native runtime libraries.
Build tooling downloads release 1.13.8, verifies SHA-256, then extracts the required
arm64-v8a and x86_64 shared libraries.

The runtime libraries are not committed to Git.

## Threading

Neural inference never runs inside an Oboe data callback.

The native realtime callback writes to lock-free buffers. A separate AI worker consumes
chunks and invokes the streaming denoiser.

Until the direct native C/C++ denoiser path is validated, the phase-C bridge is isolated
behind an interface so JNI transport can later be replaced without touching UI, routing,
model lifecycle or adaptation logic.

## Gating

AI mode may activate only when:

1. the runtime loads successfully;
2. the model exists;
3. exact SHA-256 and byte size match;
4. the model-reported input rate matches the live route;
5. the AI worker starts successfully.

Any failed gate leaves DSP available.

## Fallback

AI -> DSP -> RAW

No error in model download or inference may stop the microphone STOP control or corrupt the
factory DSP path.
