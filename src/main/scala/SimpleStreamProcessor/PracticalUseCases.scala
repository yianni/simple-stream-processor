package SimpleStreamProcessor

import SimpleStreamProcessor.NodeSyntax._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

object PracticalUseCases extends App {

  case class Transaction(userId: String, amount: Int)
  case class LogLine(level: String, message: String)

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

  val riskyTxnCount = Source(Stream.fromList(List(
    Transaction("u-1", 25),
    Transaction("u-2", 2000),
    Transaction("u-3", 75),
    Transaction("u-4", 4100),
    Transaction("u-5", 130)
  )))
    .parMap(4)(txn => if (txn.amount >= 1000) 1 else 0)
    .toSink((acc: Int, i: Int) => acc + i, 0)
    .run(Stream.Empty)

  val errorBursts = Source(Stream.fromList(List(
    LogLine("INFO", "boot"),
    LogLine("ERROR", "db timeout"),
    LogLine("INFO", "retry ok"),
    LogLine("ERROR", "cache miss"),
    LogLine("ERROR", "queue full"),
    LogLine("WARN", "slow call")
  )))
    .map(log => if (log.level == "ERROR") 1 else 0)
    .windowByCount(3)
    .map(_.sum)
    .run(Stream.Empty)
    .toList

  val eventWindows = Source(Stream.fromList(List(
    Timestamped("checkout", 1000L),
    Timestamped("search", 1300L),
    Timestamped("checkout", 6200L),
    Timestamped("search", 7200L),
    Timestamped("checkout", 9100L)
  )))
    .withWatermarks(emitEveryN = 2)
    .windowByEventTime(windowSizeMs = 5000L)
    .run(Stream.Empty)
    .toList

  val cancellable = Source(Stream.fromList((1 to 2000).toList))
    .asyncBoundary(16)
    .map { i =>
      Thread.sleep(1)
      i
    }
    .runCancellableIterator(Stream.Empty, bufferSize = 8)

  val sampled = cancellable.iterator.take(5).toList
  cancellable.cancel()
  val streamOutcome = Await.result(cancellable.outcome, 2.seconds)

  var capturedWriter: AuditWriter = null
  val auditSink = Source(Stream.fromList(List("created", "queued", "processed")))
    .toManagedSink(() => {
      val writer = new AuditWriter
      capturedWriter = writer
      writer
    })((writer, status) => writer.append(s"order:$status"))

  auditSink.run(Stream.Empty)

  println(s"Risky transaction count: $riskyTxnCount")
  println(s"Error bursts by 3-log window: ${errorBursts.mkString(",")}")
  println(s"Event-time windows emitted: ${eventWindows.size}")
  println(s"Cancelled iterator sampled: ${sampled.mkString(",")}, outcome=$streamOutcome")
  println(s"Audit sink writes: ${capturedWriter.entries.mkString(" | ")}")
}
