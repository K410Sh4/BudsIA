# BudsIA V3 — 2026 technology baseline

This file records technology choices separately from product claims.

## Realtime audio

Production path:

- Oboe 1.10.0
- AAudio on supported Android versions through Oboe
- C++20
- lock-free SPSC rings
- worker-thread DSP/inference
- Kotlin/JNI only for control and low-frequency telemetry

The app never performs JNI calls for every audio frame.

## Neural runtime — next phase

The planned model runtime is ONNX Runtime Mobile.

Execution providers will be selected through measured device benchmarks rather than brand
assumptions:

1. Quantized model: CPU baseline first.
2. Non-quantized model: XNNPACK baseline first.
3. NNAPI only when measured latency/energy/quality is better on the current device/model.

A faster accelerator is not assumed merely because a GPU/NPU exists.

Reference:
https://onnxruntime.ai/docs/tutorials/mobile/

## Model policy

The first neural filter will be causal and streaming.

Candidate families:

- compact convolutional/TCN recurrent mask estimator;
- RNNoise-class lightweight baseline;
- DeepFilterNet-inspired full-band enhancement experiments.

A large Transformer is not the default for a continuous mobile audio callback pipeline.

## Model promotion

Factory model is immutable.

Personal adaptation and candidate models are kept separately:

Factory -> Adaptive Profile -> Candidate -> Evaluation -> Promote/Rollback

No candidate is promoted without measured regression checks.

## Measurements

BudsIA distinguishes:

- MEASURED: produced from a device/runtime counter or monotonic timer;
- ESTIMATED: algorithmic estimate;
- UNKNOWN: cannot be measured with the current route/API.

Bluetooth codec latency must never be presented as measured unless it has actually been
measured.
