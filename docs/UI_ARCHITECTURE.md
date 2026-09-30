# Layered UI Architecture

## Goal

BudsIA separates the everyday human experience from engineering telemetry without removing
technical capability.

## User-facing layers

### Escuta

The default surface answers five questions immediately:

- Is capture active?
- Is local AI active?
- Which input/output route is active?
- Can processed audio be heard?
- Did a protective fallback occur?

Raw RTF, XRuns, headroom, provider and validation counters are intentionally not mixed into
this surface.

### Rotas

Input and output devices use friendly labels first. Product name, Android type label and device
id remain visible as technical subtitles. Bluetooth HFP/SCO communication constraints remain
explicit.

### IA

Neural package management, automatic exact-rate model selection, adaptive profiles and local
Factory/Candidate A/B evaluation stay together.

### Diagnóstico

Realtime transport, streaming inference, ADPF/performance, thermal state and categorized
technical notices are kept here.

### Lab

The 30-second Device Validation Lab is isolated from the normal listening flow. PASS/WARN/FAIL
continues to describe measurable runtime stability, not acoustic quality.

### Dev/IA

The screen exposes an `AdaptiveSessionState` schema and copyable JSON snapshot. It aggregates
route, inference, performance and validation state and intentionally contains no raw audio.

## Structured state

`AudioFocusPresentation.kt` derives independent presentation states:

- `ListeningUiState`
- `RoutingUiState`
- `ModelsUiState`
- `DiagnosticsUiState`
- `LabUiState`
- `DeveloperUiState`

The existing `AudioFocusUiState` remains the authoritative runtime state. The layered states
are derived read-only projections, which avoids duplicating control state or changing the audio
engine.

## Before vs after

### Before

One vertically long screen mixed:

- human controls;
- model management;
- adaptive training controls;
- ADPF;
- realtime metrics;
- route selection;
- Device Validation Lab;
- raw failure reasons.

### After

The same capabilities are grouped by intent:

- everyday listening is concise;
- routing is explicit;
- AI/model controls are isolated;
- diagnostics are intentionally technical;
- validation is a dedicated lab;
- automation/AI consumers receive a structured snapshot.

No audio-processing feature is removed by this UI refactor.
