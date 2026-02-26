package SimpleStreamProcessor

import scala.concurrent.ExecutionContext

object BackpressureStressValidation {
  def main(args: Array[String]): Unit = {
    implicit val executionContext: ExecutionContext = ExecutionContext.global

    val targetDurationMs = args.headOption.map(_.toLong).getOrElse(5000L)
    val result = BackpressureStressHarness.run(targetDurationMs = targetDurationMs, capacity = 8)

    println(s"elapsed_ms=${result.elapsedMs}")
    println(s"sum=${result.sum}")
    println(s"boundary_queue_depth_max=${result.metrics.boundaryQueueDepthMax}")
    println(s"boundary_producer_blocked_ms=${result.metrics.boundaryProducerBlockedMs}")
    println(s"target_duration_ms=${result.targetDurationMs}")
    println(f"observed_per_element_ms=${result.observedPerElementMs}%.3f")
    println(s"element_count=${result.elementCount}")

    BackpressureStressHarness.assertInvariants(result)
  }
}
