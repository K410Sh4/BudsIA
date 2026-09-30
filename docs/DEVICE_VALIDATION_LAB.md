# Device Validation Lab

## Purpose

The built-in lab turns the telemetry already produced by BudsIA into a reproducible short
technical test on the real phone/headset.

It is specifically designed to answer questions CI cannot answer, such as:

- Did Android really open the selected Galaxy Buds microphone?
- Which sample rate did the physical Bluetooth route expose?
- Can the selected neural model sustain realtime on this phone?
- Are native input or AI transport samples being dropped?
- Is the private output monitor underrunning or overrunning?
- Did the route disconnect?
- Did thermal or memory protection force a fallback?

## Quick procedure

> Route sanity check: selecting a Bluetooth A2DP output does **not** mean the headset microphone
> is active. Before a headset-microphone test, stop audio and explicitly select the Bluetooth
> HFP/SCO or BLE headset input. Android then owns the paired communication route.
> BudsIA requests a 16 kHz logical communication stream for this path so the lightweight
> GTCRN model is selected instead of running the 48 kHz high-resolution model over HFP.
> The live monitor uses a VoiceCommunication/Speech output stream and is enabled only after
> Android confirms the communication device.

1. Connect the intended headset if applicable.
2. Select the desired input/output while audio is stopped.
3. Select RAW, DSP or AI.
4. For AI, wait until the neural panel shows `RUNNING`.
5. Start **Device Validation Lab**.
6. Keep the route/configuration unchanged for 30 seconds.
7. Review the technical report.

During the run, BudsIA freezes processing/profile/performance setting mutations so all samples
describe one configuration.

## Key report fields

- actual input sample rate;
- active model;
- native input-drop delta;
- AI-input-drop delta;
- output-overrun delta;
- output-underrun delta;
- route-disconnect delta;
- input/output XRuns when Android exposes them;
- maximum neural RTF;
- sustained-realtime fallback only after a warm-up window and persistent moving + cumulative RTF pressure;
- peak estimated BudsIA process CPU;
- thermal/headroom state;
- governor fallback;
- Factory vs adaptive-candidate state.

## Reading the result

A PASS means the 30-second runtime sample did not violate the technical gates.

A WARN means the run remained usable but exposed a condition that should be investigated.

A FAIL means at least one technical safety/stability gate failed.

UNKNOWN means the platform or route did not expose enough data for that check.

None of these labels means “good audio quality”. Acoustic quality still requires listening or
an objective audio dataset with appropriate metrics.

## Recommended sequence

Run the short lab for:

- phone mic + DSP;
- phone mic + AI;
- Galaxy Buds mic + DSP;
- Galaxy Buds mic + AI;
- Factory neural;
- Candidate adaptive control.

Then run the longer stress protocol from `docs/DEVICE_VALIDATION.md`.
