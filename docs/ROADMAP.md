# BudsIA V3 — Incremental roadmap

## Phase A — foundation — DONE
- Android project
- Hilt
- Compose
- reference microphone capture
- deterministic DSP
- metrics
- privacy baseline
- unit tests
- CI

## Phase B — native realtime audio core — IMPLEMENTED, DEVICE VALIDATION PENDING
- Oboe / AAudio
- C++20
- lock-free SPSC rings
- native processing worker
- exclusive -> shared fallback
- unprocessed -> voice-recognition input fallback
- actual route IDs and sample rates
- xrun/drop/underrun telemetry
- RAW vs DSP comparison
- private-route live monitor guard
- stop capture on screen background
- host native ring-buffer test

Gate before Phase C:
- CI green
- physical-device capture test
- route-change test
- Bluetooth output test
- verify no audible glitches under baseline DSP load

## Phase C — neural enhancement
- STFT/iSTFT or model-native spectral frontend
- ONNX Runtime Mobile
- causal streaming model
- model manifest + SHA-256
- CPU/XNNPACK baseline
- NNAPI benchmark only when beneficial
- AI -> DSP fallback
- measured inference latency

## Phase D — adaptive profiles
- environment profile
- target embeddings
- user feedback: emphasize / keep / reduce / ignore
- versioned local adaptation state
- immutable factory model

## Phase E — local learning
- explicit opt-in local dataset
- candidate profile/model
- offline evaluation
- measurable promote/reject gate
- rollback support

## Quality gate for every phase

1. unit/native tests
2. debug build
3. release compile
4. no sensitive logging
5. documented failure behavior
6. no invented telemetry
7. device validation before performance claims
