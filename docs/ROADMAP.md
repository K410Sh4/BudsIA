# BudsIA V3 — Incremental roadmap

## Phase A — foundation — DONE
- Android project
- Hilt
- Compose
- deterministic reference capture/DSP
- privacy baseline
- tests
- CI

## Phase B — native realtime audio core — DONE, DEVICE VALIDATION PENDING
- Oboe / AAudio
- C++20
- lock-free SPSC rings
- native processing worker
- exclusive -> shared fallback
- unprocessed -> voice-recognition input fallback
- actual route IDs and sample rates
- xrun/drop/underrun telemetry
- RAW vs DSP comparison
- private-route monitor guard
- lifecycle stop safety
- host native concurrency test

## Phase C — neural enhancement — DONE, DEVICE QUALITY VALIDATION PENDING
- verified sherpa-onnx 1.13.8 Android runtime
- DPDFNet2 48 kHz HR streaming denoiser
- explicit model download
- exact size + SHA-256 gate
- AI transport isolated from Oboe callback
- measured inference current/avg/max
- moving realtime factor
- AI -> DSP fallback
- enhanced waveform/RMS/peak

## Phase D1 — adaptive profiles — IMPLEMENTED
- Geral / Casa / Rua / Carro / Trabalho
- per-profile neural/raw mix
- live profile switching
- bounded learning policy
- diminishing learning step
- Teach AI: better / worse / too aggressive / too weak
- Room 2.8.5 persistence
- relational feedback audit events
- model identity attached to each feedback event
- immutable factory model

## Phase D2 — automatic scene adaptation — NEXT
- acoustic scene feature extraction
- local environment fingerprint
- conservative automatic profile suggestion
- explicit confidence and UNKNOWN state
- no silent profile switching until validated
- per-device evaluation dataset

## Phase E — local candidate learning
- explicit opt-in local dataset
- candidate profile/model
- offline evaluation
- regression suite
- measurable promote/reject gate
- rollback support

## Phase F — performance policy
- thermal status monitoring
- battery/session drain
- memory telemetry
- measured CPU/XNNPACK/NNAPI benchmark where supported
- automatic quality downgrade only when evidence requires it

## Device validation gate

Before performance or quality claims:
- physical-device capture test
- route-change test
- Bluetooth output test
- target Galaxy Buds route inspection
- long-session glitch/drop test
- acoustic A/B recordings with consent
- thermal/battery session benchmark

## Quality gate for every phase

1. unit/native tests
2. debug build
3. release compile
4. no sensitive production logging
5. documented failure behavior
6. no invented telemetry
7. persistent state is migratable, not destructively reset
8. device validation before performance claims
