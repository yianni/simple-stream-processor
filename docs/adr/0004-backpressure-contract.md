# ADR-0004: Backpressure Contract

- Status: Accepted
- Date: 2026-02-25

## Context

Without bounded buffers, producer-consumer imbalance risks unbounded memory growth.

## Decision

- Backpressure boundary is explicit via `asyncBoundary(bufferSize)`.
- Boundary uses bounded queue semantics.
- When queue is full, producer blocks until capacity is available.
- Buffer size must be `> 0`; invalid values fail fast.

## Consequences

- Memory use is bounded by design at boundaries.
- Throughput may reduce under slow consumers, which is expected.

## Non-Goals

- Drop-on-overflow mode in v1
- Adaptive buffer resizing in v1
