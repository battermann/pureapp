package com.github.battermann.pureapp

import cats.data.StateT
import cats.effect.{Effect, IO}
import cats.implicits._

final case class Program[F[_]: Effect, Model, Msg, Cmd, Env](
    env: F[Env],
    init: (Model, Cmd),
    update: (Msg, Model) => (Model, Cmd),
    io: (Model, Cmd, Env) => F[Msg],
    quit: Option[Msg],
    dispose: Env => F[Unit]
) {
  def run(): F[Msg] = {

    val app = StateT[F, (Model, Cmd, Env), Msg] {
      case (model, cmd, env_) =>
        io(model, cmd, env_).map { msg =>
          val newModel = update(msg, model)
          ((newModel._1, newModel._2, env_), msg)
        }
    }.iterateUntil(msg => quit.contains(msg))

    for {
      dependency <- env
      msg        <- app.runA((init._1, init._2, dependency))
      _          <- dispose(dependency)
    } yield msg
  }
}

object Program {
  def simple[F[_]: Effect, Model, Msg](
      init: Model,
      update: (Msg, Model) => Model,
      io: Model => F[Msg],
      quit: Option[Msg]): Program[F, Model, Msg, Unit, Unit] =
    Program(Effect[F].unit,
            (init, ()),
            (msg, model) => (update(msg, model), ()),
            (model, _, _) => io(model),
            quit,
            _ => Effect[F].unit)

  def program[F[_]: Effect, Model, Msg, Cmd](
      init: (Model, Cmd),
      update: (Msg, Model) => (Model, Cmd),
      io: (Model, Cmd) => F[Msg],
      quit: Option[Msg]): Program[F, Model, Msg, Cmd, Unit] =
    Program(Effect[F].unit,
            init,
            update,
            (model, cmd, _) => io(model, cmd),
            quit,
            _ => Effect[F].unit)
}

abstract class MainApp[F[_]: Effect] {

  type Msg

  def runl(args: List[String]): F[Msg]

  final def main(args: Array[String]): Unit = {
    Effect[F]
      .runAsync(runl(args.toList)) {
        case Left(err) => Terminal.putStrLn(err.getMessage)
        case Right(_)  => IO.unit
      }
      .unsafeRunSync()
  }
}

abstract class EnvPureApp[F[_]: Effect] extends MainApp[F] {
  type Env

  type Model

  type Msg

  type Cmd

  def env: F[Env]

  def init: (Model, Cmd)

  def update(msg: Msg, model: Model): (Model, Cmd)

  def io(model: Model, cmd: Cmd, env: Env): F[Msg]

  val quit: Option[Msg]

  def dispose(env: Env): F[Unit] = Effect[F].unit

  override def runl(args: List[String]): F[Msg] = run()

  final def run(_init: (Model, Cmd) = init, _env: F[Env] = env): F[Msg] = {
    Program(env, _init, update, io, quit, dispose).run()
  }
}

abstract class PureApp[F[_]: Effect] extends MainApp[F] {
  type Model

  type Msg

  type Cmd

  type Env = Unit

  def init: (Model, Cmd)

  def update(msg: Msg, model: Model): (Model, Cmd)

  def io(model: Model, cmd: Cmd): F[Msg]

  val quit: Option[Msg]

  override def runl(args: List[String]): F[Msg] = run()

  final def run(_init: (Model, Cmd) = init): F[Msg] = {
    Program.program(_init, update, io, quit).run()
  }
}

abstract class SimplePureApp[F[_]: Effect] extends MainApp[F] {
  type Model

  type Msg

  type Env = Unit

  type Cmd = Unit

  def init: Model

  def update(msg: Msg, model: Model): Model

  def io(model: Model): F[Msg]

  val quit: Option[Msg]

  override def runl(args: List[String]): F[Msg] = run()

  final def run(_init: Model = init): F[Msg] = {
    Program.simple(_init, update, io, quit).run()
  }
}
