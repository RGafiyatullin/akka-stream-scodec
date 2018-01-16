package com.github.rgafiyatullin.akka_stream_scodec

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString
import scodec.{Attempt, Codec, DecodeResult}
import scodec.bits.BitVector
import scodec.interop.akka._


final case class SCDecodeStage[T](codec: Codec[T]) extends GraphStage[FlowShape[ByteString, T]] {
  val inlet: Inlet[ByteString] = Inlet("In:ByteString")
  val outlet: Outlet[T] = Outlet("Out:PDU")

  override def shape: FlowShape[ByteString, T] = FlowShape.of(inlet, outlet)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      var buffer: BitVector = BitVector.empty
      var itemRequested: Boolean = false
      var upstreamFinished: Boolean = false

      def appendToBuffer(bs: ByteString): Unit = {
        buffer = buffer ++ bs.toByteVector.bits
      }

      def decodeItem(): Unit = {
        require(itemRequested)
        codec.decode(buffer) match {
          case Attempt.Successful(DecodeResult(item, remainder)) =>
            buffer = remainder
            itemRequested = false
            push(outlet, item)

          case Attempt.Failure(_: scodec.Err.InsufficientBits) =>
            if (!upstreamFinished) pull(inlet)
            else completeStage()

          case Attempt.Failure(cause) =>
            fail(outlet, SCDecodeError(cause, codec))
        }
      }

      setHandler(inlet, new InHandler {
        override def onPush(): Unit = {
          appendToBuffer(grab(inlet))
          if (itemRequested)
            decodeItem()
        }

        override def onUpstreamFinish(): Unit = {
          upstreamFinished = true
        }
      })

      setHandler(outlet, new OutHandler {
        override def onPull(): Unit = {
          itemRequested = true
          decodeItem()
        }
      })
    }
}