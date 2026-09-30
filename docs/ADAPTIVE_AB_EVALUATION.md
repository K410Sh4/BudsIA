# Local adaptive A/B evaluation

## Goal

Turn subjective preference into auditable local evidence without pretending that a model has
objectively improved.

## Flow

```
AI running
  -> listen Factory
  -> listen Candidate
  -> choose Factory / Candidate / No difference
  -> store one local counter
  -> reset round
```

Both variants must be auditioned before the vote controls are enabled.

## Per-environment history

Results are isolated for:

- Geral
- Casa
- Rua
- Trabalho
- Carro

A preference in one environment does not automatically become evidence for another.

## Local assessment

After enough comparisons, BudsIA reports one of:

- Dados insuficientes
- Candidato preferido
- Factory preferido
- Preferência mista

The candidate preference rate ignores "Sem diferença" when calculating decisive preference,
while those responses still count toward total comparisons.

## Important limitation

This is a user-preference measurement, not PESQ, STOI, SI-SDR or another objective acoustic
quality metric.

BudsIA does not auto-promote the candidate based on this result.

## Next validation step

Combine this preference history with physical-device stability metrics collected during the
same candidate experiments: realtime factor, input drops, AI-input drops, output underruns,
clipping and thermal state.
