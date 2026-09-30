# BudsIA V3 — Architecture

## Objective

BudsIA V3 is an auditable, local-first adaptive audio-focus system.

The architecture deliberately separates capture, deterministic DSP, neural enhancement,
adaptation, output, diagnostics, model lifecycle and UI.

## Foundation rule

One module = one responsibility = one observable contract.

No component is allowed to silently own Bluetooth, capture, neural inference, storage and UI
at the same time.

## Current pipeline

```
Android microphone
  -> AudioCaptureEngine
  -> AudioPreprocessor
  -> AudioEnhancementEngine
  -> AudioMetricsAnalyzer
  -> AudioPipelineSnapshot
  -> AudioFocusViewModel
  -> Compose UI
```

The current enhancement implementation is an explicit BYPASS fallback. It is not presented
as AI. This makes the foundation testable before a neural model is introduced.

## Surgical replacement points

- `AudioCaptureEngine`: capture backend
- `AudioPreprocessor`: deterministic DSP
- `AudioEnhancementEngine`: neural filter
- `AudioMetricsAnalyzer`: signal metrics
- `MonotonicClock`: latency timing
- `AudioFocusPipeline`: orchestration only

Hilt binds implementations to these contracts.

## Failure policy

A future neural engine must fail closed to a safe audio path:

AI -> DSP_ONLY -> RAW

A neural failure must never block stop controls or leave microphone capture orphaned.

## Privacy

The foundation does not write raw microphone audio to disk.
No conversation content is logged.
Diagnostic audio capture, if later implemented, must be explicit and user-visible.
