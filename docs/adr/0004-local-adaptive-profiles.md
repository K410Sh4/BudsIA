# ADR-0004 — Versioned local adaptive preference profiles

Status: Accepted for implementation  
Date: 2026-09-30

## Decision

BudsIA stores explicit user audio preferences locally with AndroidX DataStore 1.2.1.

The adaptive profile is deliberately separate from neural-model weights.

Current profile dimensions:

- acoustic environment: General, Home, Street, Work, Car;
- preferred enhancement strength, bounded from 0.25 to 1.00;
- monotonic local revision;
- feedback count;
- positive-feedback count.

Explicit feedback options are:

- More filter;
- More natural;
- Good as is.

## Safety boundary

Phase D does **not** silently retrain a neural model and does not automatically change live
audio from the stored preference.

The profile is an auditable candidate-control signal. It must first pass physical-device A/B
evaluation before it is allowed to influence realtime enhancement parameters.

This prevents a preference-learning feature from becoming an unmeasured self-modifying audio
pipeline.

## Learning rule

The profile tuner uses a bounded decaying step.

More feedback produces smaller future adjustments. The value is always clamped to the factory
safety range.

The rule is deterministic and covered by unit tests.

## Persistence

- storage: app-private Preferences DataStore;
- schema version: 1;
- one independent profile per acoustic environment;
- active environment stored separately;
- corrupted/invalid numeric values are clamped to safe bounds;
- unsupported schema versions fall back to a factory profile;
- app backup remains disabled.

No raw PCM, transcript, microphone recording or neural feature vector is stored by this
profile system.

## UX

Slider changes are persisted only when the user commits the slider interaction, not on every
pixel movement.

Feedback actions persist one bounded profile revision.

The UI tells the user that the preference is saved locally and is not yet automatically
controlling the realtime engine.

## Promotion gate

A future profile-to-audio policy may be enabled only after:

1. deterministic mapping from profile to a supported model/DSP parameter;
2. A/B test on the physical target device;
3. no regression in realtime factor, drops or output underruns;
4. rollback to factory behavior;
5. clear UI indication when adaptive control is actually active.
