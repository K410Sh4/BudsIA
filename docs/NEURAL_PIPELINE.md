# Neural enhancement pipeline

## Current implementation

```
selected Android input route
  -> optional Android communication-device preparation
  -> Oboe input callback
  -> native SPSC input rings
  -> native deterministic DSP worker
  -> route-rate neural model selector
  -> non-realtime neural worker
  -> sherpa-onnx OnlineSpeechDenoiser
  -> native SPSC output ring
  -> optional private-route monitor
```

The Oboe callback never performs model inference.

## Verified model set

### 48 kHz route
- DPDFNet2 48 kHz HR
- MAX_QUALITY
- exact 48,000 Hz match
- SHA-256 verified before use

### 16 kHz route
- GTCRN Simple
- ECO
- exact 16,000 Hz match
- SHA-256 verified before use

Unsupported rates remain on DSP.

## Startup contract

The native audio stream starts in deterministic DSP when AI has been requested.

Only after:
1. route has opened;
2. actual input sample rate is known;
3. a compatible installed model is found;
4. model file integrity passes;
5. sherpa runtime loads;
6. runtime-reported rate matches catalog;

does BudsIA switch the live path from DSP to AI.

This prevents microphone backlog from accumulating while the model initializes.

## Realtime health gate

The AI coordinator measures inference duration and a moving realtime factor.

If sustained RTF exceeds the safety threshold after warmup, BudsIA falls back to DSP.

Measured telemetry includes:
- input drops;
- AI-input drops;
- output underruns/overruns;
- current/average/max inference time;
- moving RTF;
- enhanced sample count;
- enhanced RMS/peak;
- neural waveform.

No synthetic quality-confidence score is created.

## Adaptive preference layer

The local adaptive profile is currently **not** connected automatically to the neural worker.

It stores a bounded preference candidate per environment so a future mapping can be tested
against factory behavior before promotion.

This separation is intentional:

```
verified factory model
  + explicit local preference
  -> candidate mapping
  -> physical A/B evaluation
  -> promote or reject
```

No neural weights are modified by the current adaptive profile system.
