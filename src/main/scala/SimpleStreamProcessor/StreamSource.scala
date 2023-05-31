package SimpleStreamProcessor

import SimpleLazyListProcessor.Node

import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

abstract class StreamSource[I] {
  protected var downstream: Option[Node[I, _]] = None

  def connectTo[O](node: Node[I, O]): Node[I, O] = {
    downstream = Some(node)
    node
  }

  protected def produce(): Option[I]

  def start()(implicit ec: ExecutionContext): Future[Unit]
}

case object EndOfStream

class DataStream[T](implicit val ec: ExecutionContext) extends StreamSource[T] {
  private val dataQueue: BlockingQueue[Either[EndOfStream.type, T]] = new LinkedBlockingQueue[Either[EndOfStream.type, T]]

  override def connectTo[O](node: Node[T, O]): Node[T, O] = {
    downstream = Some(node)
    node
  }

  def addData(data: T): Unit = {
    println(s"Adding data: $data")
    dataQueue.put(Right(data))
  }

  def signalEndOfStream(): Unit = {
    dataQueue.put(Left(EndOfStream))
  }

  override protected def produce(): Option[T] = {
    val data = dataQueue.poll()
    data match {
      case Right(data) => Some(data)
      case Left(EndOfStream) =>
        println("End of stream reached.")
        None
      case null => None
    }
  }

  override def start()(implicit ec: ExecutionContext): Future[Unit] = Future {
    var running = true
    while (running) {
      produce() match {
        case Some(data) =>
          downstream.foreach(_.processElement(data).onComplete {
            case Success(_) => println(s"Processing data: $data")
            case Failure(e) => println(s"Failed to process data: $data, with error: ${e.getMessage}")
          })
        case None =>
          running = false
      }
    }
  }
}
