# ADR-0006 — In-app physical-device validation lab

Status: Accepted for implementation  
Date: 2026-09-30

## Decision

BudsIA includes a 30-second technical validation session that observes the already-running
audio pipeline and produces a structured report without saving microphone audio.

The lab exists because CI cannot prove physical headset routing, acoustic stability, thermal
behavior or realtime performance on a specific phone.

## Collected telemetry

At one-second intervals the lab samples:

- realtime engine state;
- active processing mode;
- input/output sample rates;
- input/output frame counters;
- input dropped samples;
- output underrun samples;
- input/output XRuns when exposed;
- monitoring state;
- neural state/model/sample rate;
- neural realtime factor;
- Android thermal level;
- battery percentage;
- estimated BudsIA process CPU usage.

No PCM is copied into the report.

## Checks

The evaluator reports PASS / WARN / FAIL / UNKNOWN for:

- realtime engine continuity;
- processing-mode stability;
- input sample-rate stability;
- input sample loss;
- monitored-output underruns;
- XRuns;
- thermal pressure;
- neural state continuity when AI is requested;
- neural model/sample-rate compatibility;
- neural realtime factor.

UNKNOWN is used when Android or the active route does not expose a metric.

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
