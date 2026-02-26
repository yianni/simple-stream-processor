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
- Ordered emission is strict: if element `i` is slower than `i+1`, downstream waits for `i` (head-of-line blocking is expected).

## Invariants

- Default output order equals input order.
- `parMap` never emits out of order in v1.
- Maximum concurrent tasks is bounded by `parallelism`.
- On cancellation, no new tasks are scheduled and pending outputs are dropped.

## Consequences

- Deterministic testability is preserved.
- Throughput scales with CPU-bound transforms while preserving semantics.
- Completion-order throughput optimizations are deferred.

## Alternatives Considered

- Completion-order `parMap`: rejected for v1 due to deterministic behavior requirements.
- Unbounded task submission: rejected due to memory and scheduler pressure risks.

## Operational Metrics

- `parMap` in-flight task count.
- Per-operator throughput and p95 processing latency.
- Queue wait time caused by head-of-line blocking.

## Rollout and Migration

- `parMap` launches as ordered-only in v1.
- Any future unordered mode must be additive and opt-in.

## Non-Goals

- Out-of-order emission mode in v1
- Dynamic auto-tuning of parallelism
