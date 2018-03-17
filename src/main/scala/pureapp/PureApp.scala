package pureapp

import cats.data.StateT
import cats.effect.{Effect, IO}
import cats.implicits._

abstract class PureApp[F[_]: Effect] {
  type Model

  type Msg

  type Cmd

  def init: (Model, Cmd)

  def update(msg: Msg, model: Model): (Model, Cmd)

  def io(model: Model, cmd: Cmd): F[Msg]

  val quit: Option[Msg]

  final def run(_init: (Model, Cmd) = init): F[Unit] = {
    type Terminate = Boolean

    StateT[F, (Model, Cmd), Terminate] {
      case (model, cmd) =>
        io(model, cmd).map { msg =>
          val newModel = update(msg, model)
          (newModel, quit.contains(msg))
        }
    }.iterateUntil(terminate => terminate)
      .runA(_init)
      .void
  }

  def runl(args: List[String]): F[Unit] = run()

  final def main(args: Array[String]): Unit = {
    Effect[F].runAsync(runl(args.toList)){
      case Left(err) => Terminal.putStrLn(err.getMessage)
      case Right(_) => IO.unit
    }.unsafeRunSync()
  }
}

object PureApp {
  def simple[F[_]: Effect, _Model, _Msg](
      _init: _Model,
      _update: (_Msg, _Model) => _Model,
      _io: _Model => F[_Msg],
      _quit: Option[_Msg]
  ): Unit = {
    val app = new PureApp[F] {
      def init: (_Model, Unit) = (_init, ())

      def update(msg: _Msg, model: _Model): (_Model, Unit) =
        (_update(msg, model), ())

      def io(model: _Model, cmd: Unit): F[_Msg] =
        _io(model)

      type Msg   = _Msg
      type Model = _Model
      type Cmd   = Unit
      val quit: Option[_Msg] = _quit
    }
    app.main(Array())
  }
}
