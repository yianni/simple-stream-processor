package SimpleStreamProcessor

import SimpleLazyListProcessor.Node

import scala.concurrent.{ExecutionContext, Future}

abstract class StreamSource[I](protected var data: List[I]) {
  protected var downstream: Option[Node[I, _]] = None

  def connectTo[O](node: Node[I, O]): Node[I, O] = {
    downstream = Some(node)
    node
  }

  def start()(implicit ec: ExecutionContext): Future[Unit] = Future {
    while (true) {
      val data = produce()
      data.foreach { element =>
        downstream.foreach(_.processElement(element))
      }
    }
  }

  //  protected def produce(): Option[I]

  protected def produce(): Option[I] = {
    data match {
      case Nil => None
      case head :: tail =>
        data = tail
        Some(head)
    }
  }

  def copy(newData: List[I]): StreamSource[I]

}

class IntegerSource(data: List[Int]) extends StreamSource[Int](data) {
  override def copy(newData: List[Int]): StreamSource[Int] = new IntegerSource(newData)
}
