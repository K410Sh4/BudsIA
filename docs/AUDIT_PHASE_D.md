# Phase D audit — adaptive profiles

## Scope

Phase D adds a safe personalization layer. It does not modify the native audio callback,
Bluetooth routing, model integrity logic or factory neural weights.

## Created components

### `core/ai/adaptation/AdaptiveAudioProfile.kt`
Pure domain model and fixed bounds.

Dependency direction: none.

### `core/ai/adaptation/AdaptiveTuningEngine.kt`
Pure deterministic update rule.

Input: current profile + explicit user feedback.  
Output: new versioned profile.

No Android dependency. Unit-testable in isolation.

### `core/ai/adaptation/AdaptiveProfileRepository.kt`
Persistence contract exposed to features.

### `core/ai/adaptation/PreferencesAdaptiveProfileRepository.kt`
AndroidX DataStore implementation.

Responsibilities:
- transactional persistence;
- environment selection;
- schema check;
- corruption recovery;
- bounded value decoding.

It stores no microphone samples or conversation content.

### `di/CoroutineModule.kt`
Process-lifetime IO scope for persistence infrastructure.

### `di/AdaptationModule.kt`
Hilt bindings only.

## Modified integration points

### `AudioFocusUiState.kt`
Adds the current immutable profile snapshot.

### `AudioFocusViewModel.kt`
Collects profile state and forwards explicit user actions to the repository.

It preserves the existing hardened neural startup path: realtime capture still boots in DSP
before AI is prepared.

### `AudioFocusScreen.kt`
Adds profile/environment controls and feedback UI.

Slider writes are committed only when the gesture finishes; DataStore is not written for
every pointer movement.

## Deliberate non-integration

The learned preference is not yet multiplied into neural PCM output.

Reason: the streaming denoiser can have algorithmic delay and its output length can differ
from an input chunk. Naïve dry/wet mixing would risk comb filtering and phase artifacts.

This is a safety decision, not an unfinished hidden feature.

## Rollback

Removing the Phase D integration leaves Phase C audio behavior unchanged because:

- neural factory model is immutable;
- native audio engine contracts are unchanged;
- profile state is outside the audio callback;
- no profile value is currently required to start RAW, DSP or AI.

## Next surgical seam

Phase E should introduce an alignment-safe evaluation adapter behind its own interface. Only a
candidate that beats the current behavior under explicit evaluation may consume
`preferredEnhancementStrength` automatically.
