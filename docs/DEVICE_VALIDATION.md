# Physical-device validation gate

CI validates source, native compilation, runtime packaging and pinned model integrity.
It cannot prove acoustic quality or end-to-end latency on a real phone/headset.

Before calling Phase C production-ready, validate on the target Android device:

## Capture
- start/stop 20 consecutive times;
- leave/re-enter the screen;
- verify microphone indicator behavior;
- verify no orphaned capture after app backgrounding.

## Native realtime
- 10-minute RAW session;
- 10-minute DSP session;
- record input drops/xruns/ring high-water marks;
- verify no progressive queue growth.

## Neural
- install model through the app;
- verify SHA-256 state becomes INSTALLED;
- run AI for at least 10 minutes;
- record average/max inference time and moving RTF;
- verify AI input drops remain zero under steady load;
- deliberately stress CPU and verify DSP fallback occurs if RTF cannot be sustained.

## Routes
- phone microphone + phone speaker (monitor must remain blocked);
- phone microphone + wired/USB headphones if available;
- phone microphone + Bluetooth output;
- Buds microphone only if Android exposes it as an input route;
- disconnect/reconnect route while running.

## Acoustic A/B
Use the same consented test material for RAW, DSP and AI.

Evaluate:
- speech preservation;
- stationary noise suppression;
- transient artifacts;
- pumping;
- musical noise;
- clipping;
- intelligibility.

Do not promote any model solely from subjective loudness.
