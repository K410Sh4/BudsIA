# ADR-0005 — AI performance governor

Status: Accepted for implementation  
Date: 2026-09-30

## Decision

BudsIA continuously observes device health with Android platform APIs and uses a deterministic
governor to decide whether local AI remains safe to run.

Observed signals:

- Android thermal status;
- battery percentage;
- charging state;
- Android low-memory flag;
- available/total memory;
- estimated BudsIA process CPU load.

## Measurement labels

BudsIA distinguishes:

- MEASURED: value reported directly by Android;
- ESTIMATED: value derived from measured counters;
- UNKNOWN: unavailable.

Current CPU percentage is explicitly ESTIMATED. It is calculated from process CPU time versus
elapsed wall time and normalized by available processors.

Thermal state, battery state and memory values are platform-reported.

## Governor policy

Default policy:

- severe/critical/emergency/shutdown thermal state -> force AI to DSP;
- Android low-memory signal -> force AI to DSP;
- battery <= 15% while not charging -> force AI to DSP;
- moderate thermal state or battery <= 25% while not charging -> recommend ECO;
- stable thermal state while charging -> recommend MAX_QUALITY;
- otherwise -> BALANCED.

Only DSP_ONLY is currently enforced automatically during a live AI session.

ECO/BALANCED/MAX_QUALITY are recommendations until a measured runtime policy proves that
changing model/runtime parameters mid-session is stable on the target device.

## Realtime safety

The governor is evaluated outside the Oboe callback.

When a forced fallback condition appears:

```
AI worker
  -> governor detects unsafe condition
  -> switch native mode to DSP
  -> clear AI transport
  -> preserve STOP control
  -> surface reason in UI
```

No thermal/battery policy may block microphone shutdown.

## Non-goals

This phase does not:

- invent temperature values;
- estimate device skin temperature;
- overclock/underclock hardware;
- force GPU/NPU execution;
- claim battery runtime before device testing.
