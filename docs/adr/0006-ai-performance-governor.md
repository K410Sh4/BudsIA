# ADR-0006 — Measured AI performance governor

Status: Accepted for implementation  
Date: 2026-09-30

## Decision

BudsIA continuously observes device health using Android platform signals and protects the
local neural audio path with a deterministic governor.

Observed signals:

- Android thermal status;
- battery percentage;
- charging state;
- Android low-memory flag;
- available/total memory;
- estimated BudsIA process CPU load.

## Measurement labels

BudsIA distinguishes:

- **MEASURED**: platform-reported value;
- **ESTIMATED**: derived from measured counters;
- **UNKNOWN**: unavailable.

Process CPU percentage is explicitly ESTIMATED from process CPU time versus elapsed wall time,
normalized by available processors.

No device temperature in Celsius is invented.

## Enforcement

Mandatory automatic fallback:

- severe/critical/emergency/shutdown thermal status -> DSP;
- Android low-memory signal -> DSP.

Optional battery fallback:

- disabled by default;
- user may enable "Parar IA com bateria baixa";
- default threshold when enabled: 15%;
- configurable from 5% to 30%.

Moderate thermal or battery <= 25% may recommend ECO, but BudsIA does not silently change to
an incompatible model. Model selection still requires an exact input sample-rate match.

## Threading

Performance sampling and governor decisions run outside Oboe callbacks.

A forced fallback:

```
AI worker
  -> governor decision
  -> native mode DSP
  -> AI transport cleared
  -> reason exposed to UI
```

STOP remains available.

## Interaction with adaptive profiles

The performance governor has priority over candidate adaptive control.

If device health requires fallback, the candidate mixer stops with the AI worker and the
native DSP path continues.

## Non-goals

This phase does not:

- overclock or underclock the device;
- force GPU/NPU execution;
- claim battery-runtime estimates without physical measurement;
- claim a recommended tier is actually active unless a compatible runtime/model is selected.
