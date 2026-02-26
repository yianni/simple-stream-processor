# ADR-0007: Watermark and Late Event Policy

- Status: Accepted
- Date: 2026-02-25

## Context

Event-time systems require a policy for watermark generation and late data.

## Decision

- Watermarks are monotonic and propagate downstream as control events.
- v1 late-event policy: drop events with timestamp `< current watermark`.
- Dropped late events must increment a late-event counter metric.
- Watermark cadence is configurable by operator policy.

## Consequences

- Event-time closure remains deterministic.
- Late data handling is explicit and observable.

## Non-Goals

- Allowed lateness windows in v1
- Side outputs for late events in v1
