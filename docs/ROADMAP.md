# BudsIA V3 — Incremental roadmap

## Phase A — foundation — DONE
- Android project
- Hilt / Compose
- deterministic reference DSP
- privacy baseline
- tests and CI

## Phase B — native realtime core — DONE, DEVICE VALIDATION PENDING
- Oboe / AAudio
- C++20
- lock-free SPSC rings
- dedicated native worker
- actual sample rates/device IDs
- xrun/drop/underrun telemetry
- RAW vs DSP
- lifecycle-safe stop

## Phase B.1 — explicit device routing — DONE, DEVICE VALIDATION PENDING
- selectable input/output routes
- Android 12+ communication-device routing
- Bluetooth microphone route preparation
- actual opened route as source of truth
- BLUETOOTH_CONNECT permission only when required
- safe route release
- no deprecated hidden SCO workaround

## Phase C — streaming neural enhancement — DONE, DEVICE VALIDATION PENDING
- verified sherpa-onnx 1.13.8 runtime
- DPDFNet2 48 kHz HR
- explicit model download
- exact model size + SHA-256 gate
- AI transport isolated from Oboe callback
- measured inference timing
- moving realtime factor
- AI -> DSP fallback
- enhanced waveform/RMS/peak

## Phase D1 — adaptive self-learning profiles — DONE, DEVICE VALIDATION PENDING
- Geral / Casa / Rua / Carro / Trabalho
- per-profile bounded neural/raw mix
- live profile switching
- diminishing-step learning
- Better / Worse / Too aggressive / Too weak feedback
- Room 2.8.5 persistence
- atomic feedback audit events
- model identity on every feedback event
- immutable factory model
- R8/JNI release protection

## Phase D2 — automatic acoustic scene understanding — NEXT
- dedicated `SoundSceneClassifier` interface
- local classifier running outside realtime callback
- low-duty-cycle analysis windows
- scene labels with explicit confidence / UNKNOWN
- conservative profile suggestion
- no silent automatic switch until device validation shows acceptable accuracy
- candidate: sherpa-onnx audio tagging with a compact CED model

## Phase E — local candidate learning
- explicit opt-in dataset
- candidate profile/model separate from factory
- offline evaluation
- regression suite
- promote/reject gate
- rollback and dataset delete/export controls

## Phase F — performance policy
- Android thermal status
- memory telemetry
- session battery drain
- CPU baseline and optional accelerator benchmark
- adaptive quality downgrade only from measured evidence

## Physical-device gate

Before performance or quality claims:
- phone microphone capture
- Galaxy Buds microphone discovery and selected-route verification
- HFP/BLE sample-rate and bandwidth observation
- disconnect/reconnect recovery
- 15+ minute glitch/drop run
- RAW vs DSP vs IA acoustic A/B
- thermal and battery session benchmark
- processed-output monitoring test without speaker feedback

## Quality gate for every phase

1. unit/native tests
2. debug build
3. release compile
4. verified external runtime/model integrity
5. no sensitive production logging
6. documented fallback behavior
7. no invented telemetry
8. persistent state is migratable, never destructively reset by default
9. physical-device validation before device-specific claims
