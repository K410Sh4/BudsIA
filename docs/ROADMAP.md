# BudsIA V3 — Incremental roadmap

## Phase A — foundation
- Android project
- Hilt
- Compose
- real microphone capture
- stateful DSP
- metrics
- stage timing
- privacy baseline
- unit tests
- CI

## Phase B — real-time native audio core
- Oboe/AAudio
- lock-free ring buffers
- input/output route abstraction
- underrun/overrun telemetry
- loopback diagnostics where supported

## Phase C — neural enhancement
- ONNX Runtime Mobile
- model manifest with SHA-256
- model variants
- STFT/iSTFT
- neural mask/deep filtering
- explicit fallback on inference failure

## Phase D — adaptive profiles
- environment profile
- target embeddings
- user feedback: emphasize / keep / reduce / ignore
- versioned local adaptation state
- no uncontrolled mutation of the factory model

## Phase E — local learning
- opt-in local dataset
- candidate model/profile
- benchmark against current model
- promote only on measurable improvement
- rollback support

## Quality gates

Every phase must pass:
1. unit tests
2. debug build
3. release compile
4. no sensitive logging
5. documented failure/fallback behavior
