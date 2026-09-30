# ADR-0005 — Opt-in adaptive runtime control

Status: Accepted as experimental control path  
Date: 2026-09-30

## Decision

BudsIA can optionally apply the active local adaptive profile directly to the verified neural
output through a deterministic dry/wet blend.

The feature is disabled by default.

It is explicitly user-controlled and does not retrain, mutate or replace neural-model
weights.

## Signal path

```
dry microphone frame
  + verified neural output
  + local preferred strength
  -> bounded dry/wet blend
  -> output monitor
```

For strength `s`:

```
final = dry * (1 - s) + neural * s
```

The allowed profile range remains 0.25–1.00.

## Realtime requirements

- no allocation is performed by the blend processor per audio frame;
- one reusable scratch buffer is allocated when the neural worker starts;
- blend is performed outside the Oboe callback;
- if dry/wet frame sizes do not match, verified neural output passes through unchanged;
- output samples are clamped to the normalized PCM range.

## Live learning behavior

When the user changes the slider or submits explicit feedback, the active DataStore-backed
profile revision changes.

If runtime adaptive control is enabled, the neural worker reads the newest profile state and
uses the updated strength on subsequent frames.

This makes the filter locally self-adjusting from explicit feedback without silently
self-modifying model weights.

## Transparency

Telemetry exposes:

- adaptive control enabled;
- whether blend was actually applied;
- requested blend strength;
- active environment;
- profile revision;
- bypass reason when applicable.

## Default and rollback

Default: OFF.

Rollback is immediate: disabling runtime control returns output to 100% verified neural
signal without altering the installed model or deleting the learned preference.

## Promotion status

This is an experimental control path until physical-device A/B validation confirms that
profile-driven blending improves the requested listening behavior without unacceptable
speech coloration, artifacts, clipping, underruns or realtime-factor regressions.
