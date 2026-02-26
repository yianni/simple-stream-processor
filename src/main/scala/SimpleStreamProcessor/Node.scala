package SimpleStreamProcessor

import SimpleStreamProcessor.Stream.{QueueEnd, QueueError, QueueSignal, QueueValue}

import java.util.concurrent.CancellationException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.annotation.tailrec

sealed trait Node[I, O] {
  protected var nodeName: String = "Node"

  def run(input: Stream[I]): Stream[O]

  def map[O2](f: O => O2): Node[I, O2] = Pipe(this, f).withName(this.nodeName + ".map")

  def flatMap[O2](f: O => Stream[O2]): Node[I, O2] = FlatMapPipe(this, f).withName(this.nodeName + ".flatMap")

  def filter(f: O => Boolean): Node[I, O] = FilterPipe(this, f).withName(this.nodeName + ".filter")

  def recover(f: PartialFunction[Throwable, O]): Node[I, O] = RecoverPipe(this, f).withName(this.nodeName + ".recover")

  def recoverWith(f: PartialFunction[Throwable, Stream[O]]): Node[I, O] =
    RecoverWithPipe(this, f).withName(this.nodeName + ".recoverWith")

  def parMap[O2](parallelism: Int)(f: O => O2)(implicit executionContext: ExecutionContext): Node[I, O2] =
    ParMapPipe(this, parallelism, f, executionContext).withName(this.nodeName + ".parMap")

  def asyncBoundary(bufferSize: Int): Node[I, O] = AsyncBoundaryPipe(this, bufferSize).withName(this.nodeName + ".asyncBoundary")

  def windowByCount(size: Int): Node[I, List[O]] = CountWindowPipe(this, size).withName(this.nodeName + ".windowByCount")

  def toSink(f: (O, O) => O, zero: O): Sink[I, O] = Sink(this, f, zero).withName(this.nodeName + ".toSink")

  def toManagedSink[R <: AutoCloseable](resourceFactory: () => R)(consume: (R, O) => Unit): ManagedSink[I, O, R] =
    ManagedSink(this, resourceFactory, consume).withName(this.nodeName + ".toManagedSink")

  def runToListAsync(input: Stream[I])(implicit executionContext: ExecutionContext): ExecutionHandle[List[O]] =
    RuntimeControl.runAsync { token =>
      def finalizeOnCancel(stream: Stream[O]): Unit = {
        stream.takeUntilCancelled(token).foreach(_ => ())
      }

      @tailrec
      def loop(stream: Stream[O], acc: List[O]): List[O] = {
        if (token.isCancelled) {
          finalizeOnCancel(stream)
          throw new CancellationException("Pipeline cancelled")
        }
        stream match {
          case Stream.Emit(value, next) => loop(next(), value :: acc)
          case Stream.Halt() => acc.reverse
          case Stream.Empty => acc.reverse
          case Stream.Error(e) => throw e
        }
      }

      loop(run(input), Nil)
    }

  def runForeachAsync(input: Stream[I])(consume: O => Unit)(implicit executionContext: ExecutionContext): ExecutionHandle[Unit] =
    RuntimeControl.runAsync { token =>
      def finalizeOnCancel(stream: Stream[O]): Unit = {
        stream.takeUntilCancelled(token).foreach(_ => ())
      }

      @tailrec
      def loop(stream: Stream[O]): Unit = {
        if (token.isCancelled) {
          finalizeOnCancel(stream)
          throw new CancellationException("Pipeline cancelled")
        }
        stream match {
          case Stream.Emit(value, next) =>
            consume(value)
            loop(next())
          case Stream.Halt() =>
          case Stream.Empty =>
          case Stream.Error(e) => throw e
        }
      }

      loop(run(input))
    }

  def runCancellableIterator(input: Stream[I], bufferSize: Int = 64)(implicit executionContext: ExecutionContext): CancellableIterator[O] = {
    val capacity = math.max(1, bufferSize)
    val queue = new ArrayBlockingQueue[QueueSignal[O]](capacity)

    val handle = runForeachAsync(input) { value =>
      queue.put(QueueValue(value))
    }

    def publishTerminal(signal: QueueSignal[O]): Unit = {
      queue.clear()
      queue.offer(signal)
    }

    handle.outcome.foreach {
      case ExecutionCompleted(_) => publishTerminal(QueueEnd)
      case ExecutionCancelled => publishTerminal(QueueEnd)
      case ExecutionFailed(error) => publishTerminal(QueueError(error))
    }(executionContext)

    CancellableIterator(
      iterator = Stream.fromBlockingQueue(queue).iterator,
      cancel = handle.cancel,
      outcome = handle.outcome,
      metricsSnapshot = handle.metricsSnapshot
    )
  }

  def runIterator(input: Stream[I]): Iterator[O] = run(input).iterator

  def withName(name: String): this.type = {
    nodeName = name;
    this
  }

  override def toString: String = nodeName
}

case class Source[I](stream: Stream[I]) extends Node[Unit, I] {
  def run(input: Stream[Unit]): Stream[I] = stream

  override def toString: String = super.toString
}

case class ManagedSource[I, R <: AutoCloseable](resourceFactory: () => R, streamFactory: R => Stream[I]) extends Node[Unit, I] {
  def run(input: Stream[Unit]): Stream[I] = {
    val resource = resourceFactory()
    val closed = new AtomicBoolean(false)

    def closeResourceOnce(): Unit = {
      if (closed.compareAndSet(false, true)) {
        try resource.close()
        catch {
          case closeError: Throwable =>
            Metrics.incResourceCloseFailure()
            throw closeError
        }
      }
    }

    try {
      val stream = streamFactory(resource)
      val cancellableStream = RuntimeControl.currentToken match {
        case Some(token) => stream.takeUntilCancelled(token)
        case None => stream
      }
      cancellableStream.ensuring(() => closeResourceOnce())
    } catch {
      case e: Throwable =>
        try closeResourceOnce()
        catch {
          case closeError: Throwable if closeError ne e =>
            e.addSuppressed(closeError)
          case _: Throwable =>
        }
        Stream.Error(e)
    }
  }

  override def toString: String = super.toString
}

case class Pipe[I, O, O2](upstream: Node[I, O], f: O => O2) extends Node[I, O2] {
  def run(input: Stream[I]): Stream[O2] = upstream.run(input).map(f)

  override def toString: String = super.toString + "(" + upstream + ")"
}

case class FlatMapPipe[I, O, O2](upstream: Node[I, O], f: O => Stream[O2]) extends Node[I, O2] {
  def run(input: Stream[I]): Stream[O2] = upstream.run(input).flatMap(f)

  override def toString: String = super.toString + "(" + upstream + ")"
}

case class FilterPipe[I, O](upstream: Node[I, O], f: O => Boolean) extends Node[I, O] {
  def run(input: Stream[I]): Stream[O] = upstream.run(input).filter(f)

  override def toString: String = super.toString + "(" + upstream + ")"
}

case class RecoverPipe[I, O](upstream: Node[I, O], f: PartialFunction[Throwable, O]) extends Node[I, O] {
  def run(input: Stream[I]): Stream[O] = upstream.run(input).recover(f)

  override def toString: String = super.toString + "(" + upstream + ")"
}

case class RecoverWithPipe[I, O](upstream: Node[I, O], f: PartialFunction[Throwable, Stream[O]]) extends Node[I, O] {
  def run(input: Stream[I]): Stream[O] = upstream.run(input).recoverWith(f)

  override def toString: String = super.toString + "(" + upstream + ")"
}

case class ParMapPipe[I, O, O2](
  upstream: Node[I, O],
  parallelism: Int,
  f: O => O2,
  executionContext: ExecutionContext
) extends Node[I, O2] {
  def run(input: Stream[I]): Stream[O2] = upstream.run(input).parMap(parallelism)(f)(executionContext)

  override def toString: String = super.toString + "(" + upstream + ")"
}

case class AsyncBoundaryPipe[I, O](upstream: Node[I, O], bufferSize: Int) extends Node[I, O] {
  def run(input: Stream[I]): Stream[O] = {
    if (bufferSize <= 0) return Stream.Error(new IllegalArgumentException("bufferSize must be > 0"))

    val queue = new ArrayBlockingQueue[QueueSignal[O]](bufferSize)
    val cancellationToken = RuntimeControl.currentToken
    val collector = Metrics.currentCollector
    val lastConsumerSignalNs = new AtomicLong(System.nanoTime())
    val stalledConsumerNs = TimeUnit.SECONDS.toNanos(2)

    def putOrAbort(signal: QueueSignal[O]): Unit = {
      var offered = false
      while (!offered) {
        if (cancellationToken.exists(_.isCancelled)) throw new CancellationException("Pipeline cancelled")

        offered = queue.offer(signal, 100, TimeUnit.MILLISECONDS)
        if (offered) {
          Metrics.setBoundaryQueueDepth(queue.size())
        } else {
          Metrics.addBoundaryProducerBlockedMs(100)
          val stalledNs = System.nanoTime() - lastConsumerSignalNs.get()
          if (stalledNs >= stalledConsumerNs) {
            throw new IllegalStateException("Async boundary consumer stalled")
          }
        }
      }
    }

    val producer = new Thread(() => {
      Metrics.withCollector(collector) {
        cancellationToken.foreach(_.registerCurrentThread())
        try {
          upstream.run(input).foreach { o =>
            putOrAbort(QueueValue(o))
          }
          putOrAbort(QueueEnd)
        } catch {
          case _: InterruptedException if cancellationToken.exists(_.isCancelled) =>
            queue.offer(QueueEnd)
            Metrics.setBoundaryQueueDepth(queue.size())
          case _: CancellationException =>
            queue.offer(QueueEnd)
            Metrics.setBoundaryQueueDepth(queue.size())
          case e: Throwable =>
            queue.offer(QueueError(e))
            Metrics.setBoundaryQueueDepth(queue.size())
        } finally {
          cancellationToken.foreach(_.unregisterCurrentThread())
        }
      }
    })

    producer.setName(s"simple-stream-async-boundary-$nodeName")
    producer.setDaemon(true)
    producer.start()

    Stream.fromBlockingQueue(queue, (_: QueueSignal[O]) => lastConsumerSignalNs.set(System.nanoTime()))
  }

  override def toString: String = super.toString + "(" + upstream + ")"
}

case class CountWindowPipe[I, O](upstream: Node[I, O], size: Int) extends Node[I, List[O]] {
  def run(input: Stream[I]): Stream[List[O]] = upstream.run(input).grouped(size)

  override def toString: String = super.toString + "(" + upstream + ")"
}

case class Timestamped[+A](value: A, timestampMs: Long)
case class EventTimeWindow[+A](startMs: Long, endMs: Long, values: List[A], watermarkMs: Long)

sealed trait TimedEvent[+A]
case class Record[+A](event: Timestamped[A]) extends TimedEvent[A]
case class Watermark(timestampMs: Long) extends TimedEvent[Nothing]

case class WatermarkPipe[I, O](upstream: Node[I, Timestamped[O]], emitEveryN: Int) extends Node[I, TimedEvent[O]] {
  def run(input: Stream[I]): Stream[TimedEvent[O]] = {
    if (emitEveryN <= 0) return Stream.Error(new IllegalArgumentException("emitEveryN must be > 0"))

    val out = mutable.ListBuffer.empty[TimedEvent[O]]
    var count = 0
    var maxTimestamp = Long.MinValue

    try {
      upstream.run(input).foreach { ts =>
        out += Record(ts)
        count += 1
        maxTimestamp = math.max(maxTimestamp, ts.timestampMs)
        if (count % emitEveryN == 0) out += Watermark(maxTimestamp)
      }

      if (count > 0 && count % emitEveryN != 0) out += Watermark(maxTimestamp)
      Stream.fromList(out.toList)
    } catch {
      case e: Throwable => Stream.Error(e)
    }
  }

  override def toString: String = super.toString + "(" + upstream + ")"
}

case class EventTimeWindowPipe[I, O](upstream: Node[I, TimedEvent[O]], windowSizeMs: Long) extends Node[I, EventTimeWindow[O]] {
  def run(input: Stream[I]): Stream[EventTimeWindow[O]] = {
    if (windowSizeMs <= 0) return Stream.Error(new IllegalArgumentException("windowSizeMs must be > 0"))

    val openWindows = mutable.Map.empty[Long, List[O]]
    val emitted = mutable.ListBuffer.empty[EventTimeWindow[O]]
    var currentWatermark = Long.MinValue

    try {
      upstream.run(input).foreach {
        case Record(Timestamped(value, ts)) =>
          if (ts >= currentWatermark) {
            val start = (ts / windowSizeMs) * windowSizeMs
            openWindows.update(start, openWindows.getOrElse(start, Nil) :+ value)
          } else {
            Metrics.incLateEventDropped()
          }

        case Watermark(wmTs) =>
          if (wmTs > currentWatermark) {
            currentWatermark = wmTs
            openWindows.keys
              .filter(start => start + windowSizeMs <= currentWatermark)
              .toList
              .sorted
              .foreach { start =>
                val values = openWindows.remove(start).getOrElse(Nil)
                emitted += EventTimeWindow(start, start + windowSizeMs, values, currentWatermark)
              }
          } else if (wmTs < currentWatermark) {
            Metrics.incWatermarkRegression()
          }
      }

      Stream.fromList(emitted.toList)
    } catch {
      case e: Throwable => Stream.Error(e)
    }
  }

  override def toString: String = super.toString + "(" + upstream + ")"
}

object NodeSyntax {
  implicit class TimestampedNodeOps[I, O](private val node: Node[I, Timestamped[O]]) extends AnyVal {
    def withWatermarks(emitEveryN: Int): Node[I, TimedEvent[O]] =
      WatermarkPipe(node, emitEveryN).withName(node.toString + ".withWatermarks")
  }

  implicit class TimedEventNodeOps[I, O](private val node: Node[I, TimedEvent[O]]) extends AnyVal {
    def windowByEventTime(windowSizeMs: Long): Node[I, EventTimeWindow[O]] =
      EventTimeWindowPipe(node, windowSizeMs).withName(node.toString + ".windowByEventTime")
  }
}

case class ManagedSink[I, O, R <: AutoCloseable](
  upstream: Node[I, O],
  resourceFactory: () => R,
  consume: (R, O) => Unit,
  name: String = "ManagedSink"
) {
  def run(input: Stream[I]): Unit = {
    val resource = resourceFactory()
    var processingError: Throwable = null

    try {
      upstream.run(input).foreach(value => consume(resource, value))
    } catch {
      case e: Throwable =>
        processingError = e
        throw e
    } finally {
      try {
        resource.close()
      } catch {
        case closeError: Throwable =>
          Metrics.incResourceCloseFailure()
          if (processingError != null) processingError.addSuppressed(closeError)
          else throw closeError
      }
    }
  }

  def withName(newName: String): ManagedSink[I, O, R] = this.copy(name = newName)

  def runAsync(input: Stream[I])(implicit executionContext: ExecutionContext): ExecutionHandle[Unit] =
    RuntimeControl.runAsync { token =>
      val resource = resourceFactory()
      var processingError: Throwable = null

      try {
        @tailrec
        def drain(stream: Stream[O]): Unit = {
          if (token.isCancelled) throw new CancellationException("Pipeline cancelled")
          stream match {
            case Stream.Emit(value, next) =>
              consume(resource, value)
              drain(next())
            case Stream.Halt() =>
            case Stream.Empty =>
            case Stream.Error(e) => throw e
          }
        }

        drain(upstream.run(input))
      } catch {
        case e: Throwable =>
          processingError = e
          throw e
      } finally {
        try {
          resource.close()
        } catch {
          case closeError: Throwable =>
            Metrics.incResourceCloseFailure()
            if (processingError != null) processingError.addSuppressed(closeError)
            else throw closeError
        }
      }
    }

  override def toString: String = s"$name($upstream)"
}

case class Sink[I, O](upstream: Node[I, O], f: (O, O) => O, zero: O, name: String = "Sink") {
  def run(input: Stream[I]): O = upstream.run(input).fold(zero)(f)

  def runAsync(input: Stream[I])(implicit executionContext: ExecutionContext): ExecutionHandle[O] =
    RuntimeControl.runAsync { token =>
      def finalizeOnCancel(stream: Stream[O]): Unit = {
        stream.takeUntilCancelled(token).foreach(_ => ())
      }

      @tailrec
      def loop(stream: Stream[O], acc: O): O = {
        if (token.isCancelled) {
          finalizeOnCancel(stream)
          throw new CancellationException("Pipeline cancelled")
        }
        stream match {
          case Stream.Emit(value, next) => loop(next(), f(acc, value))
          case Stream.Halt() => acc
          case Stream.Empty => acc
          case Stream.Error(e) => throw e
        }
      }

      loop(upstream.run(input), zero)
    }

  def withName(newName: String): Sink[I, O] = this.copy(name = newName)

  override def toString: String = s"$name($upstream)"
}
