# ADR-0006: Windowing Semantics

- Status: Proposed
- Date: 2026-02-25

## Context

Window operators require explicit closure and emission rules to avoid nondeterminism.

## Decision

- v1 supports:
  - Tumbling count windows
  - Tumbling event-time windows
- Count window emits in input order and may emit a final partial window.
- Event-time window closes when `windowEnd <= watermark`.
- Window size parameters must be `> 0`; otherwise fail fast.
- End-of-stream without a final watermark does not auto-close incomplete event-time windows in v1.
- For bounded streams, callers should emit a terminal watermark (`Long.MaxValue`) if all remaining windows must flush.

## Invariants

- Count windows preserve input ordering.
- Event-time window closure is watermark-driven only.
- Window assignment is deterministic for a given timestamp and window size.
- Validation failures (invalid size) fail fast before processing starts.

## Consequences

- Window emission is deterministic.
- Event-time behavior is aligned with watermark progression.

## Alternatives Considered

- Auto-close event-time windows at end-of-stream without watermark: rejected to preserve strict watermark semantics.
- Sliding/session windows in v1: rejected to keep state model small and testable.

## Operational Metrics

- Open window count over time.
- Window emit count and average window size.
- Watermark-to-window-close lag.

## Rollout and Migration

- Ship count windows first, then event-time windows under the same contract.
- Keep advanced trigger policies out of v1 and document as future extensions.

## Non-Goals

- Session windows
- Sliding windows
- Trigger customizations in v1
