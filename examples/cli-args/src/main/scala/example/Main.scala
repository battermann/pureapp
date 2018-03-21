package example

import cats.effect.IO
import cats.implicits._
import com.github.battermann.pureapp._

object Main extends SimplePureApp[IO] {

  // MODEL

  type Model = String

  type Msg = Unit

  def init: Model = "hello pureapp"

  override val quit: Option[Msg] = Some(())

  override def runl(args: List[String]) =
    run(s"args: [${args.mkString(", ")}]")

  // UPDATE

  def update(msg: Msg, model: Model): Model = model

  // IO

  def io(model: Model): IO[Msg] =
    IO { println(model) }.void
}
