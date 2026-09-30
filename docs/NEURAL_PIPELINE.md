# Neural enhancement pipeline

## Current implementation

BudsIA V3 integrates a streaming speech-enhancement engine behind an isolated interface.

```
Oboe input callback
  -> native SPSC AI input ring
  -> non-realtime AI worker
  -> sherpa-onnx OnlineSpeechDenoiser
  -> DPDFNet2 48 kHz HR
  -> native SPSC output ring
  -> optional Oboe monitor output
```

The Oboe callback never performs inference.

## Factory model

Current MAX QUALITY candidate:

- ID: `dpdfnet2-48k-hr`
- family: DPDFNet
- sample rate: 48 kHz
- bytes: 10,596,848
- SHA-256: `0b399f8a58dc4d70d8cd97541f5c39869406145193b957d00a03b66070944928`

The model is not bundled in the APK. Installation is explicit and verified before use.

## Runtime

- sherpa-onnx 1.13.8
- ONNX Runtime supplied by the verified sherpa Android runtime
- CPU provider baseline
- two inference threads in the initial profile

The provider is intentionally not auto-switched to NNAPI without a measured device benchmark.

## Realtime health gate

The AI coordinator measures inference duration with the monotonic clock and computes a moving
realtime factor (RTF).

If sustained RTF exceeds 1.10 after warmup, BudsIA falls back to DSP before queue growth turns
into multi-second latency.

## Compatibility gate

AI starts only when the opened input route's actual sample rate equals the model's required
sample rate. Current factory candidate requires 48 kHz.

A route mismatch produces an explicit DSP fallback. No implicit low-quality resampling is
hidden from the user.

## Integrity gate

Model installation requires:

1. HTTPS download;
2. exact byte count;
3. exact SHA-256;
4. atomic promotion from a temporary file;
5. post-install re-verification.

Partial or corrupt files are rejected.

## Observability

The UI exposes:

- model state;
- provider;
- model frame size;
- current/average/max inference time;
- moving RTF;
- neural input drops;
- enhanced sample count;
- enhanced RMS/peak;
- enhanced waveform;
- fallback reason.

No synthetic confidence score is generated.
