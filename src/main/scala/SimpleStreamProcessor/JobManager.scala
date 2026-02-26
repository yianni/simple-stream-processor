package SimpleStreamProcessor

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}

class JobManager(numberOfTaskManagers: Int, slotsPerTaskManager: Int) {
  private val executorServices = IndexedSeq.fill(numberOfTaskManagers)(Executors.newFixedThreadPool(slotsPerTaskManager))
  private val taskManagers: IndexedSeq[TaskManager] = executorServices.map(es => new TaskManager(slotsPerTaskManager)(ExecutionContext.fromExecutorService(es)))

  def submit[A](job: () => A): Future[A] = {
    val managersByAvailability = taskManagers.sortBy(tm => -tm.getAvailableSlots)
    managersByAvailability.iterator
      .map { taskManager =>
        try Some(taskManager.submit(job))
        catch {
          case _: IllegalStateException => None
        }
      }
      .collectFirst { case Some(future) => future }
      .getOrElse(Future.failed(new IllegalStateException("No available slots in any Task Manager")))
  }

  def shutdown(): Unit = executorServices.foreach(_.shutdown())
}
