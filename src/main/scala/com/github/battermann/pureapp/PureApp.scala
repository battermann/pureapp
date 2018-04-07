package com.github.battermann.pureapp

import cats.data.StateT
import cats.effect.{Effect, IO}
import cats.implicits._
import com.github.battermann.pureapp.interpreters.Terminal

final case class Program[F[_]: Effect, Model, Msg, Cmd, Resource, Result](
    acquire: F[Resource],
    init: (Model, Cmd),
    update: (Msg, Model) => (Model, Cmd),
    io: (Model, Cmd, Resource) => F[Msg],
    quit: Option[Msg],
    dispose: Resource => F[Unit],
    mkResult: Model => Result
) {
  def build(): F[Result] = {

    val app = StateT[F, (Model, Cmd, Resource), Msg] {
      case (model, cmd, resource) =>
        io(model, cmd, resource).map { msg =>
          val (updatedModel, newCmd) = update(msg, model)
          ((updatedModel, newCmd, resource), msg)
        }
    }

    for {
      resource <- acquire
      (finalState, _, _) <- app
        .iterateUntil(quit.contains(_))
        .runS((init._1, init._2, resource))
      _ <- dispose(resource)
    } yield
      quit
        .map(update(_, finalState))
        .fold(mkResult(finalState)) { case (m, _) => mkResult(m) }
  }

  def withResult[A](mkResult: Model => A)
    : Program[F, Model, Msg, Cmd, Resource, A] =
    Program(acquire, init, update, io, quit, dispose, mkResult)
}

object Program {
  def simple[F[_]: Effect, Model, Msg](
      init: Model,
      update: (Msg, Model) => Model,
      io: Model => F[Msg],
      quit: Option[Msg]
  ): Program[F, Model, Msg, Unit, Unit, Unit] =
    Program(Effect[F].unit,
            (init, ()),
            (msg, model) => (update(msg, model), ()),
            (model, _, _) => io(model),
            quit,
            _ => Effect[F].unit,
            _ => ())

  def standard[F[_]: Effect, Model, Msg, Cmd](
      init: (Model, Cmd),
      update: (Msg, Model) => (Model, Cmd),
      io: (Model, Cmd) => F[Msg],
      quit: Option[Msg]): Program[F, Model, Msg, Cmd, Unit, Unit] =
    Program(Effect[F].unit,
            init,
            update,
            (model, cmd, _) => io(model, cmd),
            quit,
            _ => Effect[F].unit,
            _ => ())
}

abstract class PureProgram[F[_]: Effect] {
  type Resource

  type Model

  type Msg

  type Cmd

  def acquire: F[Resource]

  def init: (Model, Cmd)

  def update(msg: Msg, model: Model): (Model, Cmd)

  def io(model: Model, cmd: Cmd, env: Resource): F[Msg]

  def quit: Option[Msg]

  def dispose(env: Resource): F[Unit]

  val program: Program[F, Model, Msg, Cmd, Resource, Unit] =
    Program(acquire, init, update, io, quit, dispose, (_: Model) => ())
}

abstract class StandardPureProgram[F[_]: Effect] {
  type Model

  type Msg

  type Cmd

  def init: (Model, Cmd)

  def update(msg: Msg, model: Model): (Model, Cmd)

  def io(model: Model, cmd: Cmd): F[Msg]

  def quit: Option[Msg]

  val program: Program[F, Model, Msg, Cmd, Unit, Unit] =
    Program.standard(init, update, io, quit)
}

abstract class SimplePureProgram[F[_]: Effect] {
  type Model

  type Msg

  def init: Model

  def update(msg: Msg, model: Model): Model

  def io(model: Model): F[Msg]

  def quit: Option[Msg]

  val program: Program[F, Model, Msg, Unit, Unit, Unit] =
    Program.simple(init, update, io, quit)
}

abstract class PureApp[F[_]: Effect] extends PureProgram[F] {

  def runl(args: List[String]): F[Unit] = run()

  def run(_init: (Model, Cmd) = init): F[Unit] =
    program.copy(init = _init).build()

  final def main(args: Array[String]): Unit =
    Effect[F]
      .runAsync(runl(args.toList)) {
        case Left(err) => Terminal.putStrLn(err.toString)
        case Right(_)  => IO.unit
      }
      .unsafeRunSync()
}

abstract class StandardPureApp[F[_]: Effect] extends StandardPureProgram[F] {
  def runl(args: List[String]): F[Unit] = run()

  def run(_init: (Model, Cmd) = init): F[Unit] =
    program.copy(init = _init).build()

  final def main(args: Array[String]): Unit =
    Effect[F]
      .runAsync(runl(args.toList)) {
        case Left(err) => Terminal.putStrLn(err.toString)
        case Right(_)  => IO.unit
      }
      .unsafeRunSync()
}

abstract class SimplePureApp[F[_]: Effect] extends SimplePureProgram[F] {
  def runl(args: List[String]): F[Unit] = run()

  def run(_init: Model = init): F[Unit] =
    program.copy(init = (_init, ())).build()

  final def main(args: Array[String]): Unit =
    Effect[F]
      .runAsync(runl(args.toList)) {
        case Left(err) =>
          err.printStackTrace()
          Terminal.putStrLn(err.toString)
        case Right(_) => IO.unit
      }
      .unsafeRunSync()
}

abstract class SafeApp[F[_]: Effect] {
  def runl(args: List[String]): F[Unit] = run

  def run: F[Unit] = Effect[F].unit

  final def main(args: Array[String]): Unit =
    Effect[F]
      .runAsync(runl(args.toList)) {
        case Left(err) =>
          err.printStackTrace()
          Terminal.putStrLn(err.toString)
        case Right(_) => IO.unit
      }
      .unsafeRunSync()
}
