# AI performance monitoring

## Signals

The live AI Performance card shows:

- recommended tier;
- Android thermal status;
- battery percentage;
- charging state;
- available memory;
- BudsIA process CPU estimate.

Each metric is labeled through its measurement type.

## Automatic protection

Thermal SEVERE or worse and Android low-memory conditions force AI -> DSP.

Battery fallback is a user option, OFF by default.

When enabled, the threshold can be configured between 5% and 30%.

## Tier recommendation versus active model

MAX_QUALITY, BALANCED and ECO are recommendations derived from device conditions.

They are **not** claims that a different neural model is running.

The actual model remains selected by exact audio-route sample rate:

- 48 kHz -> verified DPDFNet2 48 kHz HR when installed;
- 16 kHz -> verified GTCRN Simple when installed;
- unsupported rate -> DSP.

This prevents thermal/battery policy from silently switching to a model that cannot process
the current route.

## Future acceleration

CPU remains the correctness baseline.

Any vendor accelerator or alternate execution provider must show measurable benefit on the
target phone without quality, stability or energy regression before promotion.
