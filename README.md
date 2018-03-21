# PureApp

A principled and opinionated library for writing purely functional, easy to reason about, and stack-safe sequential programs partly inspired by [Elm](http://elm-lang.org/), [scalm](https://github.com/julienrf/scalm), and scalaz's [SafeApp](https://github.com/scalaz/scalaz/blob/bffbbcf366ca3a33dad6b3c10683228b20812bcf/effect/src/main/scala/scalaz/effect/SafeApp.scala)

## installtion

    libraryDependencies += "com.github.battermann" %% "pureapp" % "0.1.1"

## overview

The architecture for PureApp applications is mainly inspired by the [Elm Architecture](https://guide.elm-lang.org/architecture/).

A program consists of three components:

### model

The model represents the immutable application state.

### update

A way to update the application's state. `update` is a function that takes a `Model` (the current application state) and a `Msg` and returns a new `Model` (a new application state).

### io

`io` is a function that describes all side effects of an application.

Unlike Elm and scalm, PureApp applications do not have a `view` function. Instead `io` is responsible for printing and reading from the standard input/output as well as for other side effects.

`io` takes a `Model` and returns an `F[Msg]`. Where `F[_]` has an instance of [`Effect[F]`](https://typelevel.org/cats-effect/typeclasses/effect.html). Additionally you can pass immutable, pure values of type `Cmd` that represent commands to perform other side effects than just printing and reading.

Internally the `Msg` that is returned from `io` and wrapped inside an `F[_]` together with the current `Model` is fed back into the `update` function. However, this is hidden from the user and we do not have to worry about this.

## termination

To control when to terminate a PureApp application we define a value `quit` of type `Option[Msg]`. If this special value is returned from `io` the program will terminate. When `quit` is `None` the application will not terminate.

## example

How to use PureApp can best be demonstrated with an example. Here is the PureApp version of the [Elm counter example](http://elm-lang.org/examples/buttons):

```scala
import com.github.battermann.pureapp._
import cats.effect.IO

object Main extends SimplePureApp[IO] {

  // MODEL

  type Model = Int

  sealed trait Msg
  case object Increment    extends Msg
  case object Decrement    extends Msg
  case object InvalidInput extends Msg
  case object Quit         extends Msg

  def init: Model = 42

  val quit = Some(Quit)

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
      _     <- Terminal.putStrLn(model.toString)
      _     <- Terminal.putStr("enter: +, -, or q> ")
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

## three different patterns

PureApp supports three different patterns:

### SimplePureApp

A simple program (like the counter example from above) knows only models and messages. We can create a simple program by extending from the `SimplePureApp[F_]]` class.

### PureApp

A *normal* program which extends `PureApp[F[_]]` also supports commands. Normally printing to and reading from the console can be done based on the `Model` (the application state). If we want to perform other side effecting actions, we often can't or don't want to do this based on the application state. Instead we can use commands that represent requests for performing such tasks. The `io` function then becomes the interpreter for our commands as [this example](examples/command/src/main/scala/example/Main.scala) demonstrates.

### EnvPureApp

A program that can create and dispose resources in a referentially transparent way has to extend from the `EnvPureApp[F[_]]` class. The type `Env` represents an environment containing disposable resources and other things that do not belong into the domain model (like e.g. a configuration). We have to provide an implementation for `def env: F[Env]` and we can override  `def dispose(env: Env): F[Unit]` to dispose resources.

The `io` function of an `EnvPureApp` provides an additional parameter of type `Env` that we can now use while interpreting our commands. [Here is an example](examples/env/src/main/scala/Main.scala) with an HTTP client.


## minimal working skeleton

The main object has to extend from one of the three abstract classes mentioned above. 

Then the types `Model` and `Msg` have to be defined. Depending on which pattern we use we might have to define `Cmd` and `Env` as well.

Usually `Msg` and `Cmd` will be implemented as sum types.

Finally all abstract methods have to be implemented.

Here is a minimal working skeleton to get started:

```scala
import pureapp._
import cats.effect.IO

object Main extends PureApp[IO] {

  // MODEL

  type Model = String

  type Msg = Unit

  type Cmd = Unit

  def init: (Model, Cmd) = ("Hello PureApp!", ())

  override val quit: Option[Msg] = Some(())

  // UPDATE

  def update(msg: Msg, model: Model): (Model, Cmd) = (model, ())

  // IO

  def io(model: Model, cmd: Cmd): IO[Msg] =
    Terminal.putStrLn(model)
}
```

An example that is a little more involved can be found here: [TodoList](https://github.com/battermann/pureapp/blob/master/examples/todolist/src/main/scala/example/Main.scala).
## command line args

To use command line arguments we have to override the `runl(args: List[String])` method. And the call `run(_init: (Model, Cmd))` manually. Now we can use `args` for creating the initial `Model` and `Cmd` e.g. like this:

```scala
object Main extends PureApp[IO] {

  override def runl(args: List[String]) =
	run((Model(args = args), Cmd.Empty))
	
  // ...
}  
```

## internals

Internally pureapp uses an instance of `StateT[F, (Model, Cmd), Boolean]` where the `Boolean` value indicates termination. The program loop is implemented with `iterateUntil` which is stack safe. And the state is run with the initial `Model` and `Cmd`.

Also we do not have to run our program. This is handled internally. The given effect is evaluated in the context of `F[_]` to an `IO[Unit]`. Which is then run with `unsafeRunSync` similar to scalaz's SafeApp.
