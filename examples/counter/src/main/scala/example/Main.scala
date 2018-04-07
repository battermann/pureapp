package example

import com.github.battermann.pureapp._
import cats.effect.IO
import com.github.battermann.pureapp.interpreters.Terminal

object Main extends SimplePureApp[IO] {

  // MODEL

  type Model = Int

  sealed trait Msg
  case object Increment extends Msg
  case object Decrement extends Msg
  case object InvalidInput extends Msg
  case object Quit extends Msg

  def init: Model = 42

  def quit = Some(Quit)

  // UPDATE

  def update(msg: Msg, model: Model): Model =
    msg match {
      case Increment    => model + 1
      case Decrement    => model - 1
      case Quit         => model
      case InvalidInput => model
    }

  // IO

  def io(model: Model): IO[Msg] =
    for {
      _ <- Terminal.putStrLn(model.toString)
      _ <- Terminal.putStr("enter: +, -, or q> ")
      input <- Terminal.readLine
    } yield {
      input match {
        case "+" => Increment
        case "-" => Decrement
        case "q" => Quit
        case _   => InvalidInput
      }
    }
}
