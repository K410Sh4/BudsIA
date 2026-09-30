# ADR-0003 — Explicit Bluetooth communication routing

Status: Accepted for implementation  
Date: 2026-09-30

## Problem

A connected headset being visible to Android does not guarantee that the app is recording from
its microphone. A media route can use Bluetooth output while input still comes from the phone.

BudsIA must therefore distinguish:

- device discovery;
- user route selection;
- Android communication-route preparation;
- actual device IDs reported by the opened native streams.

## Decision

For Android 12+ BudsIA uses the modern communication-device API for Bluetooth microphone
routes.

Flow:

1. user selects an input/output device while audio is stopped;
2. if the selected input is Bluetooth HFP/SCO or BLE headset, the app requires
   `BLUETOOTH_CONNECT`;
3. the app sets `AudioManager.MODE_IN_COMMUNICATION`;
4. the app calls `AudioManager.setCommunicationDevice()`;
5. the chosen device IDs are passed to the Oboe stream builder;
6. the native input stream prefers `VoiceCommunication`, then
   `VoiceRecognition`, then `Unprocessed`;
7. the UI reports the device ID that the opened stream actually returned;
8. when the session ends, BudsIA calls `clearCommunicationDevice()` and restores
   `MODE_NORMAL`.

## Why

Android documents `setCommunicationDevice()` as the replacement for deprecated
Bluetooth SCO routing APIs in communication use cases. It gives the app an explicit way to
select a Bluetooth headset microphone instead of assuming the system default route is correct.

## Permissions

Declared:

- `RECORD_AUDIO`
- `MODIFY_AUDIO_SETTINGS`
- `BLUETOOTH_CONNECT` on Android 12+
- legacy Bluetooth declarations capped at API 30

The app does not request Bluetooth scan permission because this phase does not scan for new
nearby peripherals. It only works with audio devices already exposed by Android.

## Android 11

The explicit Bluetooth communication route is intentionally not emulated with deprecated SCO
APIs. On Android 11 the app fails that explicit route request with a clear message instead of
silently claiming success.

## Validation status

Build/CI validation does not prove that a specific Galaxy Buds model exposes its microphone on
a given phone firmware.

Physical-device validation remains required for:

- Galaxy Buds HFP/SCO input;
- BLE headset input where supported;
- route loss and reconnection;
- input/output sample-rate changes;
- DSP/AI behavior on reduced-bandwidth Bluetooth microphone routes.
