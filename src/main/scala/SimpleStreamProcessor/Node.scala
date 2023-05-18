package SimpleStreamProcessor

sealed trait Node[I, O] {
  protected var nodeName: String = "Node"

  def run(input: Stream[I]): Stream[O]

  def map[O2](f: O => O2): Node[I, O2] = Pipe(this, f).withName(this.nodeName + ".map")

  def flatMap[O2](f: O => Stream[O2]): Node[I, O2] = FlatMapPipe(this, f).withName(this.nodeName + ".flatMap")

  def filter(f: O => Boolean): Node[I, O] = FilterPipe(this, f).withName(this.nodeName + ".filter")

  def toSink(f: (O, O) => O, zero: O): Sink[I, O] = Sink(this, f, zero).withName(this.nodeName + ".toSink")

  def withName(name: String): this.type = {
    nodeName = name;
    this
  }

  override def toString: String = nodeName
}

case class Source[I](stream: Stream[I]) extends Node[Unit, I] {
  def run(input: Stream[Unit]): Stream[I] = stream

  override def toString: String = super.toString
}

case class Pipe[I, O, O2](upstream: Node[I, O], f: O => O2) extends Node[I, O2] {
  def run(input: Stream[I]): Stream[O2] = upstream.run(input).map(f)

  override def toString: String = super.toString + "(" + upstream + ")"
}

case class FlatMapPipe[I, O, O2](upstream: Node[I, O], f: O => Stream[O2]) extends Node[I, O2] {
  def run(input: Stream[I]): Stream[O2] = upstream.run(input).flatMap(f)

  override def toString: String = super.toString + "(" + upstream + ")"
}

case class FilterPipe[I, O](upstream: Node[I, O], f: O => Boolean) extends Node[I, O] {
  def run(input: Stream[I]): Stream[O] = upstream.run(input).filter(f)

  override def toString: String = super.toString + "(" + upstream + ")"
}

case class Sink[I, O](upstream: Node[I, O], f: (O, O) => O, zero: O, name: String = "Sink") {
  def run(input: Stream[I]): O = upstream.run(input).fold(zero)(f)

  def withName(newName: String): Sink[I, O] = this.copy(name = newName)

  override def toString: String = s"$name($upstream)"
}
