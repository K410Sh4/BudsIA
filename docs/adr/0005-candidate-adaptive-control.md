# ADR-0005 — Explicit candidate adaptive control

Status: Experimental / opt-in  
Date: 2026-09-30

## Decision

BudsIA may apply a stored local preference to live neural output only when the user explicitly
enables **candidate adaptive control**.

The factory neural model remains immutable.

The candidate mapping is intentionally simple and auditable:

```
output = original * (1 - strength) + neural * strength
```

where `strength` is the bounded profile value in the interval 0.25–1.00.

## Why a wet/dry mapping first

A preference profile should not immediately mutate model weights.

A bounded wet/dry mapping provides:

- deterministic behavior;
- constant-time processing;
- no extra model inference;
- no hidden retraining;
- instant rollback to factory neural output;
- an understandable A/B variable for physical validation.

## Activation

Default: OFF.

The candidate switch is not persisted as an automatic startup choice.

A saved profile may exist, but it only affects audio after the user explicitly enables the
candidate control in the current app process.

## Threading

The current adaptive config is stored in an atomic reference.

Profile changes can therefore be published from managed UI/repository code without locking the
neural worker.

The mixer runs after neural inference, on the non-realtime AI worker. It never runs in the
Oboe callback.

## Failure and fallback

The existing fallback chain remains unchanged:

AI candidate -> factory DSP -> RAW

Disabling candidate control returns neural output to 100% wet factory behavior.

## Validation status

The mapping is implemented and unit-tested but is not declared acoustically superior.

Promotion to a default-on behavior requires physical A/B validation on the target phone and
Galaxy Buds route, including:

- speech preservation;
- artifacts;
- realtime factor;
- input drops;
- AI-input drops;
- output underruns;
- clipping;
- user preference repeatability.

Until that gate passes, the UI labels the control as CANDIDATE / experimental.
