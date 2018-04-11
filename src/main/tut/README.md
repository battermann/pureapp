# PureApp

A principled and opinionated library for writing purely functional, easy to reason about, and stack-safe sequential programs partly inspired by [Elm](http://elm-lang.org/), [scalm](https://github.com/julienrf/scalm), and scalaz's [SafeApp](https://github.com/scalaz/scalaz/blob/bffbbcf366ca3a33dad6b3c10683228b20812bcf/effect/src/main/scala/scalaz/effect/SafeApp.scala)

## installtion

    libraryDependencies += "com.github.battermann" %% "pureapp" % "0.3.1"

## overview

The architecture for PureApp applications is mainly inspired by the [Elm Architecture](https://guide.elm-lang.org/architecture/).

An Idiomatic PureApp program is completely pure and referentially transparent.

It can be either implemented as the main application or it can be composed of other PureApp programs (see below).

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

To control when to terminate a PureApp application we define `def quit: Option[Msg]`. If the `Msg` defined as `Some` for `quit` is returned from the `io` function, the program will terminate. When `quit` is `None` the application will not terminate.

## example

How to use PureApp can best be demonstrated with an example. Here is the PureApp version of the [Elm counter example](http://elm-lang.org/examples/buttons):

```tut:book
import com.github.battermann.pureapp._
import com.github.battermann.pureapp.interpreters.Terminal._
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

  def quit = Some(Quit)

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
      _     <- putStrLn(model.toString)
      _     <- putStr("enter: +, -, or q> ")
      input <- readLine
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

### StandardPureApp

A *standard* program which extends `StandardPureApp[F[_]]` also supports commands. Normally printing to and reading from the console can be done based on the `Model` (the application state). If we want to perform other side effecting actions, we often can't or don't want to do this based on the application state. Instead we can use commands that represent requests for performing such tasks. The `io` function then becomes the interpreter for our commands as [this example](examples/command/src/main/scala/example/Main.scala) demonstrates.

### PureApp

A program that can create and dispose resources in a referentially transparent way has to extend the `PureApp[F[_]]` class. The type `Resource` represents an environment containing disposable resources and other things that do not belong into the domain model (like e.g. a configuration). We have to provide an implementation for `def acquire: F[Resource]` and we can override  `def dispose(resource: Resource): F[Unit]` to dispose resources.

The `io` function of an `PureApp ` provides an additional parameter of type `PureApp ` that we can now use while interpreting our commands. [Here is an example](examples/env/src/main/scala/Main.scala) uses an HTTP client as a resource.


## minimal working skeleton

To create a minimal working skeleton the main object of an application has to extend one of the three abstract classes mentioned above:

- `SimplePureApp[F[_]]`
- `StandardPureApp[F[_]]`
- or `PureApp[F[_]]`

Then the types `Model` and `Msg` have to be defined. Depending on which pattern we use we might have to define `Cmd` and `Resource` as well.

Usually `Msg` and `Cmd` will be implemented as sum types.

Finally all abstract methods have to be implemented:

- `init`
- `update`
- `io`
- `quit` (if we want the program to terminate)

And optionally:

- `acquire`
- `dispose`

Here is a minimal working skeleton to get started:

```tut:book
object Main extends StandardPureApp[IO] {

  // MODEL

  type Model = String

  type Msg = Unit

  type Cmd = Unit

  def init: (Model, Cmd) = ("Hello PureApp!", ())

  def quit: Option[Msg] = Some(())

  // UPDATE

  def update(msg: Msg, model: Model): (Model, Cmd) = (model, ())

  // IO

  def io(model: Model, cmd: Cmd): IO[Msg] =
    putStrLn(model)
}

Main.main(Array())
```

An example that is a little more involved can be found here: [TodoList](https://github.com/battermann/pureapp/blob/master/examples/todolist/src/main/scala/example/Main.scala).

## command line args

To use command line arguments we have to override the `runl(args: List[String])` method. And the call `run(_init: (Model, Cmd))` manually. Now we can use `args` for creating the initial `Model` and `Cmd` e.g. like this:

```tut:book:silent:fail
object Main extends StandardPureApp[IO] {
  
  override def runl(args: List[String])
	run((Model(args = args), Cmd.Empty))
	
  // ...
}
```

## composability

PureApp programs are pure, immutable values represented by the case class `Program[F[_]: Effect, Model, Msg, Cmd, Resource, A]`.

There are different constructors for the three different flavours described above:

- `Program.simple(...)`
- `Program.standard(...)`
- or `Program.apply(...)`

By default, the final result of a program is `F[Model]`, the final application state. If we need our program to return something else we can map over it with `map` and pass a function `f: A => B`. 

To finally create a composable program, we have to transform it to it's representation in the context of it's effect type `F[_]` by calling `build()`. Note that this will not run the program.

Now we have all the compositional capabilities at hand that the type `F[_]` offers.

Here is a (not very meaningful) example of showing the technique of composing programs:

```tut:book
import cats.implicits._

val p1 = Program.simple(
	  "Hello PureApp 1!",
	  (_: Unit, model: String) => model,
	  (_: String) => IO.unit,
	  Some(())
  ).map(List(_)).build()
  
val p2 = Program.simple(
	  "Hello PureApp 2!",
	  (_: Unit, model: String) => model,
	  (_: String) => IO.unit,
	  Some(())
  ).map(List(_)).build()
  
val program = p1 |+| p2

program.unsafeRunSync()
```

Alternatively and for convenience, instead of using the constructors we can implement one of the three abstract classes:

- `SimplePureProgram[F_]`
- `StandardPureProgram[F_]`
- or `PureProgram[F_]`

Here is how to apply this approach to the example from above:

```tut:book
object Hello1 extends SimplePureProgram[IO] {
  type Model = String
  type Msg = Unit
  def init: Model = "Hello PureApp 1!"
  def quit: Option[Msg] = ().some
  def update(msg: Msg, model: Model): Model = model
  def io(model: Model): IO[Msg] = IO.unit
}

object Hello2 extends SimplePureProgram[IO] {
  type Model = String
  type Msg = Unit
  def init: Model = "Hello PureApp 2!"
  def quit: Option[Msg] = ().some
  def update(msg: Msg, model: Model): Model = model
  def io(model: Model): IO[Msg] = IO.unit
}
```

Similar to scalaz, PureApp offers an abstract class `SafeApp[F[_]]` that provides an implementation of the `main` method by running a specified `Effect[F]`. We can use this to embed the composition of the two programs:

```tut:book
object Main extends SafeApp[IO] {

  val program =
    Hello1.program.map(List(_)).build() |+|
      Hello2.program.map(List(_)).build()  
      
  override def run: IO[Unit] =
    program.flatMap(v => putStrLn(v.toString))
}

Main.main(Array())
```


## internals

Internally PureApp uses an instance of `StateT[F, (Model, Cmd, Resource), Msg]`. The program loop is implemented with `iterateUntil` which is stack safe. And the state is run with the initial `Model` and `Cmd`.

Also we do not have to run our program. This is handled internally. The given effect is evaluated in the context of `F[_]` to an `IO[Unit]`. Which is then run with `unsafeRunSync` similar to scalaz's SafeApp.

## contributions

I'm happy for any kind of contributions whatsoever, be it comments, issues, or pull requests.
