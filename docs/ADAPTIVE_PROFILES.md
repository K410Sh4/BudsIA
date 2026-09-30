# Adaptive profiles

## Purpose

Adaptive profiles capture explicit listening preferences without pretending that BudsIA has
already learned a new neural network.

The current pipeline is:

```
user feedback
  -> bounded deterministic tuner
  -> versioned environment profile
  -> DataStore
  -> candidate preference signal
```

The stored signal is **not** connected to live neural inference yet.

## Environments

- Geral
- Casa
- Rua
- Trabalho
- Carro

Each environment has independent state.

## Preference field

`preferredEnhancementStrength` is a normalized preference in the safe interval 0.25–1.00.

It is intentionally model-agnostic. The same stored profile can survive model replacement.

## Feedback rule

`Mais filtro` moves preference upward.

`Mais natural` moves preference downward.

`Está bom assim` records positive feedback without changing the strength.

The adjustment step decays with accumulated feedback and never leaves the configured bounds.

## What is not stored

- raw audio;
- conversation content;
- transcripts;
- speaker embeddings;
- neural activations.

## Next validation

Before the preference can affect live audio, test a candidate mapping against RAW/DSP/AI on
the target phone and Galaxy Buds route. Compare speech preservation, artifacts, input drops,
output underruns and realtime factor.
