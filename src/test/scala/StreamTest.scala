import java.nio.charset.Charset

import StreamTest.PDU
import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.scaladsl.Source
import akka.stream._
import akka.util.ByteString
import com.github.rgafiyatullin.akka_stream_scodec.SCFlow
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Seconds, Span}
import scodec.Codec
import scodec.codecs
import scodec.bits._

import scala.collection.immutable.Queue
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

object StreamTest {
  import scodec.interop.akka._

  final case class PDU(i: Int, s: String)
  val encoding: Charset = Charset.forName("utf8")
  val pduCodec: Codec[PDU] =
    (codecs.literals.constantByteVectorCodec(hex"010203") :: codecs.int32 :: codecs.string32(encoding)).as[PDU]

  val frameCodec: Codec[ByteString] =
    codecs.variableSizeBytes(codecs.int32, codecs.bytes)
      .xmap(_.toByteString, _.toByteVector)
}

class StreamTest extends FlatSpec with Matchers with ScalaFutures {
  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(Span(30, Seconds), Span(100, Milliseconds))

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

  def run[Item](items: List[Item], codec: Codec[Item])(regroupWith: ByteString => List[ByteString]): Future[_] =
    withMaterializer { implicit mat =>
      implicit val ec: ExecutionContext = mat.executionContext

      val loggingAttributes = Attributes.logLevels(onElement = Logging.WarningLevel, onFinish = Logging.WarningLevel, onFailure = Logging.WarningLevel)
      val encodedFut = Source(items).via(SCFlow.encode(codec)).runFold(ByteString.empty)(_ ++ _)
      val decodedFut =
        for {
          encoded <- encodedFut
          regrouped = regroupWith(encoded)
          decoded <-
            Source(regrouped)
              .log("decoder-in").withAttributes(loggingAttributes)

            .via(SCFlow.decode(codec))
              .log("decoder-out").withAttributes(loggingAttributes)

            .runFold(Queue.empty[Item])(_.enqueue(_))
        }
          yield decoded


      val f = decodedFut.map(_ should be (items))(mat.executionContext)
      Await.result(f, 10.seconds)
      f
    }

  val pdus: List[PDU] = (1 to 2).map(i => PDU(i, i.toString)).toList

  val frames: List[ByteString] =
    (1 to 2)
      .map(i =>
        ByteString(
          (1 to i)
            .map(_.toByte)
            .toArray))
      .toList


  "SC [PDU]" should "single piece" in
    run(pdus, StreamTest.pduCodec)(regroupIdentity _)

  it should "many pieces (a byte per piece)" in
    run(pdus, StreamTest.pduCodec)(regroupNBytePacks(1) _)

  "SC [Frame]" should "single piece" in
    run(frames, StreamTest.frameCodec)(regroupIdentity _)

  it should "many pieces (a byte per piece)" in
    run(frames, StreamTest.frameCodec)(regroupNBytePacks(1) _)

  it should "many pieces (5 bytes per piece)" in
    run(frames, StreamTest.frameCodec)(regroupNBytePacks(5) _)


  def regroupIdentity(bs: ByteString): List[ByteString] = List(bs)
  def regroupNBytePacks(n: Int)(bs: ByteString): List[ByteString] = bs.grouped(n).toList
}
