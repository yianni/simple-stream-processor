# Practical Examples

This page contains copy-paste examples for common usage patterns.

## Run Commands

```bash
# End-to-end practical demo app
sbt -Dsbt.supershell=false "runMain SimpleStreamProcessor.PracticalUseCases"

# Unit tests
sbt -Dsbt.supershell=false test
```

## 1) Ordered Parallel Enrichment

```scala
import scala.concurrent.ExecutionContext.Implicits.global

case class Transaction(userId: String, amount: Int)

val riskyTxnCount = Source(Stream.fromList(List(
  Transaction("u-1", 25),
  Transaction("u-2", 2000),
  Transaction("u-3", 75),
  Transaction("u-4", 4100)
)))
  .parMap(4)(txn => if (txn.amount >= 1000) 1 else 0)
  .toSink((acc: Int, i: Int) => acc + i, 0)
  .run(Stream.Empty)

println(riskyTxnCount) // expected: 2
```

## 2) Count-Window Error Bursts

```scala
case class LogLine(level: String, message: String)

val errorBursts = Source(Stream.fromList(List(
  LogLine("INFO", "boot"),
  LogLine("ERROR", "db timeout"),
  LogLine("INFO", "retry ok"),
  LogLine("ERROR", "cache miss"),
  LogLine("ERROR", "queue full")
)))
  .map(log => if (log.level == "ERROR") 1 else 0)
  .windowByCount(3)
  .map(_.sum)
  .run(Stream.Empty)
  .toList

println(errorBursts) // expected: List(1, 2)
```

## 3) Event-Time Windows with Watermarks

```scala
import SimpleStreamProcessor.NodeSyntax._

val windows = Source(Stream.fromList(List(
  Timestamped("checkout", 1000L),
  Timestamped("search", 1300L),
  Timestamped("checkout", 6200L),
  Timestamped("search", 7200L)
)))
  .withWatermarks(emitEveryN = 2)
  .windowByEventTime(windowSizeMs = 5000L)
  .run(Stream.Empty)
  .toList

println(windows.size) // expected: 1
```

## 4) Cancellable Iterator Consumption

```scala
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

val cancellable = Source(Stream.fromList((1 to 2000).toList))
  .asyncBoundary(16)
  .map { i =>
    Thread.sleep(1)
    i
  }
  .runCancellableIterator(Stream.Empty, bufferSize = 8)

val sample = cancellable.iterator.take(5).toList
cancellable.cancel()
val outcome = Await.result(cancellable.outcome, 2.seconds)

println(sample)   // expected: List(1, 2, 3, 4, 5)
println(outcome)  // expected: ExecutionCancelled
```

## 5) Managed Sink Resource Lifecycle

```scala
final class AuditWriter extends AutoCloseable {
  private val buffer = scala.collection.mutable.ListBuffer.empty[String]
  private var closed = false

  def append(line: String): Unit = {
    if (closed) throw new IllegalStateException("writer is closed")
    buffer += line
  }

  def entries: List[String] = buffer.toList

  override def close(): Unit = {
    closed = true
  }
}

var capturedWriter: AuditWriter = null
val auditSink = Source(Stream.fromList(List("created", "queued", "processed")))
  .toManagedSink(() => {
    val writer = new AuditWriter
    capturedWriter = writer
    writer
  })((writer, status) => writer.append(s"order:$status"))

auditSink.run(Stream.Empty)

println(capturedWriter.entries)
// expected: List("order:created", "order:queued", "order:processed")
```

## 6) Failure Recovery with `recoverWith`

```scala
val recovered = Source(Stream.fromList(List(1, 0, 2)))
  .map(i => 10 / i)
  .recoverWith {
    case _: ArithmeticException => Stream.fromList(List(99, 100))
  }
  .run(Stream.Empty)
  .toList

println(recovered) // expected: List(10, 99, 100)
```
