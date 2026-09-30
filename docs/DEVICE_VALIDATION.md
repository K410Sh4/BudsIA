# Physical-device validation gate

## In-app quick validation

BudsIA now includes a 30-second Device Validation Lab. Start the audio pipeline first, wait
for AI to reach RUNNING when applicable, then execute the lab.

It evaluates technical stability from telemetry only. PASS does not mean acoustic quality has
been proven. Use the longer procedure below for release validation.


CI validates source, native compilation, runtime packaging, model integrity and unit behavior.
It cannot prove acoustic quality, Bluetooth route behavior, thermals or end-to-end latency on
a specific phone/headset.

## Target validation sequence

### 1. Capture and lifecycle
- start/stop 20 consecutive sessions;
- background/foreground the app;
- verify the microphone indicator;
- verify no orphan capture after screen exit.

### 2. Phone route
- run RAW for 10 minutes;
- run DSP for 10 minutes;
- run AI for 10 minutes on a verified 48 kHz route;
- record xruns, drops, ring high-water marks, RTF and thermals.

### 3. Galaxy Buds route
- select the Buds microphone explicitly;
- confirm the actual native input device ID matches the selected route;
- record the actual sample rate exposed by Android;
- if 16 kHz, confirm GTCRN is selected;
- if 48 kHz, confirm DPDFNet2 48 kHz HR is selected;
- if another rate, confirm AI does not start and DSP remains active;
- verify communication output does not fall back to phone speaker.

### 4. Route loss
- disconnect Buds while idle;
- disconnect Buds while capturing;
- reconnect and start a new session;
- verify Android communication mode is restored after stop/error.

### 5. Neural stress
- keep AI active for at least 20 minutes;
- induce CPU load;
- confirm RTF watchdog falls back to DSP if realtime cannot be sustained;
- verify input/AI drops do not grow silently.

### 6. Acoustic A/B
Use identical consented material for RAW, DSP and AI.

Evaluate:
- speech preservation;
- stationary-noise suppression;
- transient artifacts;
- pumping;
- musical noise;
- clipping;
- intelligibility.

Do not promote a model or adaptive mapping solely from subjective loudness.

### 7. Adaptive profile candidate
For each environment profile:
- record factory behavior;
- record candidate mapped behavior;
- repeat with the same source material;
- compare audio quality and realtime telemetry;
- reject any mapping that increases instability;
- verify one-action rollback to factory behavior.

Until these checks are run on the physical target device, BudsIA must label device-specific
quality and latency as unvalidated.
