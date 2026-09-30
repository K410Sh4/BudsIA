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

## Phase B.1 — explicit device routing — IMPLEMENTED, DEVICE VALIDATION PENDING
- selectable input/output devices
- Android 12+ `setCommunicationDevice()` for Bluetooth microphone routes
- `BLUETOOTH_CONNECT` runtime permission
- Oboe `VoiceCommunication` preset priority for prepared communication input
- actual opened device IDs retained as the source of truth
- Android owns communication output when Bluetooth microphone input is active
- communication route is cleared only when BudsIA owns it
- Android 11 explicit Bluetooth routing fails clearly instead of using deprecated SCO APIs

### Routing device gate
- Galaxy Buds microphone appears in input catalog
- selected Buds input is the native stream's actual input device
- active route survives 15+ minute session
- disconnect/reconnect behavior is explicit and recoverable
- output does not accidentally fall back to phone speaker
- sample-rate/bandwidth reported for HFP and BLE routes

## Phase D — adaptive profiles — NEXT
- immutable factory model
- versioned local adaptation profile
- environment profile
- user feedback: emphasize / keep / reduce / ignore
- measured profile effectiveness
- rollback to factory behavior
- no silent online training

## Phase E — local learning
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
