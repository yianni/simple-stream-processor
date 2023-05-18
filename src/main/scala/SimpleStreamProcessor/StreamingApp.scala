package SimpleStreamProcessor

import SimpleStreamProcessor.Stream.Empty

import java.util.concurrent.TimeUnit
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

object StreamingApp extends App {

  //  val source: Source[Int] = Source[Int](Stream.fromList((1 to 9999).toList)).withName("source")
  //  val pipe1: Pipe[Unit, Int, Int] = Pipe(source, (i: Int) => i * 2).withName("pipe1")
  //  val pipe2: FilterPipe[Unit, Int] = FilterPipe(pipe1, (i: Int) => i % 2 == 0).withName("pipe2")
  //  val sink: Sink[Unit, Int] = Sink(pipe2, (acc: Int, i: Int) => acc + i, 0).withName("sink")

  val sink =
    Source[Int](Stream.fromList((1 to 9999).toList)).withName("source")
      .map((i: Int) => i * 2).withName("pipe1")
      .filter((i: Int) => i % 2 == 0).withName("pipe2")
      .toSink((acc: Int, i: Int) => acc + i, 0).withName("sink")
  
  val jobManager = new JobManager(1, 16) // 10 TaskManagers, each with 2 slots
  val jobGraph = JobGraph[Unit, Int](sink, jobManager)

  //  jobGraph.execute2(Empty)

  jobGraph.printTopology()
  val futuresStream: Future[Int] = jobGraph.execute(Empty)
  val result = Await.result(futuresStream, Duration(10, TimeUnit.SECONDS))
  println(s"Result: $result")
  jobManager.shutdown()
}
