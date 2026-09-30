# AI performance monitoring

## Signals

The live AI Performance card shows:

- recommended tier;
- Android thermal status;
- current thermal headroom estimate;
- 10-second thermal headroom forecast;
- Android 16 CPU headroom estimate when supported;
- battery percentage;
- charging state;
- power-save mode;
- available memory;
- BudsIA process CPU estimate;
- Performance Hint session state;
- power-efficiency scheduling hint state.

Each metric is labeled with its measurement type where applicable.

## Measurement meaning

- **MEASURED**: direct platform/counter value.
- **ESTIMATED**: derived estimate or platform-provided estimate.
- **UNKNOWN**: unavailable on the current Android/device/runtime.

Thermal headroom and Android 16 CPU headroom are estimates, not temperature or utilization
percentages.

BudsIA does not invent Celsius temperature, remaining battery runtime, Bluetooth codec
latency or NPU utilization.

## Automatic protection

Mandatory fallback:
- thermal SEVERE or worse -> AI to DSP;
- 10-second thermal forecast >= 0.98 -> AI to DSP;
- Android low-memory -> AI to DSP.

Battery fallback is a user option and remains OFF by default.

When enabled, the battery stop threshold can be configured between 5% and 30%.

## ADPF Performance Hint

The neural inference worker runs on one stable long-lived thread.

On supported Android versions BudsIA creates a Performance Hint session using the neural frame
duration as the periodic deadline and reports actual inference duration after every model
call.

On Android 15+ the governor may request power-efficient scheduling for ECO/BALANCED
conditions.

Low CPU headroom does not automatically request power-efficient placement, because the
inference deadline still needs enough CPU resources.

## Tier recommendation versus active model

MAX_QUALITY, BALANCED and ECO are governor recommendations.

They are **not** claims that a different neural model is running.

The actual model remains selected by exact audio-route sample rate:

- 48 kHz -> verified DPDFNet2 48 kHz HR when installed;
- 16 kHz -> verified GTCRN Simple when installed;
- unsupported rate -> DSP.

## Telemetry load

BudsIA lowers nonessential UI telemetry frequency under resource pressure:

- MAX_QUALITY: 5 Hz;
- BALANCED: about 2.9 Hz;
- ECO: 2 Hz.

Inference itself is never skipped solely to make telemetry look fast.

## Future acceleration

CPU remains the correctness baseline.

A vendor accelerator or alternate execution provider can only be promoted after measured
latency, power, stability and acoustic comparisons on the target phone.
