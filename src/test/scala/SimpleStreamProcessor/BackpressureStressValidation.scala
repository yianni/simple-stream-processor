package SimpleStreamProcessor

import scala.concurrent.ExecutionContext

object BackpressureStressValidation {
  def main(args: Array[String]): Unit = {
    implicit val executionContext: ExecutionContext = ExecutionContext.global

    val targetDurationMs = args.headOption.map(_.toLong).getOrElse(5000L)
    val capacity = 8
    val sleepPerElementMs = 1
    val calibrationSamples = 200

    val calibrationStart = System.nanoTime()
    (1 to calibrationSamples).foreach(_ => Thread.sleep(sleepPerElementMs.toLong))
    val observedPerElementMs = ((System.nanoTime() - calibrationStart) / 1000000.0) / calibrationSamples
    val elementCount = math.max(1000, (targetDurationMs / observedPerElementMs).toInt + 300)

    Metrics.reset()

    val startedAt = System.nanoTime()

    val result = Source[Int](Stream.fromList((1 to elementCount).toList))
      .asyncBoundary(capacity)
      .map { value =>
        Thread.sleep(sleepPerElementMs.toLong)
        value
      }
      .toSink((acc: Int, i: Int) => acc + i, 0)
      .run(Stream.Empty)

    val elapsedMs = (System.nanoTime() - startedAt) / 1000000
    val snapshot = Metrics.snapshot()

    println(s"elapsed_ms=$elapsedMs")
    println(s"sum=$result")
    println(s"boundary_queue_depth_max=${snapshot.boundaryQueueDepthMax}")
    println(s"boundary_producer_blocked_ms=${snapshot.boundaryProducerBlockedMs}")
    println(s"target_duration_ms=$targetDurationMs")
    println(f"observed_per_element_ms=$observedPerElementMs%.3f")
    println(s"element_count=$elementCount")

    require(elapsedMs >= targetDurationMs, s"Expected >=$targetDurationMs ms elapsed, got $elapsedMs")
    require(snapshot.boundaryQueueDepthMax <= capacity, s"Queue depth exceeded capacity: ${snapshot.boundaryQueueDepthMax} > $capacity")
  }
}
