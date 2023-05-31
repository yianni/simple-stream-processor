package SimpleLazyListProcessor

import scala.concurrent.{ExecutionContext, Future}

abstract class Node[I, O] {
  protected var downstream: Option[Node[O, _]] = None

  def processElement(input: I)(implicit ec: ExecutionContext): Future[Unit] = Future {
    val output = run(List(input)).headOption
    output.foreach { element =>
      downstream.foreach { node =>
        node.processElement(element)
      }
    }
  }

  protected def run(input: List[I]): List[O]

  def connectTo[O2](node: Node[O, O2]): Node[O, O2] = {
    downstream = Some(node)
    node
  }
}

class MultiplyByTwoNode extends Node[Int, Int] {
  override protected def run(input: List[Int]): List[Int] = input.map(_ * 2)
}

class PrintNode extends Node[Int, Unit] {
  override protected def run(input: List[Int]): List[Unit] = input.map(println)
}
