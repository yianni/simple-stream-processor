package SimpleStreamProcessor

import java.util.concurrent.BlockingQueue

sealed trait Stream[+A] {

  import Stream._

  import scala.annotation.tailrec

  def map[B](f: A => B): Stream[B] = this match {
    case Emit(a, next) => Emit(f(a), () => next().map(f))
    case Halt() => Halt()
    case Empty => Empty
  }

  def flatMap[B](f: A => Stream[B]): Stream[B] = this match {
    case Emit(a, next) => f(a) append next().flatMap(f)
    case Halt() => Halt()
    case Empty => Empty
  }

  def filter(f: A => Boolean): Stream[A] = this match {
    case Emit(a, next) if f(a) => Emit(a, () => next().filter(f))
    case Emit(_, next) => next().filter(f)
    case Halt() => Halt()
    case Empty => Empty
  }

  def append[B >: A](that: => Stream[B]): Stream[B] = this match {
    case Emit(a, next) => Emit(a, () => next().append(that))
    case Halt() => that
    case Empty => Empty
  }

  def fold[B](z: B)(f: (B, A) => B): B = {
    @tailrec
    def go(s: Stream[A], acc: B): B = s match {
      case Emit(a, next) => go(next(), f(acc, a))
      case Halt() => acc
      case Empty => acc
    }

    go(this, z)
  }

  def foreach(f: A => Unit): Unit = this match {
    case Emit(a, next) =>
      f(a)
      next().foreach(f)
    case Halt() =>
    case Empty =>
  }

  final def toList: List[A] = this match {
    case Emit(a, next) => a :: next().toList
    case Halt() => Nil
    case Empty => Nil
  }

}

object Stream {
  case class Emit[A](a: A, next: () => Stream[A]) extends Stream[A]

  case class Halt[A]() extends Stream[A]

  case object Empty extends Stream[Nothing]

  def fromList[A](list: List[A]): Stream[A] = list match {
    case Nil => Empty
    case h :: t => Emit(h, () => fromList(t))
  }

  def fromQueue[A](queue: BlockingQueue[A]): Stream[A] = {
    if (queue.isEmpty) Empty
    else Emit(queue.poll(), () => fromQueue(queue))
  }

}

