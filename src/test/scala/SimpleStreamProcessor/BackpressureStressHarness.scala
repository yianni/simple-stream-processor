package SimpleStreamProcessor

import scala.concurrent.ExecutionContext

object BackpressureStressHarness {
  case class Result(
    elapsedMs: Long,
    sum: Long,
    targetDurationMs: Long,
    observedPerElementMs: Double,
    elementCount: Int,
    capacity: Int,
    metrics: Metrics.Snapshot
  )

  def run(targetDurationMs: Long, capacity: Int = 8)(implicit executionContext: ExecutionContext): Result = {
    val sleepPerElementMs = 1
    val calibrationSamples = 200

    val calibrationStart = System.nanoTime()
    (1 to calibrationSamples).foreach(_ => Thread.sleep(sleepPerElementMs.toLong))
    val observedPerElementMs = ((System.nanoTime() - calibrationStart) / 1000000.0) / calibrationSamples
    val elementCount = math.max(1000, (targetDurationMs / observedPerElementMs).toInt + 300)

    Metrics.reset()
    val startedAt = System.nanoTime()

    val sum = Source[Int](Stream.fromList((1 to elementCount).toList))
      .asyncBoundary(capacity)
      .map { value =>
        Thread.sleep(sleepPerElementMs.toLong)
        value
      }
      .toSink((acc: Int, i: Int) => acc + i, 0)
      .run(Stream.Empty)

    val elapsedMs = (System.nanoTime() - startedAt) / 1000000
    val snapshot = Metrics.snapshot()

    Result(
      elapsedMs = elapsedMs,
      sum = sum.toLong,
      targetDurationMs = targetDurationMs,
      observedPerElementMs = observedPerElementMs,
      elementCount = elementCount,
      capacity = capacity,
      metrics = snapshot
    )
  }

  def assertInvariants(result: Result): Unit = {
    require(result.elapsedMs >= result.targetDurationMs, s"Expected >=${result.targetDurationMs} ms elapsed, got ${result.elapsedMs}")
    require(result.metrics.boundaryQueueDepthMax <= result.capacity, s"Queue depth exceeded capacity: ${result.metrics.boundaryQueueDepthMax} > ${result.capacity}")
  }
}
