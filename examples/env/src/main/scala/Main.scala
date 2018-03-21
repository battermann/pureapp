import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.effect.IO
import com.github.battermann.pureapp.{EnvPureApp, Terminal}
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.api.libs.ws.DefaultBodyReadables._
import scala.concurrent.ExecutionContext.Implicits._
import cats.implicits._

object Main extends EnvPureApp[IO] {

  // MODEL

  type Model = String

  final case class Env(wsClient: StandaloneAhcWSClient, system: ActorSystem)

  sealed trait Msg
  case object Quit extends Msg

  sealed trait Cmd
  object Cmd {
    case object Empty      extends Cmd
    case object GetRequest extends Cmd
  }

  def init: (Model, Cmd) = ("http://www.google.com", Cmd.GetRequest)

  // UPDATE

  def update(msg: Msg, model: Model): (Model, Cmd) =
    (model, Cmd.Empty)

  // IO

  def env: IO[Env] = IO {
    implicit val sys = ActorSystem()
    implicit val mat = ActorMaterializer()
    Env(StandaloneAhcWSClient(), sys)
  }

  override def dispose(env: Env): IO[Unit] = IO {
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

  def io(model: Model, cmd: Cmd, env: Env): IO[Msg] =
    cmd match {
      case Cmd.GetRequest =>
        call(env.wsClient, model)
          .map(_.leftMap(_.getMessage).merge)
          .flatMap(Terminal.putStrLn) map (_ => Quit)

      case Cmd.Empty => Quit.pure[IO]
    }

  val quit: Option[Msg] = Some(Quit)
}
