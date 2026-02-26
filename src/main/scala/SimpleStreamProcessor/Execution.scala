package SimpleStreamProcessor

import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{ExecutionContext, Future}

sealed trait ExecutionOutcome[+A]
case class ExecutionCompleted[A](value: A) extends ExecutionOutcome[A]
case class ExecutionFailed(error: Throwable) extends ExecutionOutcome[Nothing]
case object ExecutionCancelled extends ExecutionOutcome[Nothing]

case class ExecutionHandle[+A](
  outcome: Future[ExecutionOutcome[A]],
  cancel: () => Unit,
  metricsSnapshot: () => Metrics.Snapshot
)

case class CancellableIterator[+A](
  iterator: Iterator[A],
  cancel: () => Unit,
  outcome: Future[ExecutionOutcome[Unit]],
  metricsSnapshot: () => Metrics.Snapshot
)

final class CancellationToken {
  private val cancelled = new AtomicBoolean(false)
  private val threads = ConcurrentHashMap.newKeySet[Thread]()

  def isCancelled: Boolean = cancelled.get()

  def cancel(): Unit = {
    if (cancelled.compareAndSet(false, true)) {
      val it = threads.iterator()
      while (it.hasNext) {
        it.next().interrupt()
      }
    }
  }

  def registerCurrentThread(): Unit = {
    val current = Thread.currentThread()
    threads.add(current)
    if (isCancelled) current.interrupt()
  }

  def unregisterCurrentThread(): Unit = {
    threads.remove(Thread.currentThread())
  }
}

object RuntimeControl {
  private val threadLocalToken = new ThreadLocal[CancellationToken]()

  def currentToken: Option[CancellationToken] = Option(threadLocalToken.get())

  def isCancelled: Boolean = currentToken.exists(_.isCancelled)

  def withToken[A](token: CancellationToken)(body: => A): A = {
    val previous = threadLocalToken.get()
    threadLocalToken.set(token)
    try body
    finally {
      if (previous == null) threadLocalToken.remove()
      else threadLocalToken.set(previous)
    }
  }

  def runAsync[A](compute: CancellationToken => A)(implicit executionContext: ExecutionContext): ExecutionHandle[A] = {
    val token = new CancellationToken
    val collector = Metrics.newCollector()
    val future = Future {
      Metrics.withCollector(collector) {
        RuntimeControl.withToken(token) {
          token.registerCurrentThread()
          try {
            if (token.isCancelled) ExecutionCancelled
            else ExecutionCompleted(compute(token))
          } catch {
            case _: CancellationException => ExecutionCancelled
            case _: InterruptedException if token.isCancelled => ExecutionCancelled
            case e: Throwable => ExecutionFailed(e)
          } finally {
            token.unregisterCurrentThread()
          }
        }
      }
    }

    ExecutionHandle(future, () => token.cancel(), () => collector.snapshot())
  }
}
