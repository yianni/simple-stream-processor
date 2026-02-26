# Architecture Decision Records

These ADRs define the baseline contracts for stream semantics and implementation order.

## Status

- Proposed: 0001-0007

## Engineering Baseline

- Scala: 2.13.x
- JDK: 17
- Required validation: deterministic unit tests + concurrency stress tests for boundary/parallel operators

## Index

- [0001 Operator Taxonomy and API Boundaries](0001-operator-taxonomy-and-api-boundaries.md)
- [0002 Ordering and Parallelism Contract](0002-ordering-and-parallelism-contract.md)
- [0003 Error Model and Recovery Semantics](0003-error-model-and-recovery-semantics.md)
- [0004 Backpressure Contract](0004-backpressure-contract.md)
- [0005 Source and Sink Resource Lifecycle](0005-source-and-sink-resource-lifecycle.md)
- [0006 Windowing Semantics](0006-windowing-semantics.md)
- [0007 Watermark and Late Event Policy](0007-watermark-and-late-event-policy.md)
- [Error and Cancellation Matrix](error-and-cancellation-matrix.md)
- [Event-Time Example](event-time-example.md)
