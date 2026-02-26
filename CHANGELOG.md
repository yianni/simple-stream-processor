# Changelog

## v0.2.0 - 2026-02-26

- Completed P2 stabilization with explicit stress invariant suite (`StressInvariantTest`) and reusable stress harness.
- Added ADR evidence matrix (`docs/adr/evidence-matrix.md`) and P2 execution board (`docs/p2-execution-board.md`).
- Added performance baseline runner (`PerformanceBaselineReport`) with machine-readable throughput/latency output.
- Strengthened CI quality gates with explicit stress suite and performance baseline smoke execution.
- Updated README operational commands for stress and baseline workflows.

## v0.1.2 - 2026-02-26

- Added pipeline-scoped metrics collectors with per-execution snapshots on async handles.
- Added cancellable iterator execution path (`runCancellableIterator`) with cancellation outcome integration.
- Added Node-level `recoverWith` operator support in the pipeline API.
- Upgraded CI stress gate from smoke-level to calibrated 10s backpressure validation.
- Added regression tests for metric scoping and cancellable iterator behavior.

## v0.1.1 - 2026-02-26

- Hardened `parMap` execution with cancellation-aware batch scheduling and removed indefinite wait behavior.
- Hardened async boundary liveness with bounded offer loops, stall detection, and downstream-consumer signaling.
- Strengthened managed resource precedence semantics (processing failure remains primary, close failures are suppressed and metered).
- Added Node-level `recoverWith` to align API behavior with ADR recovery semantics.
- Added CI workflow on GitHub Actions (JDK 17 + `sbt test` + backpressure stress smoke).
- Expanded regression coverage for cancellation, boundary thread cleanup, and resource precedence paths.

## v0.1.0 - 2026-02-26

- Added ADR framework and accepted contracts for operator taxonomy, parallelism, error model, backpressure, resource lifecycle, windowing, and watermarks.
- Implemented fail-fast error propagation with recovery operators (`recover`, `recoverWith`) across stream and node APIs.
- Added ordered `parMap` with bounded parallelism and async boundaries with bounded queue backpressure.
- Added managed source/sink lifecycle handling and cancellable async execution handles.
- Added count windows, event-time windows, watermark emission, late-event drop handling, and watermark regression handling.
- Added runtime metrics and stress validation harness for backpressure behavior.
