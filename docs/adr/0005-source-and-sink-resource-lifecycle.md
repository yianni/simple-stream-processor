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

Failure precedence rules:

- If processing fails and `close` succeeds, raise processing failure.
- If processing succeeds and `close` fails, raise close failure.
- If both fail, raise processing failure and attach close failure as suppressed.

Cancellation rules:

- Cancellation is cooperative and best-effort.
- On cancellation, no new upstream pulls or task submissions are allowed.
- Framework still guarantees resource close execution.

Cancellation API contract:

- Pipeline execution APIs must return a cancellable handle (or equivalent) for asynchronous execution.
- Cancellation request must be idempotent.
- Cancellation completion must provide a terminal signal (`completed`, `failed`, or `cancelled`).
- Cancellation must propagate to managed source, async boundaries, and sink processing paths.

## Invariants

- Managed resources are closed exactly once from framework boundaries.
- Close is attempted on all terminal paths (success, failure, cancellation).
- Resource ownership is explicit and not shared implicitly across pipelines.

## Consequences

- Resource leaks are prevented by contract.
- Lifecycle behavior becomes testable and predictable.

## Alternatives Considered

- Leave lifecycle to user code only: rejected due to repeated leak-prone boilerplate.
- Fail hard on close error regardless of processing state: rejected due to loss of root-cause signal.

## Operational Metrics

- Resource open/close counts by operator.
- Close failure count and suppressed-exception count.
- Cancellation-to-close completion latency.

## Rollout and Migration

- Introduce managed wrappers additively; existing sink/source APIs continue to work.
- Promote managed APIs in examples and tests as preferred path.

## Non-Goals

- External transaction management
- Two-phase commit integration
