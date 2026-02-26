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
- On downstream cancellation, blocked producers are unblocked via boundary shutdown signaling.

## Invariants

- Queue capacity is fixed for the lifetime of a boundary in v1.
- No unbounded buffering at async boundaries.
- Producer and consumer termination paths cannot deadlock.
- Boundary forwards terminal completion and errors exactly once.

## Consequences

- Memory use is bounded by design at boundaries.
- Throughput may reduce under slow consumers, which is expected.

## Alternatives Considered

- Drop-on-full buffer: rejected for v1 due to implicit data loss.
- Unbounded queue: rejected due to memory blow-up risk.

## Operational Metrics

- Boundary queue depth (current/max).
- Producer blocked duration at boundaries.
- Boundary-induced cancellation/unblock count.

## Rollout and Migration

- `asyncBoundary` remains explicit and opt-in.
- Default pipeline behavior remains single-threaded unless boundary is added.

## Non-Goals

- Drop-on-overflow mode in v1
- Adaptive buffer resizing in v1
