# ADR-0004 — Safe local self-learning

Status: Accepted  
Date: 2026-09-30

## Goal

Allow BudsIA to improve for the user without letting a continuously running model mutate its
own weights without evaluation.

## Decision

The first self-learning layer is profile-level adaptation, not online neural weight training.

Each environment profile stores a bounded neural/raw mix and feedback history.

Initial profiles:

- Geral
- Casa
- Rua
- Carro
- Trabalho

All start from the same neutral product baseline. They diverge only from local user feedback.

## Feedback

The live IA mode exposes four explicit labels:

- BETTER
- WORSE
- TOO_AGGRESSIVE
- TOO_WEAK

The adaptation policy changes only the profile's neural mix.

Learning steps shrink as feedback accumulates. This reduces oscillation and protects mature
profiles from large single-event changes.

## Signal path

```
original frame
      |
      +-------> neural model ------+
      |                           |
      +---------------------------+--> bounded adaptive mixer --> output
```

The mixer is outside Oboe callbacks.

If original and enhanced frame lengths do not align, BudsIA uses the neural output unchanged
and records that adaptive mixing was bypassed. It does not invent alignment.

## Persistence

Profiles are stored relationally in Room 2.8.5.

The database does not use destructive migration fallback.

Raw audio is not stored as training data by this phase.

## Why this is safer than continuous weight updates

Uncontrolled online weight updates can cause model drift and catastrophic regression.

BudsIA therefore separates:

```
immutable factory model
        ↓
bounded adaptive profile
        ↓
future candidate model
        ↓
offline evaluation
        ↓
promote or reject
```

A future local-training phase may create candidate models, but it must never overwrite the
factory model directly.
