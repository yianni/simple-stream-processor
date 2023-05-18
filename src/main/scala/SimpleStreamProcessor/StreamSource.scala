package SimpleStreamProcessor

import SimpleLazyListProcessor.Node

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

abstract class StreamSource[I] {
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

  protected def produce(): Option[I]
}

class FiniteStreamSource[I](data: List[I]) extends StreamSource[I] {
  private val iterator = data.iterator

  override def produce(): Option[I] = {
    if (iterator.hasNext) Some(iterator.next())
    else None
  }

  def copy(data: List[I]): FiniteStreamSource[I] = {
    new FiniteStreamSource(data)
  }
}

class IntegerSource extends StreamSource[Int] {
  override protected def produce(): Option[Int] = Some(Random.nextInt(100))
}

class FiniteIntegerSource(data: List[Int]) extends FiniteStreamSource[Int](data)

class StreamPartition[I](data: List[I]) extends StreamSource[I] {
  private var index = 0

  override protected def produce(): Option[I] = {
    if (index < data.length) {
      val element = data(index)
      index += 1
      Some(element)
    } else {
      None
    }
  }
}