package SimpleStreamProcessor

import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{Callable, ExecutorCompletionService, Executors, TimeUnit}
import scala.concurrent.ExecutionContext

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
      case Error(e) =>
        Metrics.incUnhandledError()
        throw e
    }

    go(this, z)
  }

  def foreach(f: A => Unit): Unit = this match {
    case Emit(a, next) =>
      f(a)
      next().foreach(f)
    case Halt() =>
    case Empty =>
    case Error(e) =>
      Metrics.incUnhandledError()
      throw e
  }

  final def toList: List[A] = this match {
    case Emit(a, next) => a :: next().toList
    case Halt() => Nil
    case Empty => Nil
    case Error(e) =>
      Metrics.incUnhandledError()
      throw e
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

  def parMap[B](parallelism: Int)(f: A => B)(implicit executionContext: ExecutionContext): Stream[B] = {
    if (parallelism <= 0) return Error(new IllegalArgumentException("parallelism must be > 0"))

    def takeBatch(s: Stream[A], remaining: Int, acc: List[A]): (List[A], Stream[A]) = {
      if (remaining <= 0) (acc.reverse, s)
      else s match {
        case Emit(a, next) => takeBatch(next(), remaining - 1, a :: acc)
        case Halt() => (acc.reverse, Halt())
        case Empty => (acc.reverse, Empty)
        case Error(e) => throw e
      }
    }

    def runBatch(batch: List[A]): List[B] = {
      val cancellationToken = RuntimeControl.currentToken
      val collector = Metrics.currentCollector
      val executor = Executors.newFixedThreadPool(parallelism)
      val completion = new ExecutorCompletionService[(Int, Either[Throwable, B])](executor)
      val results = Array.fill[Option[B]](batch.size)(None)

      try {
        batch.zipWithIndex.foreach {
          case (value, index) =>
            completion.submit(new Callable[(Int, Either[Throwable, B])] {
              override def call(): (Int, Either[Throwable, B]) = {
                if (cancellationToken.exists(_.isCancelled)) {
                  (index, Left(new java.util.concurrent.CancellationException("Pipeline cancelled")))
                } else {
                  collector.incParMapInFlight()
                  try {
                    (index, Right(f(value)))
                  } catch {
                    case e: Throwable => (index, Left(e))
                  } finally {
                    collector.decParMapInFlight()
                  }
                }
              }
            })
        }

        var completed = 0
        while (completed < batch.size) {
          if (cancellationToken.exists(_.isCancelled)) {
            throw new java.util.concurrent.CancellationException("Pipeline cancelled")
          }

          val future = completion.poll(100, TimeUnit.MILLISECONDS)
          if (future != null) {
            val (index, result) = future.get()
            result match {
              case Right(mapped) => results(index) = Some(mapped)
              case Left(error) => throw error
            }
            completed += 1
          }
        }

        results.iterator.collect { case Some(value) => value }.toList
      } finally {
        executor.shutdownNow()
      }
    }

    def loop(s: Stream[A]): Stream[B] = s match {
      case Halt() => Halt()
      case Empty => Empty
      case Error(e) => Error(e)
      case _ =>
        try {
          val (batch, rest) = takeBatch(s, parallelism, Nil)
          if (batch.isEmpty) Halt()
          else Stream.fromList(runBatch(batch)).append(loop(rest))
        } catch {
          case e: Throwable => Error(e)
        }
    }

    try {
      loop(this)
    } catch {
      case e: Throwable => Error(e)
    }
  }

  def ensuring(finalizer: () => Unit): Stream[A] = {
    val closed = new AtomicBoolean(false)

    def closeOnce(): Option[Throwable] = {
      if (closed.compareAndSet(false, true)) {
        try {
          finalizer()
          None
        } catch {
          case e: Throwable => Some(e)
        }
      } else None
    }

    def go(s: Stream[A]): Stream[A] = s match {
      case Emit(a, next) =>
        Emit(a, () => {
          try go(next())
          catch {
            case e: Throwable =>
              closeOnce().foreach(e.addSuppressed)
              Error(e)
          }
        })
      case Halt() =>
        closeOnce() match {
          case Some(closeError) => Error(closeError)
          case None => Halt()
        }
      case Empty =>
        closeOnce() match {
          case Some(closeError) => Error(closeError)
          case None => Empty
        }
      case Error(e) =>
        closeOnce().foreach(e.addSuppressed)
        Error(e)
    }

    go(this)
  }

  def grouped(size: Int): Stream[List[A]] = {
    if (size <= 0) return Error(new IllegalArgumentException("group size must be > 0"))

    def takeChunk(s: Stream[A], remaining: Int, acc: List[A]): (List[A], Stream[A]) = {
      if (remaining <= 0) (acc.reverse, s)
      else s match {
        case Emit(a, next) => takeChunk(next(), remaining - 1, a :: acc)
        case Halt() => (acc.reverse, Halt())
        case Empty => (acc.reverse, Empty)
        case Error(e) => throw e
      }
    }

    this match {
      case Halt() => Halt()
      case Empty => Empty
      case Error(e) => Error(e)
      case _ =>
        try {
          val (chunk, rest) = takeChunk(this, size, Nil)
          if (chunk.isEmpty) Halt()
          else Emit(chunk, () => rest.grouped(size))
        } catch {
          case e: Throwable => Error(e)
        }
    }
  }

  def takeUntilCancelled(token: CancellationToken): Stream[A] = {
    if (token.isCancelled) Halt()
    else this match {
      case Emit(a, next) => Emit(a, () => next().takeUntilCancelled(token))
      case Halt() => Halt()
      case Empty => Empty
      case Error(e) => Error(e)
    }
  }

  def iterator: Iterator[A] = new Iterator[A] {
    private var current: Stream[A] = Stream.this
    private var nextValue: Option[A] = None
    private var done = false

    private def advance(): Unit = {
      if (done || nextValue.nonEmpty) return
      current match {
        case Emit(a, next) =>
          nextValue = Some(a)
          current = next()
        case Halt() => done = true
        case Empty => done = true
        case Error(e) =>
          done = true
          Metrics.incUnhandledError()
          throw e
      }
    }

    override def hasNext: Boolean = {
      advance()
      nextValue.nonEmpty
    }

    override def next(): A = {
      advance()
      nextValue match {
        case Some(value) =>
          nextValue = None
          value
        case None => throw new NoSuchElementException("next on empty iterator")
      }
    }
  }

}

object Stream {
  case class Emit[A](a: A, next: () => Stream[A]) extends Stream[A]

  case class Halt[A]() extends Stream[A]

  case object Empty extends Stream[Nothing]

  case class Error(e: Throwable) extends Stream[Nothing]

  sealed trait QueueSignal[+A]
  case class QueueValue[A](value: A) extends QueueSignal[A]
  case object QueueEnd extends QueueSignal[Nothing]
  case class QueueError(e: Throwable) extends QueueSignal[Nothing]

  def fromList[A](list: List[A]): Stream[A] = list match {
    case Nil => Empty
    case h :: t => Emit(h, () => fromList(t))
  }

  def fromQueue[A](queue: BlockingQueue[A]): Stream[A] = {
    if (queue.isEmpty) Empty
    else Emit(queue.poll(), () => fromQueue(queue))
  }

  def fromBlockingQueue[A](queue: BlockingQueue[QueueSignal[A]], onSignal: QueueSignal[A] => Unit = (_: QueueSignal[A]) => ()): Stream[A] = {
    try {
      val signal = queue.take()
      onSignal(signal)
      signal match {
        case QueueValue(value) =>
          Metrics.setBoundaryQueueDepth(queue.size())
          Emit(value, () => fromBlockingQueue(queue, onSignal))
        case QueueEnd =>
          Metrics.setBoundaryQueueDepth(queue.size())
          Halt()
        case QueueError(e) =>
          Metrics.setBoundaryQueueDepth(queue.size())
          Error(e)
      }
    } catch {
      case e: Throwable => Error(e)
    }
  }

}
