package SimpleStreamProcessor

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

object Metrics {
  private val parMapInFlight = new AtomicInteger(0)
  private val boundaryQueueDepth = new AtomicInteger(0)
  private val boundaryQueueDepthMax = new AtomicInteger(0)
  private val boundaryProducerBlockedMs = new AtomicLong(0)
  private val lateEventDroppedTotal = new AtomicLong(0)
  private val watermarkRegressionTotal = new AtomicLong(0)
  private val resourceCloseFailTotal = new AtomicLong(0)
  private val unhandledErrorTotal = new AtomicLong(0)

  case class Snapshot(
    parMapInFlight: Int,
    boundaryQueueDepth: Int,
    boundaryQueueDepthMax: Int,
    boundaryProducerBlockedMs: Long,
    lateEventDroppedTotal: Long,
    watermarkRegressionTotal: Long,
    resourceCloseFailTotal: Long,
    unhandledErrorTotal: Long
  )

  def reset(): Unit = {
    parMapInFlight.set(0)
    boundaryQueueDepth.set(0)
    boundaryQueueDepthMax.set(0)
    boundaryProducerBlockedMs.set(0)
    lateEventDroppedTotal.set(0)
    watermarkRegressionTotal.set(0)
    resourceCloseFailTotal.set(0)
    unhandledErrorTotal.set(0)
  }

  def snapshot(): Snapshot = Snapshot(
    parMapInFlight = parMapInFlight.get(),
    boundaryQueueDepth = boundaryQueueDepth.get(),
    boundaryQueueDepthMax = boundaryQueueDepthMax.get(),
    boundaryProducerBlockedMs = boundaryProducerBlockedMs.get(),
    lateEventDroppedTotal = lateEventDroppedTotal.get(),
    watermarkRegressionTotal = watermarkRegressionTotal.get(),
    resourceCloseFailTotal = resourceCloseFailTotal.get(),
    unhandledErrorTotal = unhandledErrorTotal.get()
  )

  def incParMapInFlight(): Unit = {
    parMapInFlight.incrementAndGet()
  }

  def decParMapInFlight(): Unit = {
    parMapInFlight.decrementAndGet()
  }

  def setBoundaryQueueDepth(depth: Int): Unit = {
    boundaryQueueDepth.set(depth)
    boundaryQueueDepthMax.getAndUpdate(prev => math.max(prev, depth))
  }

  def addBoundaryProducerBlockedMs(ms: Long): Unit = {
    boundaryProducerBlockedMs.addAndGet(ms)
  }

  def incLateEventDropped(): Unit = {
    lateEventDroppedTotal.incrementAndGet()
  }

  def incWatermarkRegression(): Unit = {
    watermarkRegressionTotal.incrementAndGet()
  }

  def incResourceCloseFailure(): Unit = {
    resourceCloseFailTotal.incrementAndGet()
  }

  def incUnhandledError(): Unit = {
    unhandledErrorTotal.incrementAndGet()
  }
}
