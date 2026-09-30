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
- host concurrent ring-buffer test

## Phase C — streaming neural enhancement — IMPLEMENTED, DEVICE VALIDATION PENDING
- sherpa-onnx 1.13.8 runtime
- DPDFNet2 48 kHz HR model profile
- explicit user-triggered model download
- exact model byte-size + SHA-256 verification
- runtime native-library SHA verification in CI
- bounded AI PCM transport separated from Oboe callbacks
- complete model-frame delivery
- measured inference latency
- moving realtime-factor telemetry
- automatic AI -> DSP fallback when realtime cannot be sustained
- neural output RMS / peak / waveform telemetry
- AI mode starts from DSP and activates only after model preparation succeeds
- no raw-audio persistence
- debug and release builds verified by CI

### Physical-device gate before quality/performance claims
- microphone capture on target phone
- Galaxy Buds input/output routing
- route-change/disconnect recovery
- long-session thermal test
- AI RTF under quiet / speech / noise workloads
- output underrun test with monitoring enabled
- subjective and objective RAW vs DSP vs AI comparison
- confirm no audible glitches during DSP <-> AI transitions

## Phase D — adaptive profiles — IMPLEMENTED, ACTIVATION GATE PENDING
- immutable factory model
- versioned local preference profile
- separate profiles for Geral / Casa / Rua / Trabalho / Carro
- explicit user feedback: mais filtro / mais natural / está bom assim
- decaying bounded learning step
- stable AndroidX DataStore 1.2.1 persistence
- revision and feedback counters
- reset to factory preference
- preference UI with transactional slider commit
- no raw-audio storage
- no silent model-weight training
- learned preference is not applied to live audio until A/B validation is safe

## Phase E — local evaluation and learning
- explicit opt-in local dataset
- candidate profile/model
- offline evaluation
- measurable promote/reject gate
- rollback support
- dataset delete/export controls

## Quality gate for every phase

1. unit/native tests
2. debug build
3. release compile
4. no sensitive logging
5. documented failure behavior
6. no invented telemetry
7. device validation before performance claims
