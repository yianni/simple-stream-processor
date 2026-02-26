# ADR-0005: Source and Sink Resource Lifecycle

- Status: Accepted
- Date: 2026-02-25

## Context

Resource ownership and close semantics are currently incomplete and asymmetric.

## Decision

- Introduce managed resource wrappers for both source and sink paths.
- Resources are always closed via `finally` semantics on success, failure, and cancellation.
- Close operations are idempotent at framework boundaries.
- Failures during `close` are surfaced after processing failure precedence rules.

## Consequences

- Resource leaks are prevented by contract.
- Lifecycle behavior becomes testable and predictable.

## Non-Goals

- External transaction management
- Two-phase commit integration
