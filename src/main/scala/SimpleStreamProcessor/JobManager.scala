package SimpleStreamProcessor

import SimpleLazyListProcessor.Node

import scala.concurrent.{ExecutionContext, Future}

class JobManager(numTaskManagers: Int, numSlotsPerTaskManager: Int)(implicit ec: ExecutionContext) {
  private val taskManagers = (1 to numTaskManagers).map(_ => new TaskManager(numSlotsPerTaskManager))

  def runJob[I, O](source: StreamSource[I], node: Node[I, O], input: List[I])(implicit ec: ExecutionContext): Future[Unit] = {
    // divide the input data into partitions
    val partitions = input.grouped(input.size / numTaskManagers).toList

    // for each partition, assign it to a task manager
    partitions.zip(taskManagers).foreach { case (partition, taskManager) =>
      val newSource = source.copy(partition)
      newSource.connectTo(node)
      taskManager.runJob(newSource, node)
    }

    // finally, start all task managers
    Future.sequence(taskManagers.map(_.start())).map(_ => ())
  }
}
