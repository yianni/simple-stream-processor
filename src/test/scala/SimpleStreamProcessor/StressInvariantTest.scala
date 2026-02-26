package SimpleStreamProcessor

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.ExecutionContext

class StressInvariantTest extends AnyFunSuite {

  test("Backpressure harness invariants hold across repeated runs") {
    implicit val executionContext: ExecutionContext = ExecutionContext.global

    val first = BackpressureStressHarness.run(targetDurationMs = 3000, capacity = 8)
    val second = BackpressureStressHarness.run(targetDurationMs = 3000, capacity = 8)

    BackpressureStressHarness.assertInvariants(first)
    BackpressureStressHarness.assertInvariants(second)

    assert(first.metrics.boundaryQueueDepthMax <= 8)
    assert(second.metrics.boundaryQueueDepthMax <= 8)
  }
}
