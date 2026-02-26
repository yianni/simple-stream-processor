package SimpleStreamProcessor

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

object Metrics {
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

  trait Collector {
    def reset(): Unit
    def snapshot(): Snapshot
    def incParMapInFlight(): Unit
    def decParMapInFlight(): Unit
    def setBoundaryQueueDepth(depth: Int): Unit
    def addBoundaryProducerBlockedMs(ms: Long): Unit
    def incLateEventDropped(): Unit
    def incWatermarkRegression(): Unit
    def incResourceCloseFailure(): Unit
    def incUnhandledError(): Unit
  }

  private final class AtomicCollector extends Collector {
    private val parMapInFlight = new AtomicInteger(0)
    private val boundaryQueueDepth = new AtomicInteger(0)
    private val boundaryQueueDepthMax = new AtomicInteger(0)
    private val boundaryProducerBlockedMs = new AtomicLong(0)
    private val lateEventDroppedTotal = new AtomicLong(0)
    private val watermarkRegressionTotal = new AtomicLong(0)
    private val resourceCloseFailTotal = new AtomicLong(0)
    private val unhandledErrorTotal = new AtomicLong(0)

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

  private val globalCollector: AtomicCollector = new AtomicCollector
  private val scopedCollector = new ThreadLocal[Collector]()

  def newCollector(): Collector = new AtomicCollector

  def currentCollector: Collector = Option(scopedCollector.get()).getOrElse(globalCollector)

  def withCollector[A](collector: Collector)(body: => A): A = {
    val previous = scopedCollector.get()
    scopedCollector.set(collector)
    try body
    finally {
      if (previous == null) scopedCollector.remove()
      else scopedCollector.set(previous)
    }
  }

  def reset(): Unit = {
    globalCollector.reset()
  }

  def snapshot(): Snapshot = globalCollector.snapshot()

  def incParMapInFlight(): Unit = {
    currentCollector.incParMapInFlight()
  }

  def decParMapInFlight(): Unit = {
    currentCollector.decParMapInFlight()
  }

  def setBoundaryQueueDepth(depth: Int): Unit = {
    currentCollector.setBoundaryQueueDepth(depth)
  }

  def addBoundaryProducerBlockedMs(ms: Long): Unit = {
    currentCollector.addBoundaryProducerBlockedMs(ms)
  }

  def incLateEventDropped(): Unit = {
    currentCollector.incLateEventDropped()
  }

  def incWatermarkRegression(): Unit = {
    currentCollector.incWatermarkRegression()
  }

  def incResourceCloseFailure(): Unit = {
    currentCollector.incResourceCloseFailure()
  }

  def incUnhandledError(): Unit = {
    currentCollector.incUnhandledError()
  }
}
