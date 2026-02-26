# Simple Stream Processor

This project demonstrates a stream processing framework implemented in Scala with explicit contracts for ordering,
errors, backpressure, resource lifecycle, and event-time semantics.

## Features

* Core stream operators: `map`, `filter`, `flatMap`, `recover`, `recoverWith`, `grouped`.
* Node pipeline API: fluent `Source -> transforms -> Sink` composition.
* Ordered parallel processing: `parMap(parallelism)` with bounded in-flight work.
* Backpressure boundaries: `asyncBoundary(bufferSize)` using bounded queues.
* Managed resources: `ManagedSource` and `ManagedSink` with close-on-success/failure/cancel behavior.
* Windowing and event time: count windows, watermark emission, event-time windows, late-event drop policy.
* Async execution control: cancellable `ExecutionHandle` with outcome states.
* Runtime metrics: queue depth, producer blocked time, late drops, watermark regressions, errors, close failures.

## Example Usage

```scala
val sink = Source[Int](Stream.fromList((1 to 100).toList)).withName("source")
  .parMap(4)(_ * 2)
  .filter(_ % 3 == 0)
  .asyncBoundary(16)
  .toSink((acc: Int, i: Int) => acc + i, 0)
  .withName("sink")
```

This pipeline applies ordered parallel mapping, filters values, introduces a bounded async boundary for backpressure,
and aggregates the final stream in a sink.

## Testing

Tests are written using ScalaTest.

Run unit tests:

```bash
sbt test
```

Run calibrated backpressure stress validation (30s target):

```bash
sbt -Dsbt.supershell=false "Test / runMain SimpleStreamProcessor.BackpressureStressValidation 30000"
```

## Current Scope Notes

This implementation is single-process and in-memory. It is designed to validate pipeline semantics and concurrency
contracts, not to provide distributed execution or exactly-once guarantees.

## Architecture Decisions

Design contracts are tracked as ADRs in `docs/adr/`.
