package SimpleStreamProcessor

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach
import SimpleStreamProcessor.NodeSyntax._

import scala.concurrent.ExecutionContext

class SimpleStreamProcessorTest extends AnyFunSuite with BeforeAndAfterEach {

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    Metrics.reset()
  }

  test("Source with map and filter operations") {
    val source: Source[Int] = Source[Int](Stream.fromList((1 to 10).toList)).withName("source")
    val pipe1: Pipe[Unit, Int, Int] = Pipe(source, (i: Int) => i * 2).withName("pipe1")
    val pipe2: FilterPipe[Unit, Int] = FilterPipe(pipe1, (i: Int) => i % 2 == 0).withName("pipe2")
    val result: List[Int] = pipe2.run(Stream.Empty).toList
    assert(result == List(2, 4, 6, 8, 10, 12, 14, 16, 18, 20))
  }

  test("Source with Sink") {
    val source: Source[Int] = Source[Int](Stream.fromList((1 to 10).toList)).withName("source")
    val sink: Sink[Unit, Int] = Sink(source, (acc: Int, i: Int) => acc + i, 0).withName("sink")
    val result: Int = sink.run(Stream.Empty)
    assert(result == 55) // the sum of numbers from 1 to 10
  }

  test("FlatMap keeps processing when branch emits empty stream") {
    val result = Stream.fromList(List(1, 2, 3))
      .flatMap(i => if (i % 2 == 0) Stream.fromList(List(i)) else Stream.Empty)
      .toList

    assert(result == List(2))
  }

  test("Stream recover converts failures into values") {
    val result = Stream.fromList(List(1, 2, 0, 4))
      .map(i => 10 / i)
      .recover { case _: ArithmeticException => -1 }
      .toList

    assert(result == List(10, 5, -1))
    assert(Metrics.snapshot().unhandledErrorTotal == 0)
  }

  test("Sink fails when upstream stream contains an error") {
    val sink = Source[Int](Stream.fromList(List(1, 0, 2)))
      .map(i => 10 / i)
      .toSink((acc: Int, i: Int) => acc + i, 0)

    intercept[ArithmeticException](sink.run(Stream.Empty))
    assert(Metrics.snapshot().unhandledErrorTotal > 0)
  }

  test("Node recover allows pipeline-level fallback") {
    val sink = Source[Int](Stream.fromList(List(1, 0, 2)))
      .map(i => 10 / i)
      .recover { case _: ArithmeticException => 0 }
      .toSink((acc: Int, i: Int) => acc + i, 0)

    assert(sink.run(Stream.Empty) == 10)
  }

  test("Stream parMap preserves input order") {
    implicit val executionContext: ExecutionContext = ExecutionContext.global

    val result = Stream.fromList(List(1, 2, 3, 4))
      .parMap(2) { i =>
        if (i % 2 == 0) Thread.sleep(15) else Thread.sleep(1)
        i * 10
      }
      .toList

    assert(result == List(10, 20, 30, 40))
    assert(Metrics.snapshot().parMapInFlight == 0)
  }

  test("Stream parMap fails fast on invalid parallelism") {
    implicit val executionContext: ExecutionContext = ExecutionContext.global

    val stream = Stream.fromList(List(1, 2, 3)).parMap(0)(identity)
    intercept[IllegalArgumentException](stream.toList)
  }

  test("Node parMap works with sink aggregation") {
    implicit val executionContext: ExecutionContext = ExecutionContext.global

    val sink = Source[Int](Stream.fromList(List(1, 2, 3, 4)))
      .parMap(2)(_ * 2)
      .toSink((acc: Int, i: Int) => acc + i, 0)

    assert(sink.run(Stream.Empty) == 20)
  }

  test("Async boundary preserves stream values") {
    val result = Source[Int](Stream.fromList(List(1, 2, 3, 4)))
      .map(_ * 3)
      .asyncBoundary(2)
      .run(Stream.Empty)
      .toList

    assert(result == List(3, 6, 9, 12))
  }

  test("Async boundary fails fast on invalid buffer size") {
    val stream = Source[Int](Stream.fromList(List(1, 2, 3)))
      .asyncBoundary(0)
      .run(Stream.Empty)

    intercept[IllegalArgumentException](stream.toList)
  }

  test("Async boundary propagates upstream error") {
    val sink = Source[Int](Stream.fromList(List(1, 0, 2)))
      .map(i => 10 / i)
      .asyncBoundary(1)
      .toSink((acc: Int, i: Int) => acc + i, 0)

    intercept[ArithmeticException](sink.run(Stream.Empty))
  }

  test("Managed sink closes resource after successful processing") {
    class FakeResource extends AutoCloseable {
      val values = scala.collection.mutable.ListBuffer.empty[Int]
      var closed = false

      override def close(): Unit = closed = true
    }

    var captured: FakeResource = null
    val sink = Source[Int](Stream.fromList(List(1, 2, 3)))
      .toManagedSink(() => {
        val resource = new FakeResource
        captured = resource
        resource
      })((resource, value) => resource.values += value)

    sink.run(Stream.Empty)

    assert(captured.values.toList == List(1, 2, 3))
    assert(captured.closed)
  }

  test("Managed sink preserves processing error and closes resource") {
    class FakeResource extends AutoCloseable {
      var closed = false

      override def close(): Unit = closed = true
    }

    var captured: FakeResource = null
    val sink = Source[Int](Stream.fromList(List(1, 0, 2)))
      .map(i => 10 / i)
      .toManagedSink(() => {
        val resource = new FakeResource
        captured = resource
        resource
      })((_, _) => ())

    intercept[ArithmeticException](sink.run(Stream.Empty))
    assert(captured.closed)
    assert(Metrics.snapshot().resourceCloseFailTotal == 0)
  }

  test("Managed source closes resource after stream consumption") {
    class FakeResource extends AutoCloseable {
      var closed = false

      override def close(): Unit = closed = true
    }

    var captured: FakeResource = null
    val source = ManagedSource[Int, FakeResource](
      resourceFactory = () => {
        val resource = new FakeResource
        captured = resource
        resource
      },
      streamFactory = _ => Stream.fromList(List(1, 2, 3))
    )

    val result = source.run(Stream.Empty).toList

    assert(result == List(1, 2, 3))
    assert(captured.closed)
  }

  test("Count windows split stream into fixed-size batches") {
    val result = Source[Int](Stream.fromList((1 to 7).toList))
      .windowByCount(3)
      .run(Stream.Empty)
      .toList

    assert(result == List(List(1, 2, 3), List(4, 5, 6), List(7)))
  }

  test("Watermarks are emitted and event-time windows close") {
    val events = List(
      Timestamped("a", 1L),
      Timestamped("b", 3L),
      Timestamped("c", 7L),
      Timestamped("d", 8L)
    )

    val windows = Source[Timestamped[String]](Stream.fromList(events))
      .withWatermarks(emitEveryN = 2)
      .windowByEventTime(windowSizeMs = 5L)
      .run(Stream.Empty)
      .toList

    assert(windows == List(EventTimeWindow(0L, 5L, List("a", "b"), 8L)))
  }

  test("Event-time windows drop late records and ignore regressing watermarks") {
    val timedEvents = List(
      Record(Timestamped("a", 1L)),
      Watermark(8L),
      Record(Timestamped("late", 4L)),
      Watermark(7L)
    )

    val windows = Source[TimedEvent[String]](Stream.fromList(timedEvents))
      .windowByEventTime(windowSizeMs = 5L)
      .run(Stream.Empty)
      .toList

    assert(windows == List(EventTimeWindow(0L, 5L, List("a"), 8L)))
    assert(Metrics.snapshot().lateEventDroppedTotal == 1)
    assert(Metrics.snapshot().watermarkRegressionTotal == 1)
  }

  test("Async boundary queue depth stays within configured capacity") {
    val capacity = 4
    val result = Source[Int](Stream.fromList((1 to 300).toList))
      .asyncBoundary(capacity)
      .map { i =>
        Thread.sleep(1)
        i
      }
      .run(Stream.Empty)
      .toList

    assert(result.size == 300)
    assert(Metrics.snapshot().boundaryQueueDepthMax <= capacity)
    assert(Metrics.snapshot().boundaryProducerBlockedMs >= 0)
  }

  test("Managed sink close failure is recorded") {
    class BrokenResource extends AutoCloseable {
      override def close(): Unit = throw new RuntimeException("close-failed")
    }

    val sink = Source[Int](Stream.fromList(List(1, 2, 3)))
      .toManagedSink(() => new BrokenResource)((_, _) => ())

    intercept[RuntimeException](sink.run(Stream.Empty))
    assert(Metrics.snapshot().resourceCloseFailTotal == 1)
  }

}
