# ADR-0001 — Native realtime audio core

Status: Accepted for BudsIA V3 development  
Date: 2026-09-30

## Decision

The production live-audio path uses C++ through the Android NDK and Oboe 1.10.0.

Kotlin remains responsible for lifecycle, UI, route descriptions, state exposure and control.
Audio callbacks, lock-free buffering and deterministic DSP remain native.

## Why

Android's current low-latency guidance recommends Oboe, low-latency performance mode,
exclusive sharing when available, data callbacks, avoiding blocking work inside callbacks and
using the device's native sample rate when possible.

BudsIA therefore does not force 48 kHz on the native stream. It opens the stream without a
sample-rate request and records the actual rate exposed by the route.

## Toolchain pin

- Android API: 36
- Oboe: 1.10.0
- NDK: 29.0.14206865
- CMake: 3.22.1
- C++: 20

NDK r30 is the current Android LTS, but Oboe 1.10.0 has a reported compatibility problem
with NDK r30 headers as of August 2026. The project pins r29 until the upstream compatibility
issue is resolved and verified in CI.

This is an explicit temporary compatibility pin, not a claim that r29 is newer than r30.

## Realtime contract

The Oboe callbacks may only:

- move samples through SPSC ring buffers;
- fill missing output samples with zero;
- increment atomics;
- record bounded timing information.

They must not:

- allocate memory;
- perform model inference;
- call Kotlin/Java;
- perform file/network I/O;
- wait on mutexes;
- sleep.

DSP and future neural inference run on a dedicated worker thread.

## Failure behavior

Input is mandatory. Output monitoring is optional.

If output cannot be opened, microphone analysis continues and the UI reports output as
unavailable.

If output drops, monitoring is disabled without stopping input analysis.

If input drops, the engine moves to ERROR and the UI keeps STOP available.

## Monitoring safety

Live monitor output is only enabled by the UI when the actual output device is categorized as
a private route such as headphones, headset, USB audio or Bluetooth audio.

This prevents accidental speaker-to-microphone feedback loops by default.

## Privacy

The native engine processes PCM in memory only.
The live screen stops capture on lifecycle ON_STOP.
No background microphone service is introduced by this decision.

## References

- https://developer.android.com/games/sdk/oboe/low-latency-audio
- https://github.com/google/oboe
- https://github.com/google/oboe/issues/2406
- https://developer.android.com/ndk/downloads
