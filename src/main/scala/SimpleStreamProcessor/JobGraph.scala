//package SimpleLazyListProcessor
//
//import SimpleStreamProcessor.JobManager
//
//import scala.concurrent.Future
//import scala.sys.process.ProcessBuilder.Sink
//
//case class JobGraph[I, O](root: Sink[I, O], jobManager: JobManager) {
//
//  def execute(input: LazyList[I]): Future[Unit] = {
//    jobManager.runJob(root  , input)
//  }
//
//  def execute2(input: LazyList[I]): O = {
//    root.run(input)
//  }
//
//  def printTopology(): Unit = {
//    println(root.toString)
//  }
//}