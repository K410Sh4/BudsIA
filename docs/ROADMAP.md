# BudsIA V3 — Incremental roadmap

## Phase A — foundation — DONE
- Android project
- Hilt
- Compose
- deterministic DSP
- privacy baseline
- unit tests
- CI

## Phase B — native realtime audio core — IMPLEMENTED, DEVICE VALIDATION PENDING
- Oboe / AAudio
- C++20
- lock-free SPSC rings
- native processing worker
- actual route IDs and sample rates
- xrun/drop/underrun telemetry
- RAW vs DSP comparison
- private-route live monitor guard

## Phase B.1 — explicit device routing — IMPLEMENTED, DEVICE VALIDATION PENDING
- selectable input/output devices
- Android 12+ communication-device routing for Bluetooth microphones
- BLUETOOTH_CONNECT runtime permission
- Oboe VoiceCommunication input preset for prepared communication routes
- actual opened device IDs remain the source of truth
- previous Android audio mode restored after session

## Phase C — streaming neural enhancement — IMPLEMENTED, DEVICE VALIDATION PENDING
- sherpa-onnx 1.13.8
- verified DPDFNet2 48 kHz HR
- verified GTCRN Simple 16 kHz
- exact model size + SHA-256
- route-rate-aware model selection
- no hidden resampling
- measured inference latency
- moving realtime factor
- automatic AI -> DSP fallback
- neural output waveform / RMS / peak
- debug/release CI

### Physical-device gate
- Galaxy Buds microphone is the native stream's actual input device
- measure actual HFP/BLE microphone rate on target phone
- confirm 16 kHz route selects GTCRN automatically
- confirm 48 kHz route selects DPDFNet2 automatically
- test unsupported rates fall back to DSP
- long-session thermal/RTF test
- output underrun test with monitoring enabled
- RAW vs DSP vs AI listening comparison

## Phase D — adaptive profiles — IMPLEMENTED WITH OPT-IN EXPERIMENTAL RUNTIME CONTROL
- immutable factory model remains unchanged
- stable DataStore 1.2.1 persistence
- schema-versioned local profile
- independent profiles: Geral / Casa / Rua / Trabalho / Carro
- bounded preferred enhancement strength
- explicit feedback: Mais filtro / Mais natural / Está bom assim
- decaying deterministic adjustment step
- slider commits once per interaction instead of writing continuously
- reset per environment
- unit-tested safety bounds
- no raw audio persistence
- no silent online model training
- opt-in deterministic dry/wet mapping to verified neural output
- adaptive mixing outside Oboe callback
- allocation-free reusable scratch buffer
- live profile revision/strength telemetry
- frame mismatch bypasses adaptive mix instead of corrupting output
- default OFF with immediate rollback to 100% neural output

### Promotion gate for adaptive control
- define a deterministic mapping to supported DSP/neural parameters
- compare factory vs adaptive mapping using the same physical test material
- no regression in speech preservation
- no regression in AI realtime factor
- no increase in input drops or output underruns
- expose active adaptive control in UI
- one-tap rollback to factory behavior

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
