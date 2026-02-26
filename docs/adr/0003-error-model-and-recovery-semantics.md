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

## Consequences

- Failure behavior is explicit and deterministic.
- Callers can choose fail-fast or local recovery.

## Non-Goals

- Retry orchestration policy
- Dead-letter queue integration
