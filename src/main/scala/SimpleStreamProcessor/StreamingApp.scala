//package SimpleStreamProcessor
//
//
//import SimpleLazyListProcessor.{FileSink, FileSource, JobGraph}
//
//import scala.collection.immutable.TreeSeqMap.Empty
//import scala.concurrent.duration.Duration
//import scala.concurrent.impl.Promise
//import scala.concurrent.{Await, Future}
////
////object StreamingApp extends App {
////
////  //  val source: Source[Int] = Source[Int](Stream.fromList((1 to 9999).toList)).withName("source")
////  //  val pipe1: Pipe[Unit, Int, Int] = Pipe(source, (i: Int) => i * 2).withName("pipe1")
////  //  val pipe2: FilterPipe[Unit, Int] = FilterPipe(pipe1, (i: Int) => i % 2 == 0).withName("pipe2")
////  //  val sink: Sink[Unit, Int] = Sink(pipe2, (acc: Int, i: Int) => acc + i, 0).withName("sink")
////
////  val sink =
////    new Source[Int](LazyList.from((1 to 9999))).withName("source")
////      .map((i: Int) => i * 2).withName("pipe1")
////      .filter((i: Int) => i % 2 == 0).withName("pipe2")
////      .toSink((acc: Int, i: Int) => acc + i, 0).withName("sink")
////
////  val jobManager = new JobManager(1, 16) // 10 TaskManagers, each with 2 slots
////  val jobGraph = new JobGraph[Unit, Int](sink, jobManager)
////
////  //  jobGraph.execute2(Empty)
////
////  jobGraph.printTopology()
////  val futuresStream: Future[Int] = jobGraph.execute(LazyList.empty)
////  val result = Await.result(futuresStream, Duration(10, TimeUnit.SECONDS))
////  println(s"Result: $result")
////  jobManager.shutdown()
////}
//
//
//object StreamingApp extends App {
//  val fileSource = new FileSource[Int]("source.txt", line => line.toInt)
//  val fileSink = new FileSink[Int]("sink.txt")
//
//  val sourceNode = fileSource
//    .map(i => i * 2).withName("pipe1")
//    .filter(i => i % 2 == 0).withName("pipe2")
//
//  val sinkNode = sourceNode.toSink((acc: Int, i: Int) => acc + i, 0).withName("sink")
//
//  val jobManager = new JobManager(1, 16) // 1 TaskManager, with 16 slots
//  val jobGraph = JobGraph(sinkNode, jobManager)
//
//  jobGraph.printTopology()
//
//  val futureResult: Future[Int] = jobGraph.execute(LazyList.empty)
////  val result = Await.result(futureResult, Duration.Inf)
//
//  futureResult match {
//    case promise: Promise.DefaultPromise[_] => println("Write successful")
//    case Future.never => println("Write successful")
//    case _ => println("Write successful")
//  }
////  futureResult match {
////    case Success(_) => println("Write successful")
////    case Failure(e) => println(s"Write failed with error: ${e.getMessage}")
////  }
//
//  jobManager.shutdown()
//}