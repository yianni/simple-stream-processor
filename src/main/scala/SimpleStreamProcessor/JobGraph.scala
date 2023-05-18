package SimpleStreamProcessor

import scala.concurrent.Future

case class JobGraph[I, O](root: Sink[I, O], jobManager: JobManager) {

 
  def execute(input: Stream[I]): Future[O] = {
    jobManager.submit(() => root.run(input))
  }

  def execute2(input: Stream[I]): O = {
    root.run(input)
  }

  def printTopology(): Unit = {
    println(root.toString)
  }
}