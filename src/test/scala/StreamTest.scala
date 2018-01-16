import java.nio.charset.Charset

import StreamTest.PDU
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import com.github.rgafiyatullin.akka_stream_scodec.SCFlow
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Seconds, Span}
import scodec.Codec
import scodec.codecs
import scodec.bits._

import scala.collection.immutable.Queue
import scala.concurrent.Future

object StreamTest {
  final case class PDU(i: Int, s: String)
  val encoding: Charset = Charset.forName("utf8")
  val pduCodec: Codec[PDU] =
    (codecs.literals.constantByteVectorCodec(hex"010203") :: codecs.int32 :: codecs.string32(encoding)).as[PDU]
}

class StreamTest extends FlatSpec with Matchers with ScalaFutures {
  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(Span(5, Seconds), Span(100, Milliseconds))

  def withActorSystem[T](f: ActorSystem => Future[T]): Future[T] = {
    val actorSystem = ActorSystem()
    try f(actorSystem)
    finally {
      actorSystem.terminate()
      ()
    }
  }

  def withMaterializer[T](f: Materializer => Future[T]): Future[T] =
    withActorSystem { actorSystem =>
      val mat: Materializer =
        ActorMaterializer()(actorSystem)
      f(mat)
    }

  def run(): Future[_] = withMaterializer { mat =>
    val pdus: List[PDU] = (1 to 100).map(i => PDU(i, i.toString)).toList
    val resultFuture =
      Source(pdus)
        .via(SCFlow.encode(StreamTest.pduCodec))
        .fold(ByteString.empty)(_ ++ _)
        .via(SCFlow.decode(StreamTest.pduCodec))
        .runFold(Queue.empty[PDU])(_ enqueue _)(mat)

    resultFuture.map(_ should be (pdus))(mat.executionContext)
  }

  "SC" should "encode and decode using codecs" in {
    whenReady(run())(identity)
  }
}
