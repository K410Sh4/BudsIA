# BudsIA V3 — Architecture

## Objective

BudsIA V3 is an auditable, local-first adaptive audio-focus system.

The architecture separates capture, routing, deterministic DSP, neural enhancement,
adaptation, output, diagnostics, model lifecycle and UI.

## Foundation rule

One module = one responsibility = one observable contract.

No component is allowed to silently own Bluetooth, capture, neural inference, storage and UI
at the same time.

## Production live path

```
Android route
  -> Oboe/AAudio native input callback
  -> lock-free SPSC input ring
  -> native processing worker
  -> RAW or DSP processor
  -> lock-free SPSC output ring
  -> optional native output callback
```

Kotlin is not in the per-audio-frame path.

It performs:

- lifecycle/control;
- route description;
- Compose state;
- telemetry polling;
- user-visible errors.

## Reference path

The original Kotlin `AudioCaptureEngine -> AudioFocusPipeline` remains available as a
deterministic reference/test path.

It is not the production realtime route.

## Surgical replacement points

Native:
- input/output stream builder;
- ring buffer;
- realtime processor;
- future neural processor.

Kotlin:
- `RealtimeAudioEngine`: control/telemetry contract;
- `AudioRouteMonitor`: Android device catalog;
- `AudioFocusViewModel`: screen state only;
- Compose UI.

Future ONNX inference will implement the processor boundary without changing UI or route code.

## Failure policy

Safe degradation:

AI -> DSP -> RAW

For the current phase:
- output failure disables monitor but keeps input analysis alive;
- input failure moves the engine to ERROR;
- STOP remains user-accessible;
- no background capture service exists.

## Privacy

Raw PCM remains in memory.
Leaving the live screen stops the session.
Conversation/audio content is not written to Logcat.
