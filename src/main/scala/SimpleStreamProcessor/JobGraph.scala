package SimpleStreamProcessor

import scala.concurrent.Future

case class JobGraph[I, O](root: Sink[I, O], jobManager: JobManager) {

 
  def execute(input: Stream[I]): Future[O] = {
    jobManager.submit(() => root.run(input))
  }

  def execute2(input: Stream[I]): O = {
    root.run(input)
  }

  def printTopology(): Unit = {
    renderTopology().foreach(println)
  }

  def renderTopology(): List[String] = {
    val sinkLine = s"Sink(${root.name})"
    sinkLine :: renderNode(root.upstream, depth = 1)
  }

  private def renderNode(node: Node[_, _], depth: Int): List[String] = {
    val indent = "  " * depth

    node match {
      case source: Source[_] =>
        List(s"${indent}Source(${source.name})")

      case pipe: Pipe[_, _, _] =>
        s"${indent}Pipe(${pipe.name})" :: renderNode(pipe.upstream, depth + 1)

      case pipe: FlatMapPipe[_, _, _] =>
        s"${indent}FlatMapPipe(${pipe.name})" :: renderNode(pipe.upstream, depth + 1)

      case pipe: FilterPipe[_, _] =>
        s"${indent}FilterPipe(${pipe.name})" :: renderNode(pipe.upstream, depth + 1)

      case pipe: RecoverPipe[_, _] =>
        s"${indent}RecoverPipe(${pipe.name})" :: renderNode(pipe.upstream, depth + 1)

      case pipe: RecoverWithPipe[_, _] =>
        s"${indent}RecoverWithPipe(${pipe.name})" :: renderNode(pipe.upstream, depth + 1)

      case pipe: ParMapPipe[_, _, _] =>
        s"${indent}ParMapPipe(${pipe.name})" :: renderNode(pipe.upstream, depth + 1)

      case pipe: AsyncBoundaryPipe[_, _] =>
        s"${indent}AsyncBoundaryPipe(${pipe.name})" :: renderNode(pipe.upstream, depth + 1)

      case pipe: CountWindowPipe[_, _] =>
        s"${indent}CountWindowPipe(${pipe.name})" :: renderNode(pipe.upstream, depth + 1)

      case pipe: WatermarkPipe[_, _] =>
        s"${indent}WatermarkPipe(${pipe.name})" :: renderNode(pipe.upstream, depth + 1)

      case pipe: EventTimeWindowPipe[_, _] =>
        s"${indent}EventTimeWindowPipe(${pipe.name})" :: renderNode(pipe.upstream, depth + 1)

      case source: ManagedSource[_, _] =>
        List(s"${indent}ManagedSource(${source.name})")

      case _ =>
        List(s"${indent}Node(${node.name})")
    }
  }
}
