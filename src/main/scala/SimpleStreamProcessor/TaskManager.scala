package SimpleStreamProcessor

import SimpleLazyListProcessor.Node

import scala.concurrent.{ExecutionContext, Future}

class TaskManager(numSlots: Int)(implicit ec: ExecutionContext) {
  private var tasks: List[Future[Unit]] = Nil

  def runJob[I, O](source: StreamSource[I], node: Node[I, O]): Future[Unit] = {
    source.connectTo(node)
    val task = source.start()
    tasks = task :: tasks
    task
  }

  def start(): Future[List[Unit]] = {
    // start all tasks
    Future.sequence(tasks)
  }

  def stop(): Future[List[Unit]] = {
    // wait for all tasks to complete
    Future.sequence(tasks)
  }
}
