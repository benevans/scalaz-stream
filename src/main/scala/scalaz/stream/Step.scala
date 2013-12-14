package scalaz.stream

import scalaz.{\/-, -\/, \/}
import scalaz.stream.Process._
import scalaz.Free.Trampoline

/**
 * Represents an intermediate step of a `Process`, including any current
 * emitted values, a next state, and the current finalizer / cleanup `Process`.
 */
case class Step[+F[_],+A](
  head: Throwable \/ Seq[A],
  tail: Process[F,A],
  cleanup: Process[F,A]) {
  def headOption: Option[Seq[A]] = head.toOption

  def fold[R](success: Seq[A] => R)(fallback: => R, error: => R): R =
    head.fold(e => if (e == Process.End) fallback else error, success)

  def isHalted = tail.isHalt
  def isCleaned = isHalted && cleanup.isHalt
}

object Step {
  def failed(e:Throwable) = Step(-\/(e),Halt(e),halt)
  val done = Step(-\/(End),halt,halt)
  def fromProcess[F[_],A](p:Process[F,A]):Step[F,A] = Step(\/-(Nil),p,halt)
}

/** trampolined version of step **/
case class Step_[+F[_],+A](
  head: Throwable \/ Seq[A]
  , tail: Trampoline[Process[F,A]]
  , cleanup: Throwable => Trampoline[Process[F,A]]) {

  def fold[R](success: Seq[A] => R, fallback: => R, error: Throwable => R): R = {
    head.fold({
      case  End => fallback
      case  e => error(e)
    }
    , success)
  }

}

object Step_ {

  object next {

    def unapply[F[_],A](s:Step_[F,A]) : Option[(Seq[A],Trampoline[Process[F,A]])] =  s.head match {
      case \/-(a) => Some((a,s.tail))
      case _ => None
    }

  }

  object failed {
    def unapply[F[_],A](s:Step_[F,A]) : Option[(Throwable,Trampoline[Process[F,A]])] =  s.head match {
      case -\/(e) => Some((e,s.cleanup(e)))
      case _ => None
    }
  }


}