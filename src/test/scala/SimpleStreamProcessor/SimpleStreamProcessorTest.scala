package SimpleStreamProcessor

import org.scalatest.funsuite.AnyFunSuite

class SimpleStreamProcessorTest extends AnyFunSuite {

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
  }

  test("Sink fails when upstream stream contains an error") {
    val sink = Source[Int](Stream.fromList(List(1, 0, 2)))
      .map(i => 10 / i)
      .toSink((acc: Int, i: Int) => acc + i, 0)

    intercept[ArithmeticException](sink.run(Stream.Empty))
  }

  test("Node recover allows pipeline-level fallback") {
    val sink = Source[Int](Stream.fromList(List(1, 0, 2)))
      .map(i => 10 / i)
      .recover { case _: ArithmeticException => 0 }
      .toSink((acc: Int, i: Int) => acc + i, 0)

    assert(sink.run(Stream.Empty) == 10)
  }

}
