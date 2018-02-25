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

  final def run(_init: (Model, Cmd) = init): IO[Unit] = {
    type Terminate = Boolean

    val state: StateT[IO, (Model, Cmd), Terminate] =
      StateT[IO, (Model, Cmd), Terminate] {
        case (model, cmd) =>
          io(model, cmd).map { msg =>
            val newModel = update(msg, model)
            (newModel, quit.contains(msg))
          }
      }

    Monad[StateT[IO, (Model, Cmd), ?]]
      .iterateUntil(state)(terminate => terminate)
      .runA(_init)
      .map(_ => ())
  }

  def runl(args: List[String]): IO[Unit] = run()

  final def main(args: Array[String]): Unit = {
    runl(args.toList).unsafeRunSync()
  }
}

object PureApp {
  def simple[_Model, _Msg](
      _init: _Model,
      _update: (_Msg, _Model) => _Model,
      _io: _Model => IO[_Msg],
      _quit: Option[_Msg]
  ): Unit = {
    val app = new PureApp {
      def init: (_Model, Unit) = (_init, Unit)

      def update(msg: _Msg, model: _Model): (_Model, Unit) =
        (_update(msg, model), Unit)

      def io(model: _Model, cmd: Unit): IO[_Msg] =
        _io(model)

      type Msg   = _Msg
      type Model = _Model
      type Cmd   = Unit
      val quit: Option[_Msg] = _quit
    }
    app.main(Array())
  }
}
