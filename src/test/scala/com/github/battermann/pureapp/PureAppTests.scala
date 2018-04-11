package com.github.battermann.pureapp

import cats.data.StateT
import cats.effect.IO
import cats.implicits._
import org.scalatest.{FunSuite, Matchers}

class PureAppTests extends FunSuite with Matchers {
  test("create a simple program and replace the interpreter") {
    val program =
      SimpleHello.program
        .withInterpreter(_ => StateT.pure[IO, String, Unit](()))
        .build()

    val expected = "Hello SimplePureProgram!"
    val actual   = program.runA("").unsafeRunSync()
    actual shouldEqual expected
  }

  test("create a standard program and replace the interpreter") {
    val program =
      StandardHello.program
        .withInterpreter((_, _) => StateT.pure[IO, String, Unit](()))
        .build()

    val expected = "Hello StandardPureProgram!"
    val actual   = program.runA("").unsafeRunSync()
    actual shouldEqual expected
  }

  object SimpleHello extends SimplePureProgram[IO] {
    type Model = String
    type Msg   = Unit
    def init: Model                           = "Hello SimplePureProgram!"
    def quit: Option[Msg]                     = ().some
    def update(msg: Msg, model: Model): Model = model
    def io(model: Model): IO[Msg]             = IO.unit
  }

  object StandardHello extends StandardPureProgram[IO] {
    type Model = String
    type Msg   = Unit
    type Cmd   = Unit
    def init: (Model, Cmd)                           = ("Hello StandardPureProgram!", ())
    def quit: Option[Msg]                            = ().some
    def update(msg: Msg, model: Model): (Model, Cmd) = (model, ())
    def io(model: Model, cmd: Cmd): IO[Msg]          = IO.unit
  }
}
