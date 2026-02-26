# ADR-0007: Watermark and Late Event Policy

- Status: Proposed
- Date: 2026-02-25

## Context

Event-time systems require a policy for watermark generation and late data.

## Decision

- Watermarks are monotonic and propagate downstream as control events.
- v1 late-event policy: drop events with timestamp `< current watermark`.
- Dropped late events must increment a late-event counter metric.
- Watermark cadence is configurable by operator policy.
- Initial watermark is `Long.MinValue` unless set by operator strategy.
- Regressing watermark values are ignored and counted as watermark regressions.

## Invariants

- Watermark values never decrease at any operator boundary.
- Late event definition is stable: `eventTs < currentWatermark`.
- Watermark propagation cannot reorder data events that were already emitted.
- Late-drop and watermark-regression counters are monotonic.

## Consequences

- Event-time closure remains deterministic.
- Late data handling is explicit and observable.

## Alternatives Considered

- Allow-lateness in v1: rejected to avoid early state-retention complexity.
- Side output for late data in v1: deferred until core path is stable.

## Operational Metrics

- Current watermark per operator.
- Late event drop count.
- Watermark regression count.

## Rollout and Migration

- Launch with monotonic watermark + late-drop behavior only.
- Add allow-lateness and side output as additive policies in later ADRs.

## Non-Goals

- Allowed lateness windows in v1
- Side outputs for late events in v1
