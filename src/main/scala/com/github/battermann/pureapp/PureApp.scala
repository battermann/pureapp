package com.github.battermann.pureapp

import cats.data.StateT
import cats.effect._
import cats.implicits._
import com.github.battermann.pureapp.Program.{SimpleProgram, StandardProgram}

final case class Program[F[_]: Effect, Model, Msg, Cmd, Resource, A](
    acquire: F[Resource],
    init: (Model, Cmd),
    update: (Msg, Model) => (Model, Cmd),
    io: (Model, Cmd, Resource) => F[Msg],
    quit: Msg => Boolean,
    dispose: Resource => F[Unit],
    mkResult: Model => A
) {

  /** Transforms a program to it's representation in the context of it's effect type `F[_]` and maps the function `f` over the final value produced by the program. The program will not be run. */
  def buildMap[B](f: A => B): F[B] =
    this.map(f).build()

  /** Transforms a program to it's representation in the context of it's effect type `F[_]`. The program will not be run. */
  def build(): F[A] = {
    val app = StateT[F, (Model, Cmd, Resource), Msg] {
      case (model, cmd, resource) =>
        io(model, cmd, resource).map { msg =>
          val (updatedModel, newCmd) = update(msg, model)
          ((updatedModel, newCmd, resource), msg)
        }
    }

    val (initialModel, initialCmd) = init

    val finalModel = for {
      ((model, _, _), msg) <- Bracket[F, Throwable].bracket(acquire) {
        resource =>
          app
            .iterateUntil(quit)
            .run((initialModel, initialCmd, resource))
      } { resource =>
        dispose(resource)
      }
    } yield update(msg, model)._1

    finalModel.map(mkResult)
  }

  /** Maps the function `f` over the final value produced by the program. */
  def map[B](f: A => B): Program[F, Model, Msg, Cmd, Resource, B] =
    Program(acquire, init, update, io, quit, dispose, mkResult andThen f)

  /** Creates a new program with the target type `G[_]` by replacing its `acquire`, `io`, and `dispose` function. */
  def withIo[G[_]: Effect, R](
      acquire: G[R],
      io: (Model, Cmd, R) => G[Msg],
      dispose: R => G[Unit]): Program[G, Model, Msg, Cmd, R, A] =
    Program(acquire, init, update, io, quit, dispose, mkResult)
}

object Program {

  type StandardProgram[F[_], Model, Msg, Cmd, A] =
    Program[F, Model, Msg, Cmd, Unit, A]

  type SimpleProgram[F[_], Model, Msg, A] =
    Program[F, Model, Msg, Unit, Unit, A]

  /** Constructor for a simple program.*/
  def simple[F[_]: Effect, Model, Msg](
      init: Model,
      update: (Msg, Model) => Model,
      io: Model => F[Msg],
      quit: Msg => Boolean
  ): SimpleProgram[F, Model, Msg, Model] =
    Program(Effect[F].unit,
            (init, ()),
            (msg, model) => (update(msg, model), ()),
            (model, _, _) => io(model),
            quit,
            _ => Effect[F].unit,
            identity)

  /** Constructor for a standard program.*/
  def standard[F[_]: Effect, Model, Msg, Cmd](
      init: (Model, Cmd),
      update: (Msg, Model) => (Model, Cmd),
      io: (Model, Cmd) => F[Msg],
      quit: Msg => Boolean): StandardProgram[F, Model, Msg, Cmd, Model] =
    Program(Effect[F].unit,
            init,
            update,
            (model, cmd, _) => io(model, cmd),
            quit,
            _ => Effect[F].unit,
            identity)

  /** Creates a new standard program with the target type `G[_]` with the given `io` function. */
  implicit class WithIoStandard[F[_]: Effect, Model, Msg, Cmd, A](
      val p: StandardProgram[F, Model, Msg, Cmd, A]) {
    def withIoStandard[G[_]: Effect](
        io: (Model, Cmd) => G[Msg]): StandardProgram[G, Model, Msg, Cmd, A] =
      p.withIo(Effect[G].unit,
               (model, cmd, _) => io(model, cmd),
               _ => Effect[G].unit)
  }

  /** Creates a new simple program with the target type `G[_]` with the given `io` function. */
  implicit class WithIoSimple[F[_]: Effect, Model, Msg, A](
      val p: SimpleProgram[F, Model, Msg, A]) {
    def withIoSimple[G[_]: Effect](
        io: Model => G[Msg]): SimpleProgram[G, Model, Msg, A] =
      p.withIo(Effect[G].unit, (model, _, _) => io(model), _ => Effect[G].unit)
  }
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

  def quit(msg: Msg): Boolean

  def dispose(env: Resource): F[Unit]

  val program: Program[F, Model, Msg, Cmd, Resource, Model] =
    Program(acquire, init, update, io, quit, dispose, identity)
}

abstract class StandardPureProgram[F[_]: Effect] {
  type Model

  type Msg

  type Cmd

  def init: (Model, Cmd)

  def update(msg: Msg, model: Model): (Model, Cmd)

  def io(model: Model, cmd: Cmd): F[Msg]

  def quit(msg: Msg): Boolean

  val program: StandardProgram[F, Model, Msg, Cmd, Model] =
    Program.standard(init, update, io, quit)
}

abstract class SimplePureProgram[F[_]: Effect] {
  type Model

  type Msg

  def init: Model

  def update(msg: Msg, model: Model): Model

  def io(model: Model): F[Msg]

  def quit(msg: Msg): Boolean

  val program: SimpleProgram[F, Model, Msg, Model] =
    Program.simple(init, update, io, quit)
}

abstract class PureApp[F[_]: Effect] extends PureProgram[F] {

  def runl(args: List[String]): F[Unit] = run()

  def run(_init: (Model, Cmd) = init): F[Unit] =
    program.copy(init = _init).build().void

  final def main(args: Array[String]): Unit =
    Effect[F]
      .runAsync(runl(args.toList)) {
        case Left(err) =>
          IO { err.printStackTrace() }
        case Right(_) => IO.unit
      }
      .unsafeRunSync()
}

abstract class StandardPureApp[F[_]: Effect] extends StandardPureProgram[F] {
  def runl(args: List[String]): F[Unit] = run()

  def run(_init: (Model, Cmd) = init): F[Unit] =
    program.copy(init = _init).build().void

  final def main(args: Array[String]): Unit =
    Effect[F]
      .runAsync(runl(args.toList)) {
        case Left(err) =>
          IO { err.printStackTrace() }
        case Right(_) => IO.unit
      }
      .unsafeRunSync()
}

abstract class SimplePureApp[F[_]: Effect] extends SimplePureProgram[F] {
  def runl(args: List[String]): F[Unit] = run()

  def run(_init: Model = init): F[Unit] =
    program.copy(init = (_init, ())).build().void

  final def main(args: Array[String]): Unit =
    Effect[F]
      .runAsync(runl(args.toList)) {
        case Left(err) =>
          IO { err.printStackTrace() }
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
          IO { err.printStackTrace() }
        case Right(_) => IO.unit
      }
      .unsafeRunSync()
}
