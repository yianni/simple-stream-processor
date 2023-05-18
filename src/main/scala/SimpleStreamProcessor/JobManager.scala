package SimpleStreamProcessor

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}

class JobManager(numberOfTaskManagers: Int, slotsPerTaskManager: Int) {
  private val executorServices = IndexedSeq.fill(numberOfTaskManagers)(Executors.newFixedThreadPool(slotsPerTaskManager))
  private val taskManagers: IndexedSeq[TaskManager] = executorServices.map(es => new TaskManager(slotsPerTaskManager)(ExecutionContext.fromExecutorService(es)))

  private def leastLoadedTaskManager: TaskManager = taskManagers.minBy(_.getAvailableSlots)

  def submit[A](job: () => A): Future[A] = leastLoadedTaskManager.submit(job)

  def shutdown(): Unit = executorServices.foreach(_.shutdown())
}