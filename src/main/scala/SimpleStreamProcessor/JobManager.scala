package SimpleStreamProcessor

import SimpleLazyListProcessor.Node

import scala.concurrent.{ExecutionContext, Future}

class JobManager(numTaskManagers: Int, numSlotsPerTaskManager: Int)(implicit ec: ExecutionContext) {
  private val taskManagers = (1 to numTaskManagers).map(_ => new TaskManager(numSlotsPerTaskManager))

  def runJob[I, O](source: StreamSource[I], node: Node[I, O]): Future[Unit] = {
    taskManagers.foreach { taskManager =>
      taskManager.runJob(source, node)
    }

    // finally, start all task managers
    Future.sequence(taskManagers.map(_.start())).map(_ => ())
//    Future.successful(())
  }
}
