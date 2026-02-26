package SimpleStreamProcessor

import java.util.concurrent.BlockingQueue

sealed trait Stream[+A] {

  import Stream._

  import scala.annotation.tailrec

  def map[B](f: A => B): Stream[B] = this match {
    case Emit(a, next) =>
      try Emit(f(a), () => next().map(f))
      catch {
        case e: Throwable => Error(e)
      }
    case Halt() => Halt()
    case Empty => Empty
    case Error(e) => Error(e)
  }

  def flatMap[B](f: A => Stream[B]): Stream[B] = this match {
    case Emit(a, next) =>
      try f(a) append next().flatMap(f)
      catch {
        case e: Throwable => Error(e)
      }
    case Halt() => Halt()
    case Empty => Empty
    case Error(e) => Error(e)
  }

  def filter(f: A => Boolean): Stream[A] = this match {
    case Emit(a, next) =>
      try {
        if (f(a)) Emit(a, () => next().filter(f))
        else next().filter(f)
      } catch {
        case e: Throwable => Error(e)
      }
    case Halt() => Halt()
    case Empty => Empty
    case Error(e) => Error(e)
  }

  def append[B >: A](that: => Stream[B]): Stream[B] = this match {
    case Emit(a, next) => Emit(a, () => next().append(that))
    case Halt() => that
    case Empty => that
    case Error(e) => Error(e)
  }

  def fold[B](z: B)(f: (B, A) => B): B = {
    @tailrec
    def go(s: Stream[A], acc: B): B = s match {
      case Emit(a, next) => go(next(), f(acc, a))
      case Halt() => acc
      case Empty => acc
      case Error(e) => throw e
    }

    go(this, z)
  }

  def foreach(f: A => Unit): Unit = this match {
    case Emit(a, next) =>
      f(a)
      next().foreach(f)
    case Halt() =>
    case Empty =>
    case Error(e) => throw e
  }

  final def toList: List[A] = this match {
    case Emit(a, next) => a :: next().toList
    case Halt() => Nil
    case Empty => Nil
    case Error(e) => throw e
  }

  def recover[B >: A](f: PartialFunction[Throwable, B]): Stream[B] =
    recoverWith {
      case e if f.isDefinedAt(e) => Emit(f(e), () => Halt())
    }

  def recoverWith[B >: A](f: PartialFunction[Throwable, Stream[B]]): Stream[B] = this match {
    case Emit(a, next) => Emit(a, () => next().recoverWith(f))
    case Halt() => Halt()
    case Empty => Empty
    case Error(e) if f.isDefinedAt(e) => f(e)
    case Error(e) => Error(e)
  }

}

object Stream {
  case class Emit[A](a: A, next: () => Stream[A]) extends Stream[A]

  case class Halt[A]() extends Stream[A]

  case object Empty extends Stream[Nothing]

  case class Error(e: Throwable) extends Stream[Nothing]

  def fromList[A](list: List[A]): Stream[A] = list match {
    case Nil => Empty
    case h :: t => Emit(h, () => fromList(t))
  }

  def fromQueue[A](queue: BlockingQueue[A]): Stream[A] = {
    if (queue.isEmpty) Empty
    else Emit(queue.poll(), () => fromQueue(queue))
  }

}
