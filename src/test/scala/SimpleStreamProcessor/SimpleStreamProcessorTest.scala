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

}