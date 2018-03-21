package example

import cats.effect.IO
import cats.implicits._
import com.github.battermann.pureapp.{FileSystem, PureApp, Terminal}

import scala.util.Try

object Main extends PureApp[IO] {

  // MODEL

  private val fileName = "todos.csv"

  sealed trait Todo
  final case class Active(name: String)    extends Todo
  final case class Completed(name: String) extends Todo

  sealed trait Status
  final case class Error(msg: String) extends Status
  final case class Info(msg: String)  extends Status

  final case class Model(
      todos: List[Todo],
      status: Option[Status] = None
  )

  sealed trait Msg
  final case class LoadResult(result: Either[String, List[Todo]]) extends Msg
  final case class Add(name: String)                              extends Msg
  final case class Delete(id: Int)                                extends Msg
  final case class MarkCompleted(id: Int)                         extends Msg
  case object InvalidInput                                        extends Msg
  case object Save                                                extends Msg
  final case class SaveResult(result: Either[String, Unit])       extends Msg
  case object Quit                                                extends Msg

  sealed trait Cmd
  object Cmd {
    case object Empty                                         extends Cmd
    final case class Save(fileName: String, list: List[Todo]) extends Cmd
    final case class Load(fileName: String)                   extends Cmd
  }

  def init: (Model, Cmd) = (Model(Nil), Cmd.Load(fileName))

  override val quit: Option[Msg] = Some(Quit)

  // UPDATE

  def update(msg: Msg, model: Model): (Model, Cmd) =
    msg match {

      case LoadResult(Right(list)) =>
        (Model(list, Some(Info("successfully loaded todos from file"))),
         Cmd.Empty)

      case LoadResult(Left(err)) =>
        (model.copy(
           status = Some(Info(s"could not load todos from file. $err"))),
         Cmd.Empty)

      case Add(name) =>
        (Model(model.todos :+ Active(name), Some(Info("item added"))),
         Cmd.Empty)

      case Delete(id) =>
        (Model(
           model.todos.zipWithIndex.filter { case (_, i) => i != id }.map(_._1),
           Some(Info("item deleted"))),
         Cmd.Empty)

      case MarkCompleted(id) =>
        val updatedList = model.todos.zipWithIndex
          .map {
            case (Active(name), i) if i == id =>
              (Completed(name), i)
            case todo => todo
          }
          .map(_._1)
        (Model(updatedList, Some(Info("marked as completed"))), Cmd.Empty)

      case InvalidInput =>
        (model.copy(status = Some(Error("invalid input"))), Cmd.Empty)

      case Save =>
        (model, Cmd.Save(fileName, model.todos))

      case SaveResult(Right(())) =>
        (model.copy(status = Some(Info("saved successfully"))), Cmd.Empty)

      case SaveResult(Left(err)) =>
        (model.copy(status = Some(Error(err))), Cmd.Empty)

      case Quit => (model, Cmd.Empty)
    }

  // IO

  def parse(input: String): Msg = {
    if (input == "q") Quit
    else if (input == "s") Save
    else {
      Try {
        val cmd   = input.substring(0, input.indexOf(' ')).trim
        val value = input.substring(input.indexOf(' ')).trim
        cmd match {
          case "a" => Add(value)
          case "d" => Delete(value.toInt - 1)
          case "c" => MarkCompleted(value.toInt - 1)
        }
      }.getOrElse(InvalidInput)
    }
  }

  def printUsage: IO[Unit] =
    Terminal.putStrLn("""usage:
                        | 'a <name>' adds a new todo
                        | 'd <id>' deletes a todo
                        | 'c <id>' marks todo as completed
                        | 's' to save
                        | 'q' to quit
                        | """.stripMargin)

  def getAndParseInput: IO[Msg] =
    Terminal.putStr(">>> ").flatMap(_ => Terminal.readLine.map(parse))

  def formatList(list: List[Todo]): String =
    if (list.isEmpty) "no todos"
    else
      list.zipWithIndex
        .map {
          case (Active(name), i)    => s"${i + 1}. [active] $name"
          case (Completed(name), i) => s"${i + 1}. [completed] $name"
        }
        .mkString("\n")

  def decode(lines: List[String]): Either[String, List[Todo]] =
    Try(
      lines.map(_.split(",").map(_.trim)).map(arr => (arr(0), arr(1))).flatMap {
        case ("a", name) => Some(Active(name))
        case ("c", name) => Some(Completed(name))
        case _           => None
      }).toEither.leftMap(_.getMessage)

  def encode(list: List[Todo]): String =
    list
      .map {
        case Active(name)    => s"a, $name"
        case Completed(name) => s"c, $name"
      }
      .mkString("\n")

  def io(model: Model, cmd: Cmd): IO[Msg] =
    cmd match {
      case Cmd.Empty =>
        model.status match {
          case Some(Info(_)) | None =>
            for {
              _ <- Terminal.putStrLn(
                s"\n## TODOS\n\n${formatList(model.todos)}\n")
              _ <- printUsage
              _ <- model.status
                .map(s => s"[$s]")
                .map(Terminal.putStrLn)
                .getOrElse(IO.unit)
              msg <- getAndParseInput
            } yield msg

          case Some(Error(err)) =>
            Terminal.putStrLn(s"\n[$err]").flatMap(_ => getAndParseInput)
        }

      case Cmd.Save(fn, content) =>
        FileSystem
          .save(fn, encode(content))
          .map(_.leftMap(_.getMessage))
          .map(SaveResult)

      case Cmd.Load(fn) =>
        FileSystem
          .readLines(fn)
          .map(_.leftMap(_.getMessage).flatMap(decode))
          .map(LoadResult)
    }
}
