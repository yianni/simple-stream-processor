# ADR Evidence Matrix

| ADR | Invariant Area | Evidence |
| --- | --- | --- |
| 0001 | Operator taxonomy exists across stateless/stateful/boundary/terminal operators | `src/main/scala/SimpleStreamProcessor/Node.scala`, `src/test/scala/SimpleStreamProcessor/SimpleStreamProcessorTest.scala` |
| 0002 | Ordered `parMap`, bounded parallelism, invalid parallelism fail-fast | `src/main/scala/SimpleStreamProcessor/Stream.scala`, `src/test/scala/SimpleStreamProcessor/SimpleStreamProcessorTest.scala` (`Stream parMap preserves input order`, `Stream parMap fails fast on invalid parallelism`) |
| 0003 | Fail-fast error propagation and recovery operators | `src/main/scala/SimpleStreamProcessor/Stream.scala`, `src/main/scala/SimpleStreamProcessor/Node.scala`, tests (`Stream recover converts failures into values`, `Node recoverWith allows stream fallback`) |
| 0004 | Bounded async boundary and backpressure invariants | `src/main/scala/SimpleStreamProcessor/Node.scala`, `src/test/scala/SimpleStreamProcessor/SimpleStreamProcessorTest.scala` (`Async boundary queue depth stays within configured capacity`) and `src/test/scala/SimpleStreamProcessor/StressInvariantTest.scala` |
| 0005 | Managed source/sink close semantics and precedence | `src/main/scala/SimpleStreamProcessor/Node.scala`, tests (`Managed sink preserves processing failure and suppresses close failure`, `Managed source preserves processing failure and suppresses close failure`) |
| 0006 | Count windows and event-time window closure semantics | `src/main/scala/SimpleStreamProcessor/Node.scala`, tests (`Count windows split stream into fixed-size batches`, `Watermarks are emitted and event-time windows close`) |
| 0007 | Late-event drop + watermark regression policy | `src/main/scala/SimpleStreamProcessor/Node.scala`, tests (`Event-time windows drop late records and ignore regressing watermarks`) |

## Stress and Baseline Commands

- `sbt -Dsbt.supershell=false "Test / testOnly SimpleStreamProcessor.StressInvariantTest"`
- `sbt -Dsbt.supershell=false "Test / runMain SimpleStreamProcessor.BackpressureStressValidation 10000"`
- `sbt -Dsbt.supershell=false "Test / runMain SimpleStreamProcessor.PerformanceBaselineReport"`
