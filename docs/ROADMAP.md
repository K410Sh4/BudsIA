# BudsIA V3 — Incremental roadmap

## Phase A — foundation — DONE
- Android project
- Hilt
- Compose
- deterministic reference path
- metrics
- privacy baseline
- tests
- CI

## Phase B — native realtime audio core — DONE IN CODE, DEVICE VALIDATION PENDING
- Oboe / AAudio
- C++20
- lock-free SPSC rings
- native DSP worker
- actual route IDs/sample rates
- xrun/drop/underrun telemetry
- RAW vs DSP
- private-route monitor guard
- lifecycle-safe stop
- native concurrency test

## Phase C — streaming neural enhancement — DONE IN CODE, DEVICE VALIDATION PENDING
- verified sherpa-onnx runtime packaging
- verified DPDFNet2 48 kHz HR model lifecycle
- explicit model integrity gate
- isolated non-realtime inference worker
- native AI input/output transport
- RAW / DSP / AI switching
- measured inference timing
- moving realtime factor
- automatic AI -> DSP fallback
- neural output waveform/levels
- unit tests for critical fallback gates

Physical-device validation remains mandatory before performance or acoustic-quality claims.

## Phase D — adaptive profiles — NEXT
- environment profiles
- target/preference parameters
- user feedback: emphasize / keep / reduce / ignore
- versioned local adaptation state
- DataStore persistence
- immutable factory model
- rollback-safe profile revisions

## Phase E — model evaluation and personalization
- local evaluation corpus
- A/B metrics
- candidate profile/model
- promote/reject gate
- rollback
- no uncontrolled online weight mutation

## Phase F — advanced target focus
- acoustic embeddings
- user-selected target sound
- source-aware enhancement experiments
- optional semantic sound classes
- performance-tier selection

## Quality gate for every phase

1. unit/native tests
2. debug build
3. release build
4. no sensitive logging
5. documented failure behavior
6. no invented telemetry
7. device validation before performance claims
