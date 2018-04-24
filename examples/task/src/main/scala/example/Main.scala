package example

import com.github.battermann.pureapp._
import cats.implicits._
import monix.execution.Scheduler.Implicits.global
import monix.eval.Task

object Main extends SimplePureApp[Task] {

  // MODEL

  type Model = String

  type Msg = Unit

  def init: Model = "hello monix task"

  def quit(msg: Msg): Boolean = true

  // UPDATE

  def update(msg: Msg, model: Model): Model = model

  // IO

  def io(model: Model): Task[Msg] =
    Task { println(model) }.void
}
