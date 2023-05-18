package SimpleStreamProcessor

import scala.concurrent.{ExecutionContext, Future, Promise}

class TaskManager(totalSlots: Int)(implicit executionContext: ExecutionContext) {
  private var availableSlots: Int = totalSlots

  def submit[A](job: () => A): Future[A] = synchronized {
    if (availableSlots <= 0) throw new Exception("No available slots in the Task Manager")
    availableSlots -= 1
    val promise = Promise[A]()
    executionContext.execute(() => {
      try {
        promise.success(job())
      } catch {
        case e: Throwable => promise.failure(e)
      } finally {
        availableSlots += 1
      }
    })
    promise.future
  }

  def getAvailableSlots: Int = synchronized {
    availableSlots
  }

}
