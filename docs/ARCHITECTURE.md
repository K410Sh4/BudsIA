# BudsIA V3 — Architecture

## Objective

BudsIA V3 is an auditable, local-first adaptive audio-focus system.

The architecture separates capture, routing, realtime transport, deterministic DSP, neural
enhancement, adaptation, persistence, diagnostics, model lifecycle and UI.

## Foundation rule

One module = one responsibility = one observable contract.

A change to the neural model must not require rewriting route management, Room storage or the
Compose screen. A change to the UI must not touch the Oboe callback.

## Production live path

```
Android audio route
  -> Oboe/AAudio input callback
  -> lock-free SPSC native ring
  -> native realtime worker
  -> dedicated AI SPSC transport
  -> non-realtime neural worker
  -> verified streaming denoiser
  -> AdaptiveAudioMixer
  -> native output ring
  -> optional private output callback
```

The model never executes inside an Oboe callback.

## Managed control plane

Kotlin handles:

- lifecycle and user controls;
- route descriptions;
- verified model installation;
- neural worker orchestration;
- adaptive profile state;
- Room persistence;
- low-frequency telemetry;
- Compose state and diagnostics.

## Surgical replacement points

Native:
- stream builder;
- lock-free rings;
- RAW/DSP processor;
- AI transport boundary.

Neural:
- `StreamingNeuralEnhancer`;
- `StreamingAiCoordinator`;
- model catalog/runtime.

Adaptation:
- `AdaptiveTuningEngine`;
- `AdaptiveAudioMixer`;
- `AdaptiveProfileRepository`;
- `AdaptiveProfileController`.

Storage:
- Room entities/DAOs;
- repository mapping and transactions.

UI:
- `AudioFocusViewModel`;
- Compose live screen.

## Safe learning architecture

```
Factory model (immutable)
        ↓
Neural output
        ↓
AdaptiveAudioMixer
        ↑
Active environment profile
        ↑
Explicit local feedback
        ↓
Room transaction
  ├─ profile update
  └─ feedback audit event
```

A user correction updates a bounded profile parameter. It does not mutate factory weights.

Every persisted feedback event records:
- profile;
- model ID;
- feedback type;
- previous mix;
- resulting mix;
- timestamp.

This supports later evaluation and rollback analysis.

## Failure policy

Safe degradation:

```
AI -> DSP -> RAW
```

- output failure disables monitoring but preserves input analysis;
- input failure moves the engine to ERROR;
- slow sustained neural inference falls back to DSP;
- model integrity failure prevents AI activation;
- profile persistence failure does not corrupt the factory model;
- STOP remains user-accessible.

## Privacy

Raw PCM remains in memory by default.
Leaving the live screen stops capture.
Raw audio is not persisted as training data.
Conversation/audio contents are not written to production Logcat.
Adaptive learning stores parameters and feedback metadata, not captured audio.
