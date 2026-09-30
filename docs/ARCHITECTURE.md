# BudsIA V3 — Architecture

## Objective

BudsIA V3 is an auditable, local-first adaptive audio-focus system.

Capture, routing, realtime transport, deterministic DSP, neural enhancement, adaptation,
persistence, diagnostics, model lifecycle and UI are independent boundaries.

## Core rule

One responsibility = one observable contract.

A neural model change must not require rewriting Bluetooth routing or Compose. A UI change
must not alter the Oboe callback. A profile-storage change must not own audio capture.

## Production signal path

```
AudioRouteController
        ↓
selected Android input
        ↓
Oboe/AAudio input callback
        ↓
lock-free SPSC input ring
        ↓
native realtime worker
        ├──────────────> RAW / DSP
        └─> AI SPSC transport
                 ↓
      StreamingNeuralEnhancer
                 ↓
         AdaptiveAudioMixer
                 ↓
       native output ring
                 ↓
 optional private output callback
```

The Oboe data callback performs bounded realtime-safe work only. Model inference, Room access,
networking and Compose never execute in the callback.

## Control plane

Kotlin owns:
- session lifecycle;
- explicit route selection;
- Android communication-route preparation/release;
- model installation/integrity;
- neural orchestration;
- adaptive profiles;
- Room transactions;
- telemetry aggregation;
- UI state.

## Surgical replacement points

### Routing
- `AudioRouteMonitor`
- `AudioRouteController`

### Native realtime
- stream builder
- SPSC rings
- realtime DSP
- AI transport boundary
- `RealtimeAudioEngine`

### Neural
- `StreamingNeuralEnhancer`
- `StreamingAiCoordinator`
- model catalog/manager

### Adaptation
- `AdaptiveTuningEngine`
- `AdaptiveAudioMixer`
- `AdaptiveProfileController`
- `AdaptiveProfileRepository`

### Persistence
- Room entities
- DAOs
- repository transactions

### Presentation
- `AudioFocusViewModel`
- Compose live screen

## Learning architecture

```
immutable factory model
        ↓
enhanced frame ─────────┐
                        ├─> AdaptiveAudioMixer
original frame ─────────┘          ↑
                              active profile
                                   ↑
                         explicit user feedback
                                   ↓
                          Room transaction
                       ┌───────────┴───────────┐
                  profile update        audit event
```

The first learning layer changes only bounded profile parameters. Factory weights are never
modified in place.

Each feedback audit event stores profile ID, model ID, feedback type, previous/new mix and
timestamp.

## Route ownership

BudsIA does not infer that a paired headset is active. It requests a route through supported
Android APIs and then treats the native stream's opened device ID as truth.

Communication-route ownership is released when the session ends. Bluetooth microphone use
does not assume simultaneous A2DP behavior.

## Failure policy

```
AI -> DSP -> RAW
```

- model integrity failure prevents AI activation;
- sustained slow inference falls back to DSP;
- output-route failure disables monitoring while preserving input analysis where possible;
- input-route failure moves the session to ERROR;
- storage failure cannot mutate the immutable factory model;
- STOP remains accessible.

## Privacy

Raw PCM stays in memory by default.
No captured audio is stored as adaptation data.
Leaving the live screen stops capture.
Production logs do not contain conversation/audio payloads.
