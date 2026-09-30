# ADR-0007 — In-app physical-device validation lab

Status: Accepted for implementation  
Date: 2026-09-30

## Decision

BudsIA includes a 30-second technical validation session that observes the already-running
audio pipeline and produces a structured report without saving microphone audio.

CI cannot prove physical headset routing, route bandwidth, acoustic stability, thermal
behavior or realtime performance on a specific phone. This lab converts the app's existing
telemetry into a repeatable on-device technical gate.

## Collected telemetry

At one-second intervals the lab samples:

- realtime engine state;
- requested/active processing mode;
- input/output sample rates;
- input/output frame counters;
- input dropped samples;
- output underrun samples;
- input/output XRuns when exposed;
- monitoring state;
- neural state/model/sample rate;
- neural realtime factor;
- adaptive candidate active/strength;
- Android thermal level;
- Android 10-second thermal-headroom forecast when available;
- CPU headroom when available;
- battery percentage;
- power-save mode;
- estimated BudsIA process CPU usage;
- current AI performance-governor tier/fallback decision.

No PCM is copied into the report.

## Checks

The evaluator reports PASS / WARN / FAIL / UNKNOWN for:

- realtime engine continuity;
- processing-mode stability;
- input sample-rate stability;
- input sample loss;
- monitored-output underruns;
- XRuns;
- measured thermal pressure;
- performance-governor forced fallback;
- neural state continuity when AI is requested;
- neural model/sample-rate compatibility;
- neural realtime factor.

Predictive headroom values are recorded for inspection but the evaluator does not duplicate
the governor's policy thresholds. The governor remains the single source of truth for
performance fallback.

## Configuration freeze

While a validation run is active, BudsIA rejects configuration mutations that would invalidate
the sample window, including:

- processing-mode changes;
- monitor changes;
- adaptive-profile changes;
- candidate-control changes;
- performance-policy changes;
- model installation/removal.

The user can cancel the lab first and then change configuration.

## AI validation gate

An AI validation run may start only after:

- the native audio pipeline is LISTENING;
- the neural runtime is RUNNING.

This prevents model-loading time from being misclassified as realtime instability.

## Interpretation

The validation report is a technical stability report, not an acoustic-quality score.

A PASS does not claim that noise suppression sounds better. Acoustic A/B remains a separate
human/device validation step.

## Privacy

The lab stores no raw audio and no conversation content.
The current report lives only in screen state.
