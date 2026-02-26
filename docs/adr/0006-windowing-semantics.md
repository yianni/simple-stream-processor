# ADR-0006: Windowing Semantics

- Status: Accepted
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

## Consequences

- Window emission is deterministic.
- Event-time behavior is aligned with watermark progression.

## Non-Goals

- Session windows
- Sliding windows
- Trigger customizations in v1
