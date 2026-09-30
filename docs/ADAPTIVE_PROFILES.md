# Adaptive profiles

## Goal

Adaptive profiles are BudsIA's first safe personalization layer.

They learn **explicit user preferences** without mutating factory neural-model weights during a
live session.

## Current profile dimensions

Each acoustic environment has its own versioned profile:

- Geral
- Casa
- Rua
- Trabalho
- Carro

A profile stores:

- schema version;
- environment;
- monotonically increasing revision;
- preferred enhancement strength;
- total feedback count;
- positive "good as is" feedback count.

No raw audio is stored by this system.

## Feedback

The current explicit feedback actions are:

- `MORE_FILTER`
- `MORE_NATURAL`
- `GOOD_AS_IS`

The tuning step decays as more feedback is collected. Values remain bounded, so a long sequence
of accidental taps cannot produce an invalid profile.

## Persistence

Profiles use AndroidX DataStore 1.2.1, the stable 2026 release.

Writes are transactional. Each environment is stored independently and the active environment
is a separate key.

## Critical safety rule

The learned preference is **not yet allowed to alter streaming neural output automatically**.

DPDFNet online enhancement does not expose the offline attenuation-limit feature, and naïve
dry/wet mixing can be phase-incorrect if neural output has algorithmic delay.

Therefore Phase D deliberately separates:

```
learn preference
  -> persist/version
  -> evaluate candidate behavior
  -> only then activate
```

This prevents a personalization feature from silently degrading audio.

## Next gate

Before a learned preference controls live audio, BudsIA needs an alignment-safe adaptation
mechanism and an A/B evaluator using consented test audio or an equivalent objective benchmark.

Factory behavior must always remain recoverable.
