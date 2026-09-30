# Candidate adaptive control

## What it does

The current local profile stores a preferred neural-enhancement strength.

When the explicit candidate switch is enabled, BudsIA uses that preference as a wet/dry mix:

- 100%: full neural output;
- 85%: mostly neural output with some original signal;
- 25%: conservative neural contribution.

The allowed range is intentionally bounded.

## What it does not do

It does not:

- retrain the model;
- modify model weights;
- download a personalized model;
- upload audio;
- infer user identity;
- persist raw PCM;
- activate itself automatically.

## Live updates

Environment changes, slider commits and explicit feedback update the local profile.

If candidate control is enabled while AI is running, the new bounded value is published
atomically and is used by the next neural frames without restarting the audio stream.

## Telemetry

The neural diagnostics panel exposes:

- FACTORY vs CANDIDATE ACTIVE;
- environment;
- current mix percentage;
- profile revision;
- inference timing;
- realtime factor;
- drop/underrun counters.

This makes the candidate behavior auditable during physical testing.
