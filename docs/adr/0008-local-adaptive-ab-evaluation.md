# ADR-0008 — Local adaptive A/B evaluation

Status: Accepted for implementation  
Date: 2026-09-30

## Decision

BudsIA evaluates the adaptive candidate through explicit local A/B listening choices before
any default promotion is considered.

The comparison is:

- **Factory:** 100% verified neural output;
- **Candidate:** the same verified model plus the current bounded adaptive wet/dry profile.

RAW audio is not one of the A/B variants.

## Audition gate

A preference can be recorded only after the user has intentionally auditioned both Factory
and Candidate in the current comparison round while the neural pipeline is running.

After a vote, the round flags reset and both variants must be auditioned again.

This prevents repeated votes without a current comparison.

## Stored data

Only local counters are stored per acoustic environment:

- Factory preferred;
- Candidate preferred;
- No difference.

No raw PCM, waveform, transcript, neural activation, device identity or conversation content
is persisted by the A/B evaluation repository.

## Interpretation

The evaluator requires:

- at least 8 total comparisons;
- at least 5 decisive comparisons;
- at least 65% of decisive choices for a directional preference.

Possible states:

- INSUFFICIENT_DATA;
- CANDIDATE_PREFERRED;
- FACTORY_PREFERRED;
- MIXED.

These states summarize explicit user preference. They are not objective audio-quality scores.

## Promotion policy

A CANDIDATE_PREFERRED result does not automatically promote or enable the candidate.

Default promotion still requires the physical validation gate:

- stable realtime factor;
- no regression in drops/underruns;
- no new clipping/artifacts;
- repeated user preference;
- rollback to factory behavior.

## Privacy

All counters remain in app-private DataStore.

The A/B harness does not enable audio recording or add background capture.
