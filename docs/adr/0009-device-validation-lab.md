# ADR-0009 — In-app physical-device validation lab

Status: Accepted for implementation  
Date: 2026-09-30

## Decision

BudsIA includes a short in-app validation lab that samples **technical telemetry only** while
the existing realtime audio session is running.

The first validation window is 30 seconds. It is intended as a fast gate before the longer
physical test protocol.

No raw audio is written by the lab.

## Start gate

Validation may start only when:

- the audio pipeline is in `LISTENING`;
- a realtime snapshot exists;
- if AI is selected, the neural runtime is already in `RUNNING`.

This prevents model startup time from being misclassified as steady-state inference
performance.

## Frozen configuration

While a validation run is active, BudsIA blocks mutations that would make the sample set
internally inconsistent:

- processing-mode changes;
- output-monitor changes;
- model install/removal;
- environment/profile changes;
- adaptive-candidate changes;
- adaptive A/B actions;
- performance-policy changes.

Stopping the audio session cancels validation.

## Sampled telemetry

Once per second, the lab records an in-memory sample containing:

- native engine and processing state;
- actual input/output sample rates;
- input frames;
- input drops;
- AI-input transport drops;
- output overruns/underruns;
- input/output XRuns when available;
- native route disconnect count;
- neural state, model, required sample rate and realtime factor;
- Android thermal state;
- 10-second thermal-headroom estimate when available;
- CPU-headroom estimate when available;
- battery and power-save state;
- process CPU estimate;
- performance-governor decision;
- whether the adaptive candidate was active and its bounded strength.

Samples are discarded after the report is produced.

## Technical result

Checks may report:

- `PASS`
- `WARN`
- `FAIL`
- `UNKNOWN`

These states describe measurable runtime stability only. They are **not** a subjective or
objective acoustic-quality score.

Important failure gates include:

- unstable native engine state;
- processing-mode drift;
- input-rate change;
- meaningful input/AI-input loss;
- route disconnect;
- severe thermal state;
- governor safety fallback;
- AI fallback;
- model/rate mismatch;
- neural realtime factor above 1.0.

Output overrun/underrun and XRuns can produce warnings where the route exposes relevant
telemetry.

## Privacy

The report stores no PCM, waveform history, conversation content, transcript or speaker
identity.

The lab does not start a second microphone stream and does not add background recording.

## Promotion rule

A 30-second PASS is a quick technical gate only.

Model/adaptive promotion still requires the longer physical-device procedure in
`docs/DEVICE_VALIDATION.md`, including sustained thermal testing and acoustic A/B listening.
