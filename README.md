# pureapp

A library for writing purely functional, easy to reason about, and stack-safe sequential programs inspired by [Elm](http://elm-lang.org/), [scalm](https://github.com/julienrf/scalm), and scalaz's [SafeApp](https://github.com/scalaz/scalaz/blob/bffbbcf366ca3a33dad6b3c10683228b20812bcf/effect/src/main/scala/scalaz/effect/SafeApp.scala)

## overview

The architecture for pureapp applications is mainly inspired by the [Elm Architecture](https://guide.elm-lang.org/architecture/).

A program consists of three components:

### model

Represents the immutable application state.

### update

A way to update the application's state. `update` is a function that takes a `Model` and a `Msg` and returns a new `Model` (a new application state).

### io

`io` is a function that describe all side effects of an application.

Unlike Elm and scalm, pureapp applications do not have a `view` function. Instead `io` is responsible for printing and reading from the standard input/output as well as other side effects.

`io` takes a `Model` and returns an `IO[Msg]`. Additionally you can pass immutable, pure values of type `Cmd` that represent commands to perform other side effects than printing and reading.

The internal pureapp implementation will feed the message inside the `IO` value back into the `update` function. However, this is hidden from the user and we do not have to worry about this.

## termination

To control when to terminate a pureapp application we define a value `quit` of type `Option[Msg]`. If this special value is returned from `io` the program will terminate. If `quit` is `None` the application will not terminate.

## example

How to use pureapp can best be demonstrated with an example. Here is the pureapp version of the [Elm counter example](http://elm-lang.org/examples/buttons):

```scala
import pureapp._
import cats.effect.IO

object Main extends App {

  PureApp.simple(init, update, io, Some(Quit))

  type Model = Int

  sealed trait Msg
  case object Increment    extends Msg
  case object Decrement    extends Msg
  case object InvalidInput extends Msg
  case object Quit         extends Msg

  def init: Model = 42

  def update(msg: Msg, model: Model): Model =
    msg match {
      case Increment    => model + 1
      case Decrement    => model - 1
      case Quit         => model
      case InvalidInput => model
    }

  def io(model: Model): IO[Msg] =
    for {
      _     <- Terminal.putStrLn(model.toString)
      _     <- Terminal.putStrLn("enter: +, -, or q")
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

```

An example that involves other side effects like e.g. reading and writing to files can be seen here: [TodoList](https://github.com/battermann/pureapp/blob/master/examples/todolist/src/main/scala/example/Main.scala). To achieve this we have to define a custom `Cmd` type and implement our own interpreters accordingly.

## Internals

Internally pureapp uses an instance of `StateT[IO, (Model, Cmd), Boolean]` where the `Boolean` value indicates termination. The program loop is implemented with `iterateUntil` which is stack safe. And the state is run with the initial `Model` and `Cmd`.

Also we do not have to run our program. This is handled internally by calling `unsafeRunSync` on the `IO` value inside the state monad stack similar to scalaz's SafeApp.
