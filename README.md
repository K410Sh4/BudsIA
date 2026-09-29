# BudsIA

**BudsIA** is a local-first Android application for live conversation assistance.

The long-term goal is to combine:

- on-device speech recognition;
- local language identification;
- offline translation;
- speaker diarization;
- speaker identification using local voice embeddings;
- explainable fallacy / persuasion analysis;
- local LLM reasoning;
- a private conversation timeline.

The application is designed so that unsupported or unfinished AI features are never presented as working.

## Current version

**0.1.0-alpha**

Implemented now:

- Kotlin
- Jetpack Compose
- Material 3
- Clean/MVVM-oriented architecture
- Hilt
- Room
- DataStore
- Coroutines / Flow / StateFlow
- Android on-device SpeechRecognizer capability detection
- local speech recognition when Android exposes an on-device recognizer
- contextual microphone permission
- ML Kit bundled language identification
- ML Kit on-device translation
- explicit translation-model downloads
- strict offline live-session mode
- local Room timeline
- in-memory session timeline
- explainable rule-based discourse analysis
- evidence + confidence for every detected signal
- premium dark UI
- GitHub Actions APK build
- unit tests

## Important transparency rules

BudsIA V0.1 does **not** claim that it can read thoughts, hidden motives, or a person's true intent.

Current analysis can surface linguistic patterns such as:

- artificial urgency
- guilt pressure
- possible false dilemma
- ad hominem
- appeal to popularity
- threat/coercion language
- scarcity pressure
- implied requests

Each result includes:

- category
- confidence
- matched evidence
- explanation

These are linguistic cues, not proof of malicious intent.

## Offline architecture

Live sessions follow:

```text
Microphone
   ↓
Android on-device SpeechRecognizer
   ↓
ML Kit Language ID
   ↓
ML Kit Translation
   ↓
Explainable local analysis
   ↓
Timeline
```

Live sessions never automatically download a model.

Translation-model downloads only start when the user explicitly taps **Download** in the Models screen.

The app does not intentionally use a cloud speech fallback in V0.1. If Android does not expose an on-device SpeechRecognizer, the feature is marked **UNAVAILABLE**.

## Current AI capability states

### READY when supported by device

- On-device SpeechRecognizer
- Language identification
- Translation after required models are installed

### EXPERIMENTAL

- Explainable discourse analysis

### PLANNED

- speaker diarization using sherpa-onnx
- local speaker embeddings / speaker identification
- Gemini Nano capability detection
- LiteRT-LM local LLM fallback
- context-aware intent analysis
- contradiction tracking
- long-context conversation reasoning
- local feedback/personalization layer

## Translation

BudsIA uses ML Kit Translation.

Initial model-management buttons:

- Portuguese
- English
- Spanish

The architecture supports adding more supported ML Kit languages later.

## Privacy

Current behavior:

- microphone permission is requested only when Live Conversation starts;
- raw microphone audio is not saved;
- recording is not started silently in background;
- transcript persistence can be disabled;
- live-session model downloads are disabled;
- speaker identity is not implemented yet, so no voice profiles are stored.

## Screens

### Home

Shows the real status of each AI subsystem:

- READY
- DOWNLOAD
- UNAVAILABLE
- EXPERIMENTAL
- PLANNED

### Live

- local microphone session
- partial transcript
- final transcript
- language detection
- translation
- discourse-analysis signals
- session timeline
- explicit STOP control

### Timeline

Displays locally saved conversation segments.

### Models

Shows installed translation models and allows explicit downloads.

### Privacy

Controls:

- strict offline sessions
- transcript persistence
- translation target

## Development roadmap

### V0.1 — current

Speech → language ID → translation → explainable rule analysis → timeline.

### V0.2

- local VAD
- speaker diarization
- speaker embeddings
- speaker profile enrollment
- speaker A/B/name timeline
- audio-device routing improvements

### V0.3

- Gemini Nano capability probe
- LiteRT-LM fallback
- quantized Gemma model management
- local intent/fallacy/persuasion reasoning
- confidence calibration
- context-window analysis

### V0.4

- user corrections
- local personalization memory
- vocabulary correction
- speaker-centroid learning
- encrypted voice embeddings
- session summaries

## Build

Requirements:

- JDK 17
- Gradle 9.6
- Android SDK 36
- Android build tools 36.0.0

Commands:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions runs unit tests before assembling and uploading the APK.

## Package

```text
com.k410sh4.budsia
```

## Current project structure

```text
app/src/main/java/com/k410sh4/budsia/
├── core/
│   ├── analysis/
│   ├── language/
│   └── speech/
├── data/
│   ├── database/
│   ├── repository/
│   └── settings/
├── di/
├── domain/
│   └── model/
├── feature/
│   └── main/
└── ui/
    └── theme/
```

## Safety / scope

BudsIA is an assistance tool. It should not be used to make definitive claims about another person's motives, honesty, mental state, or hidden intentions.

Future model-based analysis must preserve this rule by separating:

- observed wording;
- model inference;
- confidence;
- alternative interpretations;
- insufficient-evidence cases.
