# ADR-0007 — ADPF headroom and Performance Hint integration

Status: Accepted for implementation  
Date: 2026-09-30

## Decision

BudsIA augments its measured AI performance governor with Android Dynamic Performance
Framework signals and scheduling hints.

The neural worker remains CPU-correctness-first and does not assume that a GPU/NPU is faster.

## Android signals

### Thermal headroom

`PowerManager.getThermalHeadroom()` is sampled outside the audio path.

BudsIA reads:
- current thermal headroom;
- 10-second thermal headroom forecast.

The values are labeled **ESTIMATED**.

A forecast approaching the severe-throttling threshold can trigger an early AI -> DSP
fallback before the thermal status itself becomes severe.

### CPU headroom

On Android 16 / API 36, BudsIA reads:

`SystemHealthManager.getCpuHeadroom(null)`

Android defines this as an estimate of available CPU capacity in the range 0..100, where 0
means no more CPU resources can be granted.

The call is performed by the non-realtime performance monitor, never by Oboe or the inference
deadline loop.

On older Android versions or unsupported devices, the value is **UNKNOWN**.

## Performance Hint session

The AI inference loop runs on one long-lived single-thread dispatcher.

When Android 12 / API 31+ exposes `PerformanceHintManager`, BudsIA creates one hint session
for that inference thread with the neural frame duration as the target work duration.

After each model invocation BudsIA reports the measured inference duration with
`reportActualWorkDuration()`.

On Android 15 / API 35+, the governor may set
`setPreferPowerEfficiency(true)` when thermal, battery or power-save conditions make
efficient scheduling preferable.

When CPU headroom is already very low, BudsIA deliberately keeps performance scheduling
priority instead of asking for power-efficient placement.

## Safety policy

Mandatory AI -> DSP:
- Android low-memory;
- thermal status SEVERE or worse;
- 10-second thermal forecast >= 0.98.

ECO recommendation:
- MODERATE thermal;
- thermal forecast >= 0.88;
- battery below configured ECO threshold while not charging.

BALANCED:
- CPU headroom <= 10% keeps performance priority;
- power-save mode, LIGHT thermal or forecast >= 0.72 prefer efficient scheduling.

These thresholds are BudsIA policy values, not Android guarantees.

## Telemetry overhead

UI telemetry cadence is reduced as resource pressure grows:

- MAX_QUALITY: 200 ms;
- BALANCED: 350 ms;
- ECO: 500 ms.

Audio and inference cadence are unchanged. This only reduces nonessential UI/telemetry work.

## Non-goals

This integration does not:
- overclock the phone;
- pin threads to big CPU cores;
- fabricate NPU usage;
- claim a faster model is active because the recommended tier changed;
- use NNAPI as a default accelerator.

## References

- https://developer.android.com/about/versions/16/features
- https://developer.android.com/reference/android/os/health/SystemHealthManager
- https://developer.android.com/reference/android/os/PowerManager#getThermalHeadroom(int)
- https://developer.android.com/reference/android/os/PerformanceHintManager
- https://developer.android.com/reference/android/os/PerformanceHintManager.Session
