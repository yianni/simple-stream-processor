package SimpleStreamProcessor

import SimpleStreamProcessor.Stream.{QueueEnd, QueueError, QueueSignal, QueueValue}

import java.util.concurrent.ArrayBlockingQueue
import scala.collection.mutable
import scala.concurrent.ExecutionContext

sealed trait Node[I, O] {
  protected var nodeName: String = "Node"

  def run(input: Stream[I]): Stream[O]

  def map[O2](f: O => O2): Node[I, O2] = Pipe(this, f).withName(this.nodeName + ".map")

  def flatMap[O2](f: O => Stream[O2]): Node[I, O2] = FlatMapPipe(this, f).withName(this.nodeName + ".flatMap")

  def filter(f: O => Boolean): Node[I, O] = FilterPipe(this, f).withName(this.nodeName + ".filter")

  def recover(f: PartialFunction[Throwable, O]): Node[I, O] = RecoverPipe(this, f).withName(this.nodeName + ".recover")

  def parMap[O2](parallelism: Int)(f: O => O2)(implicit executionContext: ExecutionContext): Node[I, O2] =
    ParMapPipe(this, parallelism, f, executionContext).withName(this.nodeName + ".parMap")

  def asyncBoundary(bufferSize: Int): Node[I, O] = AsyncBoundaryPipe(this, bufferSize).withName(this.nodeName + ".asyncBoundary")

  def windowByCount(size: Int): Node[I, List[O]] = CountWindowPipe(this, size).withName(this.nodeName + ".windowByCount")
  def toSink(f: (O, O) => O, zero: O): Sink[I, O] = Sink(this, f, zero).withName(this.nodeName + ".toSink")

  def toManagedSink[R <: AutoCloseable](resourceFactory: () => R)(consume: (R, O) => Unit): ManagedSink[I, O, R] =
    ManagedSink(this, resourceFactory, consume).withName(this.nodeName + ".toManagedSink")

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
    try {
      streamFactory(resource).ensuring(() => resource.close())
    } catch {
      case e: Throwable =>
        try resource.close()
        catch {
          case closeError: Throwable => e.addSuppressed(closeError)
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

    val producer = new Thread(() => {
      try {
        upstream.run(input).foreach(o => queue.put(QueueValue(o)))
        queue.put(QueueEnd)
      } catch {
        case e: Throwable => queue.put(QueueError(e))
      }
    })

    producer.setName(s"simple-stream-async-boundary-$nodeName")
    producer.setDaemon(true)
    producer.start()

    Stream.fromBlockingQueue(queue)
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
          if (processingError != null) processingError.addSuppressed(closeError)
          else throw closeError
      }
    }
  }

  def withName(newName: String): ManagedSink[I, O, R] = this.copy(name = newName)

  override def toString: String = s"$name($upstream)"
}
case class Sink[I, O](upstream: Node[I, O], f: (O, O) => O, zero: O, name: String = "Sink") {
  def run(input: Stream[I]): O = upstream.run(input).fold(zero)(f)

  def withName(newName: String): Sink[I, O] = this.copy(name = newName)

  override def toString: String = s"$name($upstream)"
}
