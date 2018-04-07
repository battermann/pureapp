package example

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.effect.IO
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.api.libs.ws.DefaultBodyReadables._
import scala.concurrent.ExecutionContext.Implicits._
import cats.implicits._

import com.github.battermann.pureapp._
import com.github.battermann.pureapp.interpreters._

object Main extends PureApp[IO] {

  // MODEL

  type Model = String

  final case class Resource(wsClient: StandaloneAhcWSClient, system: ActorSystem)

  sealed trait Msg
  case object Quit extends Msg

  sealed trait Cmd
  object Cmd {
    case object Empty      extends Cmd
    case object GetRequest extends Cmd
  }

  def init: (Model, Cmd) = ("http://www.google.com", Cmd.GetRequest)

  def quit: Option[Msg] = Some(Quit)

  // UPDATE

  def update(msg: Msg, model: Model): (Model, Cmd) =
    (model, Cmd.Empty)

  // IO

  def acquire: IO[Resource] = IO {
    implicit val sys = ActorSystem()
    implicit val mat = ActorMaterializer()
    Resource(StandaloneAhcWSClient(), sys)
  }

  def dispose(env: Resource): IO[Unit] = IO {
    env.wsClient.close()
    env.system.terminate()
  }

  def call(wsClient: StandaloneWSClient,
           url: String): IO[Either[Throwable, String]] =
    IO.fromFuture {
      IO {
        wsClient.url(url).get().map { response ⇒
          response.body[String]
        }
      }
    }.attempt

  def io(model: Model, cmd: Cmd, env: Resource): IO[Msg] =
    cmd match {
      case Cmd.GetRequest =>
        call(env.wsClient, model)
          .map(_.leftMap(_.getMessage).merge)
          .flatMap(Terminal.putStrLn) map (_ => Quit)

      case Cmd.Empty => Quit.pure[IO]
    }
}
