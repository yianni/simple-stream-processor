# ADR-0003: Error Model and Recovery Semantics

- Status: Accepted
- Date: 2026-02-25

## Context

Transform and terminal failures must follow one consistent model.

## Decision

- Stream processing uses a fail-fast error channel.
- Operator failures are represented in-stream and propagate downstream.
- Terminal operations (`toList`, `fold`, `foreach`, sinks) fail on unhandled stream errors.
- Recovery operators:
  - `recover`: map matching error to a fallback value and continue from recovery point
  - `recoverWith`: map matching error to a fallback stream

## Invariants

- Unhandled operator failure is terminal for that stream path.
- Recovery only applies when the partial function matches the thrown error.
- Recovery does not reorder elements.
- Terminal consumers (`toList`, `fold`, `foreach`, sink run) throw when an unhandled stream error reaches them.

## Consequences

- Failure behavior is explicit and deterministic.
- Callers can choose fail-fast or local recovery.

## Alternatives Considered

- Exception-only propagation with no stream error state: rejected due to poor composability.
- Best-effort continue after unhandled errors: rejected due to hidden data loss risk.

## Operational Metrics

- Unhandled error count by operator type.
- Recovery invocation count by operator.
- Sink failure count and failure class distribution.

## Rollout and Migration

- Existing operators adopt fail-fast propagation first.
- Recovery operators are additive and opt-in.

## Non-Goals

- Retry orchestration policy
- Dead-letter queue integration
