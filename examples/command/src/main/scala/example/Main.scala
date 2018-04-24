package example

import cats.effect.IO
import com.github.battermann.pureapp._
import cats.implicits._
import com.github.battermann.pureapp.interpreters.{FileSystem, Terminal}

object Main extends StandardPureApp[IO] {

  // MODEL

  type Model = Option[Either[String, String]]

  sealed trait Msg
  final case class ReadFromFile(fileName: String) extends Msg
  final case class FileContentResult(result: Either[Throwable, String])
      extends Msg
  case object Quit extends Msg

  sealed trait Cmd
  object Cmd {
    case object Empty                               extends Cmd
    final case class ReadFromFile(fileName: String) extends Cmd
  }

  def init: (Model, Cmd) = (None, Cmd.Empty)

  def quit(msg: Msg): Boolean = msg == Quit

  // UPDATE

  def update(msg: Msg, model: Model): (Model, Cmd) =
    msg match {
      case Quit => (model, Cmd.Empty)

      case FileContentResult(Right(content)) =>
        (content.asRight.some, Cmd.Empty)

      case FileContentResult(Left(err)) =>
        (err.getMessage.asLeft.some, Cmd.Empty)

      case ReadFromFile(fileName) => (None, Cmd.ReadFromFile(fileName))
    }

  // IO

  def io(model: Model, cmd: Cmd): IO[Msg] =
    cmd match {
      case Cmd.Empty =>
        model match {
          case None =>
            Terminal.putStr("enter a file name> ") *> Terminal.readLine map ReadFromFile

          case Some(Right(content)) =>
            Terminal.putStrLn(content) map (_ => Quit)

          case Some(Left(err)) =>
            Terminal.putStrLn(s"error: $err") map (_ => Quit)
        }

      case Cmd.ReadFromFile(fileName) =>
        FileSystem.readLines(fileName) map (_.map(_.mkString("\n"))) map FileContentResult
    }
}
