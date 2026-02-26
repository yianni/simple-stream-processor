# ADR-0002: Ordering and Parallelism Contract

- Status: Accepted
- Date: 2026-02-25

## Context

Parallel execution can increase throughput but creates ambiguity around ordering and determinism.

## Decision

- Default ordering is input order across the pipeline.
- `parMap(parallelism)` executes work concurrently but emits results in input order.
- `parallelism` must be `> 0`; otherwise fail fast with `IllegalArgumentException`.
- In-flight work is bounded by configured parallelism.

## Consequences

- Deterministic testability is preserved.
- Throughput scales with CPU-bound transforms while preserving semantics.
- Completion-order throughput optimizations are deferred.

## Non-Goals

- Out-of-order emission mode in v1
- Dynamic auto-tuning of parallelism
