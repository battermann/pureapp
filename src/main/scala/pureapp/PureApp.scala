package pureapp

import cats.Monad
import cats.data.StateT
import cats.effect.IO

trait PureApp {
  type Model

  type Msg

  type Cmd

  def init: (Model, Cmd)

  def update(msg: Msg, model: Model): (Model, Cmd)

  def io(model: Model, cmd: Cmd): IO[Msg]

  val quit: Option[Msg]

  final def run(`init`: (Model, Cmd) = init): IO[Unit] = {

    type Terminate = Boolean

    val state: StateT[IO, (Model, Cmd), Terminate] = StateT[IO, (Model, Cmd), Terminate] {
      case (model, cmd) =>
        io(model,cmd).map { msg =>
          val newModel = update(msg, model)
          (newModel, quit.contains(msg))
        }
    }

    Monad[StateT[IO, (Model, Cmd), ?]]
      .iterateUntil(state)(terminate => terminate)
      .runA(`init`)
      .map(_ => ())
  }

  def runl(args: List[String]): IO[Unit] = run()

  final def main(args: Array[String]): Unit = {
    runl(args.toList).unsafeRunSync()
  }
}
