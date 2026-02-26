# Changelog

## v0.1.0 - 2026-02-26

- Added ADR framework and accepted contracts for operator taxonomy, parallelism, error model, backpressure, resource lifecycle, windowing, and watermarks.
- Implemented fail-fast error propagation with recovery operators (`recover`, `recoverWith`) across stream and node APIs.
- Added ordered `parMap` with bounded parallelism and async boundaries with bounded queue backpressure.
- Added managed source/sink lifecycle handling and cancellable async execution handles.
- Added count windows, event-time windows, watermark emission, late-event drop handling, and watermark regression handling.
- Added runtime metrics and stress validation harness for backpressure behavior.
