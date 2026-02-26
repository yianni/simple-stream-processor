package SimpleStreamProcessor

import scala.concurrent.ExecutionContext

object PerformanceBaselineReport {
  def main(args: Array[String]): Unit = {
    implicit val executionContext: ExecutionContext = ExecutionContext.global

    val elementCount = args.headOption.map(_.toInt).getOrElse(20000)
    val parallelism = args.lift(1).map(_.toInt).getOrElse(4)
    val boundarySize = args.lift(2).map(_.toInt).getOrElse(16)

    Metrics.reset()
    val startedNs = System.nanoTime()

    val sum = Source[Int](Stream.fromList((1 to elementCount).toList))
      .parMap(parallelism)(_ * 2)
      .asyncBoundary(boundarySize)
      .toSink((acc: Int, i: Int) => acc + i, 0)
      .run(Stream.Empty)

    val elapsedNs = System.nanoTime() - startedNs
    val elapsedMs = elapsedNs / 1000000.0
    val throughputPerSec = (elementCount.toDouble / elapsedNs) * 1000000000.0
    val snapshot = Metrics.snapshot()

    println(s"element_count=$elementCount")
    println(s"parallelism=$parallelism")
    println(s"boundary_size=$boundarySize")
    println(f"elapsed_ms=$elapsedMs%.3f")
    println(f"throughput_per_sec=$throughputPerSec%.2f")
    println(s"sum=$sum")
    println(s"boundary_queue_depth_max=${snapshot.boundaryQueueDepthMax}")
    println(s"boundary_producer_blocked_ms=${snapshot.boundaryProducerBlockedMs}")
  }
}
