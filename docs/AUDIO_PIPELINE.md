# Audio Pipeline

## Native capture

The live production path uses Oboe.

Requested:
- low-latency performance mode;
- exclusive sharing first, shared fallback;
- mono float PCM;
- unprocessed input first, voice-recognition fallback.

The engine intentionally does **not** force a sample rate. It queries and reports the actual
rate opened by Android.

## Callback rule

Input/output callbacks only perform bounded realtime-safe work:

```
callback
  -> copy through SPSC ring
  -> atomics
  -> return
```

No model inference, Kotlin call, file I/O or mutex is allowed in the data callback.

## Processing worker

The worker consumes approximately 10 ms blocks based on the actual input sample rate.

Current modes:

- RAW: samples pass unchanged;
- DSP: stateful 70 Hz high-pass filter.

The future neural processor plugs into this worker, not the audio callback.

## Output monitor

Output is optional.

Processed audio may be monitored only when Kotlin confirms the actual output route is a
private route (headphones/headset/USB/Bluetooth) and input/output sample rates match.

The monitor is disabled for a built-in speaker by default to reduce acoustic feedback risk.

## Measurements

Current native measurements:

- input/output device IDs;
- actual sample rates;
- exclusive/shared mode;
- input and output callback counts;
- input drops;
- output overruns;
- output underruns;
- xruns when the API exposes them;
- max callback execution time;
- current/max processor time;
- ring-buffer high-water marks;
- disconnect count;
- RMS;
- peak;
- DC offset;
- clipping ratio.

No Bluetooth codec latency or acoustic round-trip latency is invented.

## Persistence

Raw audio is not saved.

## Explicit route preparation

Before the native stream starts, the selected route is prepared on Android.

For a normal phone, wired or USB input, BudsIA passes the selected device ID directly to
Oboe.

For a Bluetooth communication microphone on Android 12+:

1. BudsIA requires `BLUETOOTH_CONNECT`;
2. it preserves the pre-session `AudioManager.mode`;
3. it sets `MODE_IN_COMMUNICATION`;
4. it calls `setCommunicationDevice()`;
5. the Oboe input builder prefers `VoiceCommunication`;
6. Oboe opens the selected input device;
7. the app reports the actual opened device ID, not the requested label;
8. on session end, the communication device is cleared and the previous audio mode is restored.

When a Bluetooth microphone is active, Android owns the paired communication output route.
BudsIA therefore leaves the native output device on the system communication route rather
than trying to combine HFP microphone input with a separately forced A2DP output.

